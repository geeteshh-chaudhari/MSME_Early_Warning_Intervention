"""Normalize generated prose and ground its numeric literals in metric values."""
from copy import deepcopy
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
import re
import unicodedata

TRANSLATION = str.maketrans({"−": "-", "٫": ".", "٬": ",", "٪": "%"})
NUMBER = re.compile(
    r"(?<![\w.])(?P<value>[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?)"
    r"(?P<unit>\s*(?:percentage\s+points?\b|percent\s+points?\b|percent\b|pp\b|%|"
    r"months?\b|days?\b|weeks?\b|years?\b|hours?\b|minutes?\b))?(?!\w|\.\d)", re.I)
DECLINE_BEFORE = re.compile(
    r"\b(?:decline|declined|decrease|decreased|drop|dropped|fall|fell|reduction|reduced|down|contraction)"
    r"(?:\s+(?:of|by|at|approximately|about|around|roughly|was|is|a|an)){0,4}\s*$", re.I)
DECLINE_AFTER = re.compile(
    r"^\s*(?:(?:annual|yearly|monthly|quarterly|revenue|yoy|qoq|mom)\s+){0,2}"
    r"(?:decline|decrease|drop|fall|reduction|contraction)\b", re.I)


def normalize_text(text):
    parts = []
    for char in text:
        expanded = unicodedata.normalize("NFKC", char)
        # Do not silently turn Roman numerals into words or fractions into ratios.
        if char.isnumeric() and not all(c.isdecimal() for c in expanded):
            parts.append(char)
        else:
            parts.append(expanded)
    normalized = "".join(parts).translate(TRANSLATION)
    return "".join(str(unicodedata.decimal(c)) if c.isdecimal() else c for c in normalized)


def normalize_recommendation(value):
    result = deepcopy(value)
    if not isinstance(result, dict):
        return result
    if isinstance(result.get("summary"), str):
        result["summary"] = normalize_text(result["summary"])
    if isinstance(result.get("actions"), list):
        for item in result["actions"]:
            if isinstance(item, dict):
                for key in ("title", "interpretation", "suggested_action"):
                    if isinstance(item.get(key), str):
                        item[key] = normalize_text(item[key])
    # Never normalize or guess citation IDs; validate them exactly as returned.
    return result


def unit_name(text):
    text = (text or "").strip().lower()
    if not text: return None
    if text in ("%", "percent"): return "percent"
    if text == "pp" or text.endswith("point") or text.endswith("points"): return "percentage_points"
    if text in ("month", "months"): return "months"
    return "unsupported"


def rounded_equal(actual, claimed, precision):
    if actual == claimed: return True
    if precision > 16: return False
    try:
        return actual.quantize(Decimal(1).scaleb(-precision), rounding=ROUND_HALF_UP) == claimed
    except InvalidOperation:
        return False


def numeric_errors(path, text, ids, metric_values, metric_units=None):
    text = normalize_text(text)
    errors = []
    if any(not re.fullmatch(r"\d{1,3}(?:,\d{3})+(?:\.\d+)?", m.group())
           for m in re.finditer(r"\d+(?:,\d+)+(?:\.\d+)?", text)):
        return [f"{path}: invalid numeric comma grouping; use a decimal point and optional thousands separators"]
    if re.search(r"\b\d{4}-\d{2}(?:-\d{2})?\b|\d+\s*[/⁄]\s*\d+", text):
        return [f"{path}: dates/fraction notation are not metric values; use the separate reporting metadata or qualitative wording"]
    deadline = re.search(r"\b(?:within|in|after|every|next)\s+\d+(?:\.\d+)?\s*(?:days?|weeks?|months?|years?|hours?|minutes?)\b", text, re.I)
    if deadline:
        return [f"{path}: numeric deadline {deadline.group()!r} is not a supported metric claim; use qualitative wording"]
    candidates = []
    for identifier in sorted(ids):
        if identifier not in (metric_values or {}): continue
        try:
            amount = Decimal(str(metric_values[identifier]))
        except (InvalidOperation, ValueError):
            continue
        if amount.is_finite():
            candidates.append((identifier, amount, (metric_units or {}).get(identifier)))
    consumed = set()
    for match in NUMBER.finditer(text):
        consumed.update(range(match.start(), match.end()))
        literal = match.group("value")
        decimal_text = literal.replace(",", "")
        claimed = Decimal(decimal_text)
        precision = len(decimal_text.partition(".")[2])
        unit = unit_name(match.group("unit"))
        decline = bool(DECLINE_BEFORE.search(text[max(0,match.start()-70):match.start()])
                       or DECLINE_AFTER.search(text[match.end():match.end()+60]))
        supported = False
        for identifier, actual, actual_unit in candidates:
            if unit == "unsupported" or (unit is not None and metric_units is not None and actual_unit != unit):
                continue
            if rounded_equal(actual, claimed, precision):
                supported = True; break
            # A positive magnitude can describe a NEGATIVE growth value, but only
            # next to explicit decline wording and for a growth metric.
            if (claimed > 0 and not literal.startswith("+") and actual < 0
                    and "growth_pct" in identifier and decline
                    and rounded_equal(-actual, claimed, precision)):
                supported = True; break
        if not supported:
            eligible = {identifier: str(amount) for identifier, amount, _ in candidates}
            errors.append(f"{path}: numeric claim {match.group(0).strip()!r} is unsupported by its cited metrics {eligible!r}; "
                          "correct the value/citations, preserve units and direction, or rewrite qualitatively")
    leftover = [char for index, char in enumerate(text) if char.isnumeric() and index not in consumed]
    if leftover:
        errors.append(f"{path}: unsupported numeric notation {list(dict.fromkeys(leftover))!r}; use ordinary decimal metric values")
    return errors
