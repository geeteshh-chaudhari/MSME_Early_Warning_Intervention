"""Normalize generated prose and ground its numeric literals in metric values."""
from copy import deepcopy
from decimal import Decimal, InvalidOperation
import re
import unicodedata

TRANSLATION = str.maketrans({"−": "-", "٫": ".", "٬": ",", "٪": "%"})
NUMBER = re.compile(
    r"(?<![\w.])(?P<value>[+-]?(?:(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?|\.\d+))"
    r"(?P<unit>\s*(?:percentage\s+points?\b|percent\s+points?\b|percent\b|pp\b|%|"
    r"months?\b|days?\b|weeks?\b|years?\b|hours?\b|minutes?\b))?(?!\w|\.\d)", re.I)


# Reporting dates and month/year durations are context, not financial values.
_YEAR = r"(?:19|20|21)\d{2}"
TIME_REFERENCE = re.compile(
    r"(?<![\w.,+\-$£₹])(?:"
    + _YEAR + r"-(?:0[1-9]|1[0-2])(?:-(?:0[1-9]|[12]\d|3[01]))?"
    + r"|(?:0?[1-9]|1[0-2])/" + _YEAR
    + r"|" + _YEAR
    + r"|\d+\s*(?:-\s*)?(?:months?|years?))"
    + r"(?!\w|[.,]\d)(?!\s*(?:%|percent\b|percentage\s+points?\b|pp\b|"
    + r"USD\b|INR\b|dollars?\b|rupees?\b))", re.I)


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
    # Citation text is preserved; it does not restrict numeric value matching.
    return result



def numeric_errors(path, text, ids, metric_values, metric_units=None):
    """Compare prose numbers with available tool values, without semantic checks.

    ids identifies the complete available metric set, not an action's citations.
    metric_units is retained for caller compatibility but is deliberately unused.
    Compare exact absolute magnitudes, ignoring signs and formatting zeros.
    Leading-dot decimals such as .87 are parsed as 0.87, never as 87.
    Recognizable reporting years, ISO dates and month/year durations are exempt.
    No rounding or interpretation of growth/decline wording is performed.
    """
    text = normalize_text(text)
    if any(not re.fullmatch(r"\d{1,3}(?:,\d{3})+(?:\.\d+)?", m.group())
           for m in re.finditer(r"\d+(?:,\d+)+(?:\.\d+)?", text)):
        return [f"{path}: cannot compare malformed number grouping with tool values"]
    candidates = {}
    for identifier in sorted(ids):
        if identifier not in (metric_values or {}):
            continue
        try:
            amount = Decimal(str(metric_values[identifier]))
        except (InvalidOperation, ValueError):
            continue
        if amount.is_finite():
            candidates[identifier] = amount
    errors = []
    consumed = {index for reference in TIME_REFERENCE.finditer(text)
                for index in range(reference.start(), reference.end())}
    values = {amount.copy_abs() for amount in candidates.values()}
    for match in NUMBER.finditer(text):
        is_time_reference = all(index in consumed for index in range(*match.span("value")))
        consumed.update(range(match.start(), match.end()))
        if is_time_reference:
            continue
        claimed = Decimal(match.group("value").replace(",", ""))
        if claimed.copy_abs() not in values:
            errors.append(
                f"{path}: numeric magnitude {match.group('value')!r} does not match any "
                f"available tool value {dict((key, str(value)) for key, value in candidates.items())!r}; "
                "copy the exact tool magnitude or remove the unsupported figure")
    leftover = [char for index, char in enumerate(text)
                if char.isnumeric() and index not in consumed]
    if leftover:
        errors.append(f"{path}: cannot compare numeric notation {list(dict.fromkeys(leftover))!r}; "
                      "use ordinary decimal tool values")
    return errors
