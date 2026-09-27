from typing import Any, Literal, TypedDict

from contracts import CONTRACT_VERSION, WorkflowEvent
from pydantic import BaseModel, ConfigDict, Field


NodeName = Literal[
    "CLASSIFY_TICKET",
    "SEARCH_KNOWLEDGE",
    "CHECK_CONFIDENCE",
    "GENERATE_RESPONSE",
    "CHECK_SENSITIVITY",
    "VERIFY_RESPONSE",
    "ESCALATE",
    "RESOLVE",
]

NodeStatus = Literal["PENDING", "RUNNING", "COMPLETED", "SKIPPED", "FAILED"]
RunStatus = Literal["QUEUED", "RUNNING", "WAITING_FOR_APPROVAL", "COMPLETED", "FAILED", "CANCELLED"]


class AgentRunState(BaseModel):
    """Validated run boundary; LangGraph nodes receive its dictionary representation."""

    model_config = ConfigDict(extra="forbid")

    ticket_id: int = Field(gt=0)
    run_id: str | None = None
    user_id: int | None = None
    status: RunStatus = "RUNNING"
    ticket: dict[str, Any] = Field(default_factory=dict)
    ticket_description: str = ""
    category_id: int | None = None
    category: str | None = None
    priority: str = "MEDIUM"
    knowledge_results: list[dict[str, Any]] = Field(default_factory=list)
    evidence: list[dict[str, Any]] = Field(default_factory=list)
    confidence: float = Field(default=0.0, ge=0, le=1)
    generated_response: str = ""
    is_sensitive: bool = False
    final_status: str | None = None
    current_node: str | None = None
    path: list[str] = Field(default_factory=list)
    escalation_reason: str | None = None
    verification_failure: str | None = None
    error: str | None = None
    iteration_count: int = Field(default=0, ge=0, le=7)


def initial_agent_state(ticket_id: int, run_id: str | None = None) -> "SmartHelpState":
    """Create a validated, bounded state payload accepted by the graph."""
    return AgentRunState(ticket_id=ticket_id, run_id=run_id).model_dump()


def validate_agent_state(state: "SmartHelpState") -> AgentRunState:
    """Validate a terminal graph result before exposing it outside the runtime."""
    return AgentRunState.model_validate(state)


class SmartHelpState(TypedDict, total=False):
    ticket_id: int
    ticket: dict[str, Any]
    ticket_description: str
    category_id: int | None
    category: str | None
    priority: str
    knowledge_results: list[dict[str, Any]]
    evidence: list[dict[str, Any]]
    confidence: float
    generated_response: str
    is_sensitive: bool
    final_status: str | None
    current_node: str
    path: list[str]
    escalation_reason: str | None
    verification_failure: str | None
    error: str | None
    run_id: str | None
    user_id: int | None
    status: RunStatus
    iteration_count: int


NODE_ORDER: list[NodeName] = [
    "CLASSIFY_TICKET",
    "SEARCH_KNOWLEDGE",
    "CHECK_CONFIDENCE",
    "GENERATE_RESPONSE",
    "CHECK_SENSITIVITY",
    "VERIFY_RESPONSE",
    "ESCALATE",
    "RESOLVE",
]

NODE_MESSAGES: dict[str, str] = {
    "CLASSIFY_TICKET": "Classifying ticket category and priority",
    "SEARCH_KNOWLEDGE": "Searching matching knowledge-base articles",
    "CHECK_CONFIDENCE": "Checking whether the knowledge match is strong enough",
    "GENERATE_RESPONSE": "Drafting a response from available knowledge",
    "CHECK_SENSITIVITY": "Checking whether the ticket needs human review",
    "VERIFY_RESPONSE": "Verifying the proposed response is grounded in retrieved evidence",
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
        "evidence": state.get("evidence", []),
        "generatedResponse": state.get("generated_response", ""),
        "verificationFailed": bool(state.get("verification_failure")),
        "path": state.get("path", []),
    }


def workflow_event(
    ticket_id: int,
    node: str,
    status: NodeStatus,
    state: SmartHelpState,
    message: str | None = None,
) -> dict[str, Any]:
    """Build and validate the public SSE contract before serializing it."""
    return WorkflowEvent(
        contractVersion=CONTRACT_VERSION,
        ticketId=ticket_id,
        runId=state.get("run_id"),
        node=node,
        status=status,
        state=public_state(state),
        message=message or NODE_MESSAGES.get(node, node.replace("_", " ").title()),
    ).model_dump(mode="json")
