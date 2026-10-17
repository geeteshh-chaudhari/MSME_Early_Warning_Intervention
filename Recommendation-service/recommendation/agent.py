import json
import re
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
                          data=dumps(payload).encode("utf-8"),
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
        # Bounded structured planning supports small models without native tool calling.
        for _ in range(len(TOOL_DESCRIPTIONS)+1):
            remaining = [name for name in TOOL_DESCRIPTIONS if name not in results]
            choices = remaining + (["finish"] if self.REQUIRED <= results.keys() else [])
            schema = {"type": "object", "properties": {
                "tool": {"type": "string", "enum": choices}},
                "required": ["tool"], "additionalProperties": False}
            messages = [{"role": "system", "content": self.prompt},
                {"role": "user", "content": dumps({"phase": "select_tool",
                    "question": question, "tools": TOOL_DESCRIPTIONS,
                    "already_observed": results, "choose_one_of": choices,
                    "response_schema": schema})}]
            selection = self.llm.generate(messages, schema)
            name = selection.get("tool")
            if set(selection) != {"tool"} or name not in choices:
                raise ValueError("Model selected an invalid or repeated tool; no arbitrary execution allowed.")
            if name == "finish":
                break
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
            {"role": "user", "content": dumps({"phase": "recommend", "question": question,
                "tool_results": results, "available_evidence_metric_ids": sorted(available),
                "response_schema": schema})}]
        # One repair attempt for small-model schema mistakes, never silent acceptance.
        for attempt in range(2):
            recommendation = self.llm.generate(messages, schema)
            try:
                validate_recommendation(recommendation, available, metric_values)
                break
            except ValueError as ex:
                if attempt:
                    raise
                messages.append({"role": "assistant", "content": dumps(recommendation)})
                messages.append({"role": "user", "content": "Correct the JSON: " + str(ex)})
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
    return {"type": "object", "properties": {
        "summary": {"type": "string"},
        "actions": {"type": "array", "maxItems": 6, "items": {"type": "object",
            "properties": {"title": {"type": "string"}, "interpretation": {"type": "string"},
                "suggested_action": {"type": "string"},
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
    for item in value["actions"]:
        if not isinstance(item, dict) or set(item) != {"title", "interpretation", "suggested_action", "evidence_metric_ids"}:
            raise ValueError("Each action must have title, interpretation, suggested_action and evidence_metric_ids.")
        if not all(isinstance(item[k], str) and item[k].strip() for k in ("title", "interpretation", "suggested_action")):
            raise ValueError("Action text fields must be nonempty strings.")
        ids = item["evidence_metric_ids"]
        if not isinstance(ids, list) or not ids or not all(isinstance(i, str) and i in available for i in ids):
            raise ValueError("Each action must cite available metric IDs exactly; unavailable metrics cannot support actions.")
    def check_numbers(text, ids):
        # Numeric prose may copy evidence values, but may not invent calculations.
        values = [metric_values[i] for i in ids] if metric_values is not None else []
        allowed = {round(v, digits) for v in values for digits in range(5)}
        tokens = re.findall(r"(?<![\w.])-?\d+(?:\.\d+)?", text)
        if any(float(token) not in allowed for token in tokens):
            raise ValueError("Numeric prose must copy values from cited metrics; otherwise use qualitative wording.")
    check_numbers(value["summary"], available)
    for item in value["actions"]:
        check_numbers(" ".join(item[k] for k in ("title", "interpretation", "suggested_action")), item["evidence_metric_ids"])
    text = " ".join([value["summary"]] + [item[key] for item in value["actions"]
                    for key in ("title", "interpretation", "suggested_action")])
    if re.search(r"\b(low|moderate|high|severe)\s+(financial\s+)?(stress|risk)\b", text, re.I):
        raise ValueError("Do not invent a financial stress or risk classification. Describe the observed metrics instead.")
