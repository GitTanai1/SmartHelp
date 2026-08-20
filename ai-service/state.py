from typing import Any, Literal, TypedDict


NodeName = Literal[
    "CLASSIFY_TICKET",
    "SEARCH_KNOWLEDGE",
    "CHECK_CONFIDENCE",
    "GENERATE_RESPONSE",
    "CHECK_SENSITIVITY",
    "ESCALATE",
    "RESOLVE",
]

NodeStatus = Literal["PENDING", "RUNNING", "COMPLETED", "SKIPPED", "FAILED"]


class SmartHelpState(TypedDict, total=False):
    ticket_id: int
    ticket: dict[str, Any]
    ticket_description: str
    category_id: int | None
    category: str | None
    priority: str
    knowledge_results: list[dict[str, Any]]
    confidence: float
    generated_response: str
    is_sensitive: bool
    final_status: str | None
    current_node: str
    path: list[str]
    escalation_reason: str | None
    error: str | None


NODE_ORDER: list[NodeName] = [
    "CLASSIFY_TICKET",
    "SEARCH_KNOWLEDGE",
    "CHECK_CONFIDENCE",
    "GENERATE_RESPONSE",
    "CHECK_SENSITIVITY",
    "ESCALATE",
    "RESOLVE",
]

NODE_MESSAGES: dict[str, str] = {
    "CLASSIFY_TICKET": "Classifying ticket category and priority",
    "SEARCH_KNOWLEDGE": "Searching matching knowledge-base articles",
    "CHECK_CONFIDENCE": "Checking whether the knowledge match is strong enough",
    "GENERATE_RESPONSE": "Drafting a response from available knowledge",
    "CHECK_SENSITIVITY": "Checking whether the ticket needs human review",
    "ESCALATE": "Escalating ticket to a human support agent",
    "RESOLVE": "Posting AI response and resolving ticket",
}


def add_path(state: SmartHelpState, node: str) -> list[str]:
    return [*state.get("path", []), node]


def public_state(state: SmartHelpState) -> dict[str, Any]:
    return {
        "category": state.get("category"),
        "priority": state.get("priority"),
        "confidence": round(float(state.get("confidence", 0.0)), 2),
        "sensitive": bool(state.get("is_sensitive", False)),
        "finalStatus": state.get("final_status"),
        "knowledgeCount": len(state.get("knowledge_results", [])),
        "generatedResponse": state.get("generated_response", ""),
        "path": state.get("path", []),
    }


def workflow_event(
    ticket_id: int,
    node: str,
    status: NodeStatus,
    state: SmartHelpState,
    message: str | None = None,
) -> dict[str, Any]:
    return {
        "ticketId": ticket_id,
        "node": node,
        "status": status,
        "state": public_state(state),
        "message": message or NODE_MESSAGES.get(node, node.replace("_", " ").title()),
    }
