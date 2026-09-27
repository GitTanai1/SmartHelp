"""Run versioned deterministic SmartHelp agent evaluations without live services."""

import json
import os
import sys
from pathlib import Path
from unittest.mock import MagicMock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ai-service"))
os.environ["LLM_API_KEY"] = ""

from graph import WORKFLOW  # noqa: E402


def run_case(case: dict) -> str:
    ticket = {"ticket": case["ticket"]}
    with (
        patch("graph.get_ticket") as get_ticket,
        patch("graph.search_knowledge_base") as search_knowledge,
        patch("graph.get_customer_history") as history,
        patch("graph.request_resolution_approval") as resolve,
        patch("graph.escalate_ticket") as escalate,
    ):
        get_ticket.invoke = MagicMock(return_value=ticket)
        search_knowledge.invoke = MagicMock(return_value=case["articles"])
        history.invoke = MagicMock(return_value=[])
        resolve.invoke = MagicMock(return_value={"id": 1})
        escalate.invoke = MagicMock(return_value={"id": 1})
        result = WORKFLOW.invoke({"ticket_id": case["ticket"]["id"], "path": [], "confidence": 0.0})
        return result["final_status"]


def main() -> int:
    cases = json.loads((Path(__file__).with_name("golden_cases.json")).read_text(encoding="utf-8"))
    failures = []
    for case in cases:
        actual = run_case(case)
        print(f"{case['id']}: expected={case['expectedStatus']} actual={actual}")
        if actual != case["expectedStatus"]:
            failures.append(case["id"])
    if failures:
        print(f"FAILED: {', '.join(failures)}")
        return 1
    print(f"PASS: {len(cases)} deterministic evaluation cases")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
