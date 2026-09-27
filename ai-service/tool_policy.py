"""Typed, centralized policy metadata for every agent-accessible tool.

The registry deliberately does not authorize callers: Java validates the bearer
token and business permissions on every request. It prevents Python from
accidentally exposing a write through the wrong endpoint or without its required
idempotency/approval semantics.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


RiskLevel = Literal["READ_ONLY", "LOW_RISK_WRITE", "HIGH_RISK_WRITE"]
IdempotencyMode = Literal["NONE", "IDEMPOTENCY_KEY", "RUN_ACTION_UNIQUE"]


class ToolDefinition(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    name: str
    description: str
    required_permission: str
    risk: RiskLevel
    timeout_seconds: float = Field(gt=0, le=60)
    max_retries: int = Field(ge=0, le=2)
    idempotency: IdempotencyMode
    requires_approval: bool


TOOL_REGISTRY: dict[str, ToolDefinition] = {
    "get_ticket": ToolDefinition(
        name="get_ticket", description="Read one authorized ticket", required_permission="ticket:read",
        risk="READ_ONLY", timeout_seconds=10, max_retries=1, idempotency="NONE", requires_approval=False),
    "search_knowledge_base": ToolDefinition(
        name="search_knowledge_base", description="Read knowledge evidence", required_permission="knowledge:read",
        risk="READ_ONLY", timeout_seconds=10, max_retries=1, idempotency="NONE", requires_approval=False),
    "get_customer_history": ToolDefinition(
        name="get_customer_history", description="Read authorized customer history", required_permission="ticket:read",
        risk="READ_ONLY", timeout_seconds=10, max_retries=1, idempotency="NONE", requires_approval=False),
    "escalate_ticket": ToolDefinition(
        name="escalate_ticket", description="Escalate a ticket for human review", required_permission="ticket:write",
        risk="LOW_RISK_WRITE", timeout_seconds=10, max_retries=0, idempotency="IDEMPOTENCY_KEY", requires_approval=False),
    "request_resolution_approval": ToolDefinition(
        name="request_resolution_approval", description="Propose a resolution for human approval",
        required_permission="ticket:resolve", risk="HIGH_RISK_WRITE", timeout_seconds=10, max_retries=0,
        idempotency="RUN_ACTION_UNIQUE", requires_approval=True),
}


def validate_tool_request(name: str, path: str, has_idempotency_key: bool) -> ToolDefinition:
    """Fail closed if a tool's endpoint no longer satisfies its declared policy."""
    try:
        definition = TOOL_REGISTRY[name]
    except KeyError as exc:
        raise RuntimeError(f"No tool policy is registered for {name}") from exc
    if definition.idempotency == "IDEMPOTENCY_KEY" and not has_idempotency_key:
        raise RuntimeError(f"{name} requires an idempotency key")
    if definition.requires_approval and "approval" not in path:
        raise RuntimeError(f"{name} must use an approval endpoint")
    if definition.risk != "READ_ONLY" and definition.max_retries != 0:
        raise RuntimeError(f"write tool {name} must not be retried by the Python runtime")
    return definition
