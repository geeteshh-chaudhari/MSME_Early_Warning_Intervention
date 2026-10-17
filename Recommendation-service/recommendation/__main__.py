import argparse
from datetime import date
import json
from pathlib import Path
import sys
from .agent import RecommendationAgent, OllamaClient, dumps, json_default
from .config import Settings
from .database import OracleRepository
from .metrics import MetricTools, TOOL_DESCRIPTIONS, shift_month


def main():
    parser = argparse.ArgumentParser(description="Oracle-backed MSME recommendation agent using local Qwen.")
    parser.add_argument("--msme-id", required=True, type=int)
    parser.add_argument("--as-of", help="Completed reporting month YYYY-MM; defaults to previous calendar month.")
    parser.add_argument("--metrics-only", action="store_true", help="Run all deterministic tools without Ollama.")
    parser.add_argument("--question", default="Interpret financial trends, seasonal context and propose RM review actions.")
    parser.add_argument("--output", type=Path, help="Optional new JSON output file; existing files are not overwritten.")
    args = parser.parse_args()
    if args.msme_id <= 0:
        parser.error("--msme-id must be positive")
    try:
        as_of = date.fromisoformat(args.as_of + "-01") if args.as_of else shift_month(date.today(), -1)
        if as_of >= date.today().replace(day=1):
            raise ValueError("Choose a completed reporting month before the current month.")
        if args.output and args.output.exists():
            raise ValueError("Output file already exists; choose a new filename.")
        settings = Settings.load()
        data = OracleRepository(settings).load(args.msme_id, as_of)
        tools = MetricTools(data, as_of)
        if args.metrics_only:
            result = {"status": "metrics_only", "evidence": {name: tools.execute(name) for name in TOOL_DESCRIPTIONS}}
        else:
            result = RecommendationAgent(tools, OllamaClient(settings)).run(args.question)
        result.update(msme_id=args.msme_id, as_of_month=as_of.isoformat(), formula_version="1.0")
        rendered = json.dumps(result, default=json_default, indent=2, ensure_ascii=False, allow_nan=False)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            with args.output.open("x", encoding="utf-8") as handle:
                handle.write(rendered + "\n")
        print(rendered)
        return 0
    except (ValueError, RuntimeError, OSError) as ex:
        print(dumps({"error": str(ex)}), file=sys.stderr)
        return 1
    except Exception as ex:
        # Avoid connection strings, usernames and Oracle internals in reports.
        code = getattr(ex.args[0], "code", None) if ex.args else None
        print(dumps({"error": "Database or dependency operation failed. Check the read grants, schema owners and configuration.",
                     "exception_type": type(ex).__name__, "oracle_code": code}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
