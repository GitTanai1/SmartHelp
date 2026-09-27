import os
import time
from contextvars import ContextVar
from typing import Any

import httpx
from langchain_core.tools import tool

from tool_policy import validate_tool_request


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_TIMEOUT_SECONDS = 10.0
request_id: ContextVar[str | None] = ContextVar("request_id", default=None)
idempotency_key: ContextVar[str | None] = ContextVar("idempotency_key", default=None)
authorization_header: ContextVar[str | None] = ContextVar("authorization_header", default=None)
agent_run_id: ContextVar[str | None] = ContextVar("agent_run_id", default=None)


def _base_url() -> str:
    return os.getenv("SPRING_BOOT_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def _timeout() -> float:
    value = os.getenv("SPRING_BOOT_TIMEOUT_SECONDS", str(DEFAULT_TIMEOUT_SECONDS))
    try:
        return float(value)
    except ValueError:
        return DEFAULT_TIMEOUT_SECONDS


def _request(method: str, path: str, *, tool_name: str, **kwargs: Any) -> Any:
    url = f"{_base_url()}{path}"
    try:
        headers = dict(kwargs.pop("headers", {}))
        policy = validate_tool_request(tool_name, path, "Idempotency-Key" in headers)
        if correlation_id := request_id.get():
            headers["X-Request-ID"] = correlation_id
        if authorization := authorization_header.get():
            headers["Authorization"] = authorization
        if run_id := agent_run_id.get():
            headers["X-Agent-Run-ID"] = run_id
        for attempt in range(policy.max_retries + 1):
            try:
                with httpx.Client(timeout=min(_timeout(), policy.timeout_seconds)) as client:
                    response = client.request(method, url, headers=headers, **kwargs)
                    response.raise_for_status()
                    if response.status_code == 204 or not response.content:
                        return None
                    return response.json()
            except httpx.HTTPStatusError as exc:
                if exc.response.status_code < 500 or attempt == policy.max_retries:
                    raise
            except httpx.HTTPError:
                if attempt == policy.max_retries:
                    raise
            # Deterministic bounded backoff; only READ_ONLY tools have retries.
            time.sleep(0.1 * (attempt + 1))
    except httpx.HTTPStatusError as exc:
        detail = exc.response.text[:300]
        raise RuntimeError(
            f"Spring Boot returned {exc.response.status_code} for {method} {path}: {detail}"
        ) from exc
    except httpx.HTTPError as exc:
        raise RuntimeError(f"Could not reach Spring Boot at {_base_url()}: {exc}") from exc


@tool
def get_ticket(ticket_id: int) -> dict[str, Any]:
    """Load one ticket detail through the Spring Boot REST API."""
    return _request("GET", f"/api/v1/tickets/{ticket_id}", tool_name="get_ticket")


@tool
def search_knowledge_base(category_id: int | None = None, query: str | None = None) -> list[dict[str, Any]]:
    """Search knowledge articles through the Spring Boot REST API."""
    params: dict[str, Any] = {}
    if category_id is not None:
        params["categoryId"] = category_id
    if query:
        params["query"] = query
    return _request("GET", "/api/v1/knowledge", tool_name="search_knowledge_base", params=params)


@tool
def get_customer_history(user_id: int) -> list[dict[str, Any]]:
    """Load prior customer tickets through the Spring Boot REST API."""
    return _request("GET", "/api/v1/tickets", tool_name="get_customer_history", params={"userId": user_id})


@tool
def request_resolution_approval(ticket_id: int, message: str, priority: str | None = None) -> dict[str, Any]:
    """Propose a ticket resolution for an authorized human; this never resolves directly."""
    if not agent_run_id.get():
        raise RuntimeError("A resolution proposal requires a durable agent run")
    return _request(
        "POST",
        f"/api/v1/tickets/{ticket_id}/ai-resolution-approval",
        tool_name="request_resolution_approval",
        json={"message": message, "priority": priority},
    )


@tool
def escalate_ticket(ticket_id: int, reason: str) -> dict[str, Any]:
    """Atomically add an AI escalation note and update the ticket in Spring Boot."""
    key = idempotency_key.get()
    if not key:
        raise RuntimeError("AI escalation requires an idempotency key")
    return _request(
        "POST",
        f"/api/v1/tickets/{ticket_id}/ai-escalation",
        tool_name="escalate_ticket",
        headers={"Idempotency-Key": key},
        json={"reason": reason},
    )
