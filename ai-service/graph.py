import os
import re
from typing import Any

from langgraph.graph import END, START, StateGraph

from prompts import (
    CLASSIFICATION_PROMPT,
    RESPONSE_PROMPT,
    SENSITIVITY_PROMPT,
    ClassificationOutput,
    SensitivityOutput,
)
from state import NODE_MESSAGES, SmartHelpState, add_path, workflow_event
from tools import (
    create_response,
    escalate_ticket,
    get_customer_history,
    get_ticket,
    search_knowledge_base,
    update_ticket_status,
)


CATEGORY_KEYWORDS: dict[str, tuple[str, ...]] = {
    "Billing": ("billing", "payment", "paid", "charged", "charge", "deducted", "invoice", "subscription inactive"),
    "Account Access": ("login", "log in", "password", "reset", "email", "sign in", "access"),
    "Subscription": ("subscription", "plan", "monthly", "annual", "cancel", "upgrade", "downgrade"),
    "Refunds": ("refund", "accidental purchase", "money back", "return payment"),
    "Security": ("security", "unauthorized", "compromised", "suspicious", "fraud", "stolen", "account takeover"),
    "Technical Support": ("page not loading", "browser", "cache", "network", "error", "bug", "not loading"),
}

SENSITIVE_TERMS = (
    "unauthorized",
    "compromised",
    "account takeover",
    "stolen",
    "fraud",
    "chargeback",
    "disputed charge",
    "legal",
    "lawsuit",
    "privacy",
    "personal data",
    "urgent outage",
)


def _confidence_threshold() -> float:
    value = os.getenv("SMARTHELP_CONFIDENCE_THRESHOLD", "0.70")
    try:
        return float(value)
    except ValueError:
        return 0.70


def _llm_api_key() -> str:
    return os.getenv("LLM_API_KEY", "").strip()


def _ticket_summary(detail: dict[str, Any]) -> dict[str, Any]:
    return detail.get("ticket", detail)


def _ticket_text(ticket: dict[str, Any]) -> str:
    return f"{ticket.get('subject', '')}. {ticket.get('description', '')}".strip()


def _tokens(text: str) -> set[str]:
    stop_words = {
        "the",
        "and",
        "for",
        "that",
        "this",
        "with",
        "from",
        "have",
        "want",
        "need",
        "will",
        "your",
        "you",
        "but",
        "was",
        "are",
        "can",
        "our",
    }
    return {
        token
        for token in re.findall(r"[a-z0-9]+", text.lower())
        if len(token) > 2 and token not in stop_words
    }


def _deterministic_category(text: str) -> str | None:
    lowered = text.lower()
    best_category: str | None = None
    best_score = 0
    for category, keywords in CATEGORY_KEYWORDS.items():
        score = sum(1 for keyword in keywords if keyword in lowered)
        if score > best_score:
            best_score = score
            best_category = category
    return best_category if best_score > 0 else None


def _deterministic_priority(text: str, existing_priority: str | None) -> str:
    lowered = text.lower()
    if any(term in lowered for term in ("unauthorized", "compromised", "fraud", "urgent", "outage")):
        return "HIGH"
    if existing_priority in {"LOW", "MEDIUM", "HIGH"}:
        return existing_priority
    if any(term in lowered for term in ("charged", "payment", "refund", "cannot log in")):
        return "MEDIUM"
    return "LOW"


def _build_llm():
    if not _llm_api_key():
        return None
    from langchain_openai import ChatOpenAI

    kwargs: dict[str, Any] = {
        "model": os.getenv("LLM_MODEL", "gpt-4.1-mini"),
        "api_key": _llm_api_key(),
        "temperature": 0,
    }
    base_url = os.getenv("LLM_BASE_URL", "").strip()
    if base_url:
        kwargs["base_url"] = base_url
    return ChatOpenAI(**kwargs)


def _classify_with_llm(ticket_text: str) -> ClassificationOutput | None:
    llm = _build_llm()
    if llm is None:
        return None
    try:
        structured = llm.with_structured_output(ClassificationOutput)
        return structured.invoke(CLASSIFICATION_PROMPT.format(ticket_text=ticket_text))
    except Exception:
        return None


def _sensitivity_with_llm(ticket_text: str) -> SensitivityOutput | None:
    llm = _build_llm()
    if llm is None:
        return None
    try:
        structured = llm.with_structured_output(SensitivityOutput)
        return structured.invoke(SENSITIVITY_PROMPT.format(ticket_text=ticket_text))
    except Exception:
        return None


def _generate_with_llm(ticket_text: str, knowledge_text: str) -> str | None:
    llm = _build_llm()
    if llm is None:
        return None
    try:
        response = llm.invoke(RESPONSE_PROMPT.format(ticket_text=ticket_text, knowledge_text=knowledge_text))
        return str(response.content).strip()
    except Exception:
        return None


def classify_ticket(state: SmartHelpState) -> SmartHelpState:
    detail = get_ticket.invoke({"ticket_id": state["ticket_id"]})
    ticket = _ticket_summary(detail)
    ticket_text = _ticket_text(ticket)

    llm_result = _classify_with_llm(ticket_text)
    fallback_category = ticket.get("categoryName") or _deterministic_category(ticket_text)
    fallback_priority = _deterministic_priority(ticket_text, ticket.get("priority"))

    category = llm_result.category if llm_result and llm_result.category else fallback_category
    priority = llm_result.priority if llm_result and llm_result.priority in {"LOW", "MEDIUM", "HIGH"} else fallback_priority

    user_id = ticket.get("userId")
    if user_id:
        get_customer_history.invoke({"user_id": user_id})

    return {
        "ticket": ticket,
        "ticket_description": ticket_text,
        "category_id": ticket.get("categoryId"),
        "category": category,
        "priority": priority,
        "current_node": "CLASSIFY_TICKET",
        "path": add_path(state, "CLASSIFY_TICKET"),
    }


def search_knowledge(state: SmartHelpState) -> SmartHelpState:
    category_id = state.get("category_id")
    ticket = state.get("ticket", {})
    if category_id is not None:
        articles = search_knowledge_base.invoke({"category_id": category_id, "query": None})
    else:
        subject = ticket.get("subject") or state.get("category") or ""
        articles = search_knowledge_base.invoke({"category_id": None, "query": subject})

    return {
        "knowledge_results": articles,
        "current_node": "SEARCH_KNOWLEDGE",
        "path": add_path(state, "SEARCH_KNOWLEDGE"),
    }


def check_confidence(state: SmartHelpState) -> SmartHelpState:
    articles = state.get("knowledge_results", [])
    ticket_text = state.get("ticket_description", "")
    ticket_tokens = _tokens(ticket_text)
    knowledge_text = " ".join(f"{item.get('title', '')} {item.get('content', '')}" for item in articles)
    overlap = len(ticket_tokens.intersection(_tokens(knowledge_text)))

    score = 0.0
    if articles:
        score += 0.35
    if state.get("category_id") and any(item.get("categoryId") == state.get("category_id") for item in articles):
        score += 0.25
    score += min(0.40, overlap * 0.08)

    return {
        "confidence": min(score, 1.0),
        "current_node": "CHECK_CONFIDENCE",
        "path": add_path(state, "CHECK_CONFIDENCE"),
    }


def generate_response(state: SmartHelpState) -> SmartHelpState:
    articles = state.get("knowledge_results", [])
    knowledge_text = "\n\n".join(
        f"{item.get('title', '')}: {item.get('content', '')}" for item in articles[:3]
    )
    llm_response = _generate_with_llm(state.get("ticket_description", ""), knowledge_text)
    if llm_response:
        response = llm_response
    elif articles:
        article = articles[0]
        response = (
            "Thanks for reaching out to SmartHelp. Based on our support guide, "
            f"{article.get('content', '')} Please reply with any requested details so we can continue helping."
        )
    else:
        response = "The available knowledge base does not contain enough information to resolve this ticket."

    return {
        "generated_response": response,
        "current_node": "GENERATE_RESPONSE",
        "path": add_path(state, "GENERATE_RESPONSE"),
    }


def check_sensitivity(state: SmartHelpState) -> SmartHelpState:
    ticket_text = state.get("ticket_description", "")
    lowered = ticket_text.lower()
    llm_result = _sensitivity_with_llm(ticket_text)
    sensitive = any(term in lowered for term in SENSITIVE_TERMS) or state.get("category") == "Security"
    reason = "Sensitive account, billing, legal, privacy, or outage language detected."
    if llm_result is not None:
        sensitive = llm_result.sensitive or sensitive
        if llm_result.reason:
            reason = llm_result.reason

    return {
        "is_sensitive": sensitive,
        "escalation_reason": reason if sensitive else None,
        "current_node": "CHECK_SENSITIVITY",
        "path": add_path(state, "CHECK_SENSITIVITY"),
    }


def escalate(state: SmartHelpState) -> SmartHelpState:
    if state.get("confidence", 0.0) < _confidence_threshold():
        reason = "The knowledge-base match confidence was below the automatic resolution threshold."
    else:
        reason = state.get("escalation_reason") or "The ticket requires human review."

    escalate_ticket.invoke({"ticket_id": state["ticket_id"], "reason": reason})
    return {
        "final_status": "ESCALATED",
        "escalation_reason": reason,
        "current_node": "ESCALATE",
        "path": add_path(state, "ESCALATE"),
    }


def resolve(state: SmartHelpState) -> SmartHelpState:
    create_response.invoke(
        {
            "ticket_id": state["ticket_id"],
            "message": state.get("generated_response", ""),
            "sender_type": "AI",
        }
    )
    update_ticket_status.invoke({"ticket_id": state["ticket_id"], "status": "RESOLVED", "priority": state.get("priority")})
    return {
        "final_status": "RESOLVED",
        "current_node": "RESOLVE",
        "path": add_path(state, "RESOLVE"),
    }


def route_confidence(state: SmartHelpState) -> str:
    if state.get("confidence", 0.0) >= _confidence_threshold():
        return "GENERATE_RESPONSE"
    return "ESCALATE"


def route_sensitivity(state: SmartHelpState) -> str:
    if state.get("is_sensitive", False):
        return "ESCALATE"
    return "RESOLVE"


def build_graph():
    graph = StateGraph(SmartHelpState)
    graph.add_node("CLASSIFY_TICKET", classify_ticket)
    graph.add_node("SEARCH_KNOWLEDGE", search_knowledge)
    graph.add_node("CHECK_CONFIDENCE", check_confidence)
    graph.add_node("GENERATE_RESPONSE", generate_response)
    graph.add_node("CHECK_SENSITIVITY", check_sensitivity)
    graph.add_node("ESCALATE", escalate)
    graph.add_node("RESOLVE", resolve)

    graph.add_edge(START, "CLASSIFY_TICKET")
    graph.add_edge("CLASSIFY_TICKET", "SEARCH_KNOWLEDGE")
    graph.add_edge("SEARCH_KNOWLEDGE", "CHECK_CONFIDENCE")
    graph.add_conditional_edges(
        "CHECK_CONFIDENCE",
        route_confidence,
        {"GENERATE_RESPONSE": "GENERATE_RESPONSE", "ESCALATE": "ESCALATE"},
    )
    graph.add_edge("GENERATE_RESPONSE", "CHECK_SENSITIVITY")
    graph.add_conditional_edges(
        "CHECK_SENSITIVITY",
        route_sensitivity,
        {"ESCALATE": "ESCALATE", "RESOLVE": "RESOLVE"},
    )
    graph.add_edge("ESCALATE", END)
    graph.add_edge("RESOLVE", END)
    return graph.compile()


WORKFLOW = build_graph()


def final_result(state: SmartHelpState) -> dict[str, Any]:
    return {
        "ticketId": state["ticket_id"],
        "category": state.get("category"),
        "priority": state.get("priority", "MEDIUM"),
        "confidence": round(float(state.get("confidence", 0.0)), 2),
        "generatedResponse": state.get("generated_response", ""),
        "sensitive": bool(state.get("is_sensitive", False)),
        "finalStatus": state.get("final_status"),
        "path": " -> ".join(state.get("path", [])),
    }


def run_analysis(ticket_id: int) -> dict[str, Any]:
    state = WORKFLOW.invoke({"ticket_id": ticket_id, "path": [], "confidence": 0.0})
    return final_result(state)


def stream_analysis(ticket_id: int):
    state: SmartHelpState = {"ticket_id": ticket_id, "path": [], "confidence": 0.0}
    for chunk in WORKFLOW.stream(state):
        for node, update in chunk.items():
            running_state: SmartHelpState = {**state, "current_node": node}
            yield workflow_event(ticket_id, node, "RUNNING", running_state, NODE_MESSAGES.get(node))
            state.update(update)
            yield workflow_event(ticket_id, node, "COMPLETED", state, NODE_MESSAGES.get(node))
