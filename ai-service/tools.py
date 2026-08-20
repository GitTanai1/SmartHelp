import os
from typing import Any

import httpx
from langchain_core.tools import tool


DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_TIMEOUT_SECONDS = 10.0


def _base_url() -> str:
    return os.getenv("SPRING_BOOT_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def _timeout() -> float:
    value = os.getenv("SPRING_BOOT_TIMEOUT_SECONDS", str(DEFAULT_TIMEOUT_SECONDS))
    try:
        return float(value)
    except ValueError:
        return DEFAULT_TIMEOUT_SECONDS


def _request(method: str, path: str, **kwargs: Any) -> Any:
    url = f"{_base_url()}{path}"
    try:
        with httpx.Client(timeout=_timeout()) as client:
            response = client.request(method, url, **kwargs)
            response.raise_for_status()
            if response.status_code == 204 or not response.content:
                return None
            return response.json()
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
    return _request("GET", f"/api/tickets/{ticket_id}")


@tool
def search_knowledge_base(category_id: int | None = None, query: str | None = None) -> list[dict[str, Any]]:
    """Search knowledge articles through the Spring Boot REST API."""
    params: dict[str, Any] = {}
    if category_id is not None:
        params["categoryId"] = category_id
    if query:
        params["query"] = query
    return _request("GET", "/api/knowledge", params=params)


@tool
def get_customer_history(user_id: int) -> list[dict[str, Any]]:
    """Load prior customer tickets through the Spring Boot REST API."""
    return _request("GET", "/api/tickets", params={"userId": user_id})


@tool
def create_response(ticket_id: int, message: str, sender_type: str = "AI") -> dict[str, Any]:
    """Create an AI or agent ticket response through the Spring Boot REST API."""
    return _request(
        "POST",
        f"/api/tickets/{ticket_id}/responses",
        json={"message": message, "senderType": sender_type},
    )


@tool
def update_ticket_status(ticket_id: int, status: str, priority: str | None = None) -> dict[str, Any]:
    """Update a ticket status while preserving the existing ticket content."""
    detail = get_ticket.invoke({"ticket_id": ticket_id})
    ticket = detail["ticket"]
    payload = {
        "categoryId": ticket.get("categoryId"),
        "subject": ticket["subject"],
        "description": ticket["description"],
        "status": status,
        "priority": priority or ticket.get("priority", "MEDIUM"),
    }
    return _request("PUT", f"/api/tickets/{ticket_id}", json=payload)


@tool
def escalate_ticket(ticket_id: int, reason: str) -> dict[str, Any]:
    """Escalate a ticket and leave an AI note explaining why."""
    create_response.invoke(
        {
            "ticket_id": ticket_id,
            "message": f"AI workflow escalated this ticket for human review. Reason: {reason}",
            "sender_type": "AI",
        }
    )
    return update_ticket_status.invoke({"ticket_id": ticket_id, "status": "ESCALATED"})
