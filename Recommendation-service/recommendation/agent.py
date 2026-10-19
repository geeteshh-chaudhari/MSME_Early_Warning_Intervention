import json
import re
import unicodedata
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener
from .config import ROOT
from .metrics import TOOL_DESCRIPTIONS


def json_default(value):
    from decimal import Decimal
    from datetime import date, datetime
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (date, datetime)):
        return value.isoformat()
    raise TypeError(f"Not serializable: {type(value).__name__}")


def dumps(value):
    return json.dumps(value, default=json_default, ensure_ascii=False, allow_nan=False)


def compact_dumps(value):
    """Compact only model-bound JSON; public serialization stays unchanged."""
    return json.dumps(value, default=json_default, ensure_ascii=False,
                      allow_nan=False, separators=(",", ":"))


def compact_tool_results(results):
    """Keep interpretation evidence; omit formulas and null metadata for the LLM.

    Formulas describe calculations Python has already performed. Preserve units,
    availability, comparison dates, meanings, saved thresholds, sample counts and
    caveats. Full original tool results are returned to the caller independently.
    """
    return {tool: values if tool == "business_context" else {
        key: {field: value for field, value in item.items()
              if field != "formula" and value is not None}
        for key, item in values.items()}
        for tool, values in results.items()}


class OllamaClient:
    def __init__(self, settings):
        self.settings = settings
        # Keep local financial data on the loopback connection, not an HTTP proxy.
        self.opener = build_opener(ProxyHandler({}))

    def generate(self, messages, schema):
        payload = {"model": self.settings.model, "messages": messages,
                   "stream": False, "think": False, "format": schema,
                   "options": {"temperature": 0, "num_ctx": 16384, "num_predict": 1800}}
        request = Request(self.settings.ollama_url + "/api/chat",
                          data=compact_dumps(payload).encode("utf-8"),
                          headers={"Content-Type": "application/json"})
        try:
            with self.opener.open(request, timeout=self.settings.ollama_timeout) as response:
                result = json.load(response)
            if result.get("done_reason") == "length":
                raise RuntimeError("Local model response was truncated; retry or increase num_predict.")
            content = json.loads(result["message"]["content"])
            if not isinstance(content, dict):
                raise ValueError("Expected a JSON object.")
            return content
        except HTTPError as ex:
            raise RuntimeError(f"Ollama HTTP {ex.code}. Check Ollama and the installed qwen3:1.7b model.") from ex
        except (URLError, TimeoutError) as ex:
            raise RuntimeError("Local Ollama connection failed or timed out. Check ollama serve and the model.") from ex
        except (ValueError, KeyError) as ex:
            raise RuntimeError("Local model did not return valid structured JSON.") from ex


class RecommendationAgent:
    REQUIRED = {"business_context", "revenue_metrics", "liquidity_metrics"}

    def __init__(self, tools, llm):
        self.tools, self.llm = tools, llm
        self.prompt = (ROOT / "prompts" / "riscor.md").read_text(encoding="utf-8")

    def run(self, question="Explain financial stress, seasonal context and practical next actions."):
        if len(question) > 2000:
            raise ValueError("Question must be at most 2000 characters.")
        results, trace = {}, []
        # These fixed, inexpensive Python tools require no model planning calls.
        for name in TOOL_DESCRIPTIONS:
            results[name] = self.tools.execute(name)
            trace.append({"step": len(trace)+1, "tool": name})
        if not self.REQUIRED <= results.keys():
            raise ValueError("Agent did not obtain required evidence.")
        available = {f"{tool}.{key}" for tool, values in results.items()
                     if tool != "business_context" for key, item in values.items()
                     if item["status"] == "available"}
        if not available:
            return {"status": "insufficient_data", "tool_trace": trace,
                "evidence": results, "recommendation": {"summary": "No usable financial metrics.",
                "actions": [], "limitations": ["Provide complete valid monthly snapshots and rerun.",
                    "Human RM review is required."]}}
        schema = recommendation_schema(sorted(available))
        metric_values = {f"{tool}.{key}": item["value"] for tool, values in results.items()
                         if tool != "business_context" for key, item in values.items()
                         if item["status"] == "available"}
        messages = [{"role": "system", "content": self.prompt},
            {"role": "user", "content": compact_dumps({"phase": "recommend", "question": question,
                "tool_results": compact_tool_results(results)})}]
        # The complete output schema (including allowed citation IDs) is already
        # supplied to Ollama via format; do not duplicate it in the prompt.
        # One repair attempt for small-model schema mistakes, never silent acceptance.
        for attempt in range(2):
            recommendation = self.llm.generate(messages, schema)
            import sys
            print(
                  "LLM RESPONSE:\n" + json.dumps(
                      recommendation, indent=2, ensure_ascii=False
                  ),
                  file=sys.stderr,
            )
            try:
                validate_recommendation(recommendation, available, metric_values)
                break
            except ValueError as ex:
                if attempt:
                    raise
                messages.append({"role": "assistant", "content": compact_dumps(recommendation)})
                messages.append({"role": "user", "content": (
                    "Repair the rejected JSON using this validation feedback: " + str(ex)
                    + " Preserve the required fields and supported advisory detail. "
                    "Rewrite numeric prose qualitatively; do not spell out numbers or simply delete digits. "
                    "Use exact available metric IDs supporting each action's claims. "
                    "Annual revenue and receivables growth claims need their respective growth metrics; "
                    "credit utilisation/headroom cannot substitute for them. "
                    "Matching growth does not prove effective collections; headroom alone does not "
                    "justify borrowing or changing credit terms. Recheck every action. Return only corrected JSON."
                )})
        # Preserve deterministic limitations even if the model omits them.
        limitations = list(results["business_context"]["limitations"])
        for tool, values in results.items():
            if tool == "business_context":
                continue
            for key, item in values.items():
                if item["status"] == "unavailable":
                    limitations.append(f"{tool}.{key}: {item['reason']}")
                if item.get("limitation") and item["limitation"] not in limitations:
                    limitations.append(item["limitation"])
                if item.get("assumption") and item["assumption"] not in limitations:
                    limitations.append(item["assumption"])
        for limitation in ["LLM interpretations are advisory and require RM review; citations do not guarantee narrative correctness."]:
            if limitation not in limitations:
                limitations.append(limitation)
        recommendation["limitations"] = limitations
        return {"status": "generated", "model": getattr(getattr(self.llm, "settings", None), "model", "test"),
                "tool_trace": trace, "evidence": results, "recommendation": recommendation}


def recommendation_schema(evidence_ids):
    # Constrain prose during decoding, rather than relying on prompt adherence.
    # Citation IDs are separate: their legitimate digits must remain allowed.
    # Block common alternative decimal glyphs as well as ASCII digits during
    # decoding. Python validation below also covers other Unicode numerals.
    prose = {"type": "string", "minLength": 1, "pattern": "^[^0-9０-９٠-٩۰-۹]*$"}
    return {"type": "object", "properties": {
        "summary": dict(prose),
        "actions": {"type": "array", "maxItems": 6, "items": {"type": "object",
            "properties": {"title": dict(prose), "interpretation": dict(prose),
                "suggested_action": dict(prose),
                "evidence_metric_ids": {"type": "array", "minItems": 1,
                    "items": {"type": "string", "enum": evidence_ids}}},
            "required": ["title", "interpretation", "suggested_action", "evidence_metric_ids"],
            "additionalProperties": False}}},
        "required": ["summary", "actions"], "additionalProperties": False}


def validate_recommendation(value, available, metric_values=None):
    if set(value) != {"summary", "actions"} or not isinstance(value["summary"], str) or not value["summary"].strip():
        raise ValueError("Return only summary and actions with a nonempty summary; Python adds data limitations.")
    if not isinstance(value["actions"], list) or len(value["actions"]) > 6:
        raise ValueError("actions must be a list of at most six items.")
    citation_errors = []
    for index, item in enumerate(value["actions"]):
        if not isinstance(item, dict) or set(item) != {"title", "interpretation", "suggested_action", "evidence_metric_ids"}:
            raise ValueError("Each action must have title, interpretation, suggested_action and evidence_metric_ids.")
        if not all(isinstance(item[k], str) and item[k].strip() for k in ("title", "interpretation", "suggested_action")):
            raise ValueError("Action text fields must be nonempty strings.")
        ids = item["evidence_metric_ids"]
        if not isinstance(ids, list) or not ids or not all(isinstance(i, str) for i in ids):
            raise ValueError(f"actions[{index}].evidence_metric_ids must be a nonempty list of available metric ID strings.")
        for citation in ids:
            if citation in available:
                continue
            matches = [identifier for identifier in sorted(available)
                       if identifier.rsplit(".", 1)[-1] == citation]
            detail = (f"; exact available ID is {matches[0]!r}" if len(matches) == 1
                      else "; choose an exact available metric ID supporting the claim")
            citation_errors.append(f"actions[{index}].evidence_metric_ids contains invalid ID {citation!r}{detail}")
    # Match the schema and RISCOR policy: all prose is qualitative, even when
    # a number matches evidence. Exact numeric evidence remains unchanged.
    numeric_fields = []
    def check_prose(path, text):
        # Normalize only for checking/feedback, never silently rewrite output.
        normalized = unicodedata.normalize("NFKC", text)
        tokens = list(dict.fromkeys(re.findall(r"\d+(?:[.,]\d+)*", normalized)))
        other_numerals = list(dict.fromkeys(char for char in text if char.isnumeric()))
        if tokens:
            numeric_fields.append(f"{path} contains numeric tokens {tokens!r}")
        elif other_numerals:
            numeric_fields.append(f"{path} contains Unicode numerals {other_numerals!r}")
    check_prose("summary", value["summary"])
    for index, item in enumerate(value["actions"]):
        for field in ("title", "interpretation", "suggested_action"):
            check_prose(f"actions[{index}].{field}", item[field])
    if citation_errors or numeric_fields:
        errors = []
        if citation_errors:
            errors.append("Citation errors: " + "; ".join(citation_errors))
        if numeric_fields:
            errors.append("Qualitative prose required: " + "; ".join(numeric_fields)
                          + ". Rewrite these fields without figures, dates, numeric targets or alternate Unicode numerals")
        raise ValueError(". ".join(errors) + ". Keep exact values in separately returned metric evidence. "
                         "Use fully qualified citation IDs exactly as supplied, including the tool prefix; "
                         "correct invalid IDs only if the underlying metric supports the claim.")
    text = " ".join([value["summary"]] + [item[key] for item in value["actions"]
                    for key in ("title", "interpretation", "suggested_action")])
    if re.search(r"\b(low|moderate|high|severe)\s+(financial\s+)?(stress|risk)\b", text, re.I):
        raise ValueError("Do not invent a financial stress or risk classification. Describe the observed metrics instead.")
