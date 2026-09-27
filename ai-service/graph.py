import os
import re
import logging
import time
from typing import Any

from langgraph.graph import END, START, StateGraph

from checkpoint import CheckpointStore
from contracts import AiAnalysisResult, CONTRACT_VERSION
from context import ContextBuilder
from prompts import (
    CLASSIFICATION_PROMPT,
    RESPONSE_PROMPT,
    SENSITIVITY_PROMPT,
    ClassificationOutput,
    SensitivityOutput,
)
from state import NODE_MESSAGES, SmartHelpState, add_path, initial_agent_state, validate_agent_state, workflow_event
from tools import (
    escalate_ticket,
    get_customer_history,
    get_ticket,
    request_resolution_approval,
    search_knowledge_base,
)


LOGGER = logging.getLogger(__name__)


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

PROMPT_INJECTION_TERMS = (
    "ignore previous instructions",
    "ignore all previous instructions",
    "reveal your system prompt",
    "developer message",
    "you are now",
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
        "timeout": _llm_timeout_seconds(),
        "max_retries": 1,
    }
    base_url = os.getenv("LLM_BASE_URL", "").strip()
    if base_url:
        kwargs["base_url"] = base_url
    return ChatOpenAI(**kwargs)


def _llm_timeout_seconds() -> float:
    try:
        return max(1.0, float(os.getenv("LLM_TIMEOUT_SECONDS", "30")))
    except ValueError:
        return 30.0


def _classify_with_llm(ticket_text: str) -> ClassificationOutput | None:
    llm = _build_llm()
    if llm is None:
        return None
    try:
        structured = llm.with_structured_output(ClassificationOutput)
        return structured.invoke(CLASSIFICATION_PROMPT.format(ticket_text=ticket_text))
    except Exception as exc:
        LOGGER.warning("LLM classification failed; using deterministic fallback: %s", exc)
        return None


def _sensitivity_with_llm(ticket_text: str) -> SensitivityOutput | None:
    llm = _build_llm()
    if llm is None:
        return None
    try:
        structured = llm.with_structured_output(SensitivityOutput)
        return structured.invoke(SENSITIVITY_PROMPT.format(ticket_text=ticket_text))
    except Exception as exc:
        LOGGER.warning("LLM sensitivity check failed; using deterministic fallback: %s", exc)
        return None


def _generate_with_llm(ticket_text: str, knowledge_text: str) -> str | None:
    llm = _build_llm()
    if llm is None:
        return None
    try:
        response = llm.invoke(RESPONSE_PROMPT.format(ticket_text=ticket_text, knowledge_text=knowledge_text))
        return str(response.content).strip()
    except Exception as exc:
        LOGGER.warning("LLM response generation failed; using deterministic fallback: %s", exc)
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
        "user_id": ticket.get("userId"),
        "ticket_description": ticket_text,
        "category_id": ticket.get("categoryId"),
        "category": category,
        "priority": priority,
        "current_node": "CLASSIFY_TICKET",
        "path": add_path(state, "CLASSIFY_TICKET"),
        "iteration_count": len(state.get("path", [])) + 1,
    }


def search_knowledge(state: SmartHelpState) -> SmartHelpState:
    category_id = state.get("category_id")
    ticket = state.get("ticket", {})
    if category_id is not None:
        articles = search_knowledge_base.invoke({"category_id": category_id, "query": None})
    else:
        subject = ticket.get("subject") or state.get("category") or ""
        articles = search_knowledge_base.invoke({"category_id": None, "query": subject})

    evidence = [
        {
            "articleId": article.get("id"),
            "title": article.get("title", "Untitled"),
            "categoryId": article.get("categoryId"),
        }
        for article in articles[:3]
    ]
    return {
        "knowledge_results": articles,
        "evidence": evidence,
        "current_node": "SEARCH_KNOWLEDGE",
        "path": add_path(state, "SEARCH_KNOWLEDGE"),
        "iteration_count": len(state.get("path", [])) + 1,
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
        "iteration_count": len(state.get("path", [])) + 1,
    }


def generate_response(state: SmartHelpState) -> SmartHelpState:
    articles = state.get("knowledge_results", [])
    context = ContextBuilder().build(state.get("ticket_description", ""), articles)
    llm_response = _generate_with_llm(context.ticket_text, context.knowledge_text)
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
        "iteration_count": len(state.get("path", [])) + 1,
    }


def check_sensitivity(state: SmartHelpState) -> SmartHelpState:
    ticket_text = state.get("ticket_description", "")
    lowered = ticket_text.lower()
    llm_result = _sensitivity_with_llm(ticket_text)
    injection_attempt = any(term in lowered for term in PROMPT_INJECTION_TERMS)
    sensitive = (
        any(term in lowered for term in SENSITIVE_TERMS)
        or injection_attempt
        or state.get("category") == "Security"
    )
    reason = "Sensitive account, billing, legal, privacy, or outage language detected."
    if injection_attempt:
        reason = "Potential prompt-injection instruction detected; human review is required."
    if llm_result is not None:
        sensitive = llm_result.sensitive or sensitive
        if llm_result.reason:
            reason = llm_result.reason

    return {
        "is_sensitive": sensitive,
        "escalation_reason": reason if sensitive else None,
        "current_node": "CHECK_SENSITIVITY",
        "path": add_path(state, "CHECK_SENSITIVITY"),
        "iteration_count": len(state.get("path", [])) + 1,
    }


def escalate(state: SmartHelpState) -> SmartHelpState:
    if state.get("confidence", 0.0) < _confidence_threshold():
        reason = "The knowledge-base match confidence was below the automatic resolution threshold."
    elif state.get("verification_failure"):
        reason = state["verification_failure"]
    else:
        reason = state.get("escalation_reason") or "The ticket requires human review."

    escalate_ticket.invoke({"ticket_id": state["ticket_id"], "reason": reason})
    return {
        "final_status": "ESCALATED",
        "status": "COMPLETED",
        "escalation_reason": reason,
        "current_node": "ESCALATE",
        "path": add_path(state, "ESCALATE"),
        "iteration_count": len(state.get("path", [])) + 1,
    }


def resolve(state: SmartHelpState) -> SmartHelpState:
    request_resolution_approval.invoke(
        {
            "ticket_id": state["ticket_id"],
            "message": state.get("generated_response", ""),
            "priority": state.get("priority"),
        }
    )
    return {
        "final_status": "WAITING_FOR_APPROVAL",
        "status": "WAITING_FOR_APPROVAL",
        "current_node": "RESOLVE",
        "path": add_path(state, "RESOLVE"),
        "iteration_count": len(state.get("path", [])) + 1,
    }


def verify_response(state: SmartHelpState) -> SmartHelpState:
    """Bounded deterministic verification before a resolution reaches approval."""
    if not state.get("knowledge_results"):
        failure = "No retrieved evidence was available to support a resolution proposal."
    elif not state.get("generated_response", "").strip():
        failure = "The generated response was empty and cannot be proposed for approval."
    else:
        failure = None
    return {
        "verification_failure": failure,
        "current_node": "VERIFY_RESPONSE",
        "path": add_path(state, "VERIFY_RESPONSE"),
        "iteration_count": len(state.get("path", [])) + 1,
    }


def route_confidence(state: SmartHelpState) -> str:
    if state.get("confidence", 0.0) >= _confidence_threshold():
        return "GENERATE_RESPONSE"
    return "ESCALATE"


def route_sensitivity(state: SmartHelpState) -> str:
    if state.get("is_sensitive", False):
        return "ESCALATE"
    return "VERIFY_RESPONSE"


def route_verification(state: SmartHelpState) -> str:
    return "ESCALATE" if state.get("verification_failure") else "RESOLVE"


def build_graph():
    graph = StateGraph(SmartHelpState)
    graph.add_node("CLASSIFY_TICKET", classify_ticket)
    graph.add_node("SEARCH_KNOWLEDGE", search_knowledge)
    graph.add_node("CHECK_CONFIDENCE", check_confidence)
    graph.add_node("GENERATE_RESPONSE", generate_response)
    graph.add_node("CHECK_SENSITIVITY", check_sensitivity)
    graph.add_node("VERIFY_RESPONSE", verify_response)
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
        {"ESCALATE": "ESCALATE", "VERIFY_RESPONSE": "VERIFY_RESPONSE"},
    )
    graph.add_conditional_edges(
        "VERIFY_RESPONSE",
        route_verification,
        {"ESCALATE": "ESCALATE", "RESOLVE": "RESOLVE"},
    )
    graph.add_edge("ESCALATE", END)
    graph.add_edge("RESOLVE", END)
    return graph.compile()


WORKFLOW = build_graph()


NODE_HANDLERS = {
    "CLASSIFY_TICKET": classify_ticket,
    "SEARCH_KNOWLEDGE": search_knowledge,
    "CHECK_CONFIDENCE": check_confidence,
    "GENERATE_RESPONSE": generate_response,
    "CHECK_SENSITIVITY": check_sensitivity,
    "VERIFY_RESPONSE": verify_response,
    "ESCALATE": escalate,
    "RESOLVE": resolve,
}

MAX_GRAPH_STEPS = len(NODE_HANDLERS)


class AgentBudgetExceeded(RuntimeError):
    """Raised after persisting a terminal budget failure checkpoint."""


def _bounded_int_environment(name: str, default: int, minimum: int, maximum: int) -> int:
    try:
        return min(maximum, max(minimum, int(os.getenv(name, str(default)))))
    except ValueError:
        return default


def agent_max_steps() -> int:
    return _bounded_int_environment("SMARTHELP_AGENT_MAX_STEPS", MAX_GRAPH_STEPS, 1, MAX_GRAPH_STEPS)


def agent_max_wall_seconds() -> int:
    return _bounded_int_environment("SMARTHELP_AGENT_MAX_WALL_SECONDS", 120, 1, 300)


def budget_failure_reason(state: SmartHelpState, started_at: float, now: float, max_steps: int, max_wall_seconds: int) -> str | None:
    """Return a deterministic budget error without executing another workflow node."""
    if len(state.get("path", [])) >= max_steps:
        return f"Agent step budget of {max_steps} was exhausted before the next workflow node."
    if now - started_at >= max_wall_seconds:
        return f"Agent wall-time budget of {max_wall_seconds} seconds was exhausted."
    return None


def next_node(state: SmartHelpState) -> str | None:
    """Return the one safe successor for a validated bounded workflow state."""
    current = state.get("current_node")
    if current is None:
        return "CLASSIFY_TICKET"
    if current == "CLASSIFY_TICKET":
        return "SEARCH_KNOWLEDGE"
    if current == "SEARCH_KNOWLEDGE":
        return "CHECK_CONFIDENCE"
    if current == "CHECK_CONFIDENCE":
        return route_confidence(state)
    if current == "GENERATE_RESPONSE":
        return "CHECK_SENSITIVITY"
    if current == "CHECK_SENSITIVITY":
        return route_sensitivity(state)
    if current == "VERIFY_RESPONSE":
        return route_verification(state)
    if current in {"ESCALATE", "RESOLVE"}:
        return None
    raise RuntimeError(f"Cannot resume unknown workflow node: {current}")


def _state_for_run(ticket_id: int, run_id: str | None, store: CheckpointStore) -> tuple[SmartHelpState, str | None]:
    """Load a validated checkpoint, or persist the initial replay-safe boundary."""
    if run_id:
        checkpoint = store.load(run_id, ticket_id)
        if checkpoint is not None:
            state = validate_agent_state(checkpoint.state).model_dump()
            return state, checkpoint.next_node
    state = initial_agent_state(ticket_id, run_id)
    next_step = next_node(state)
    if run_id:
        store.save(run_id, ticket_id, state, next_step)
    return state, next_step


def _run_with_checkpoints(ticket_id: int, run_id: str | None = None):
    """Execute only uncompleted nodes, persisting after each state transition.

    The checkpoint is intentionally written *before* every node and after every
    node.  If a crash occurs while a terminal write is in progress, Java's
    run-scoped idempotency / approval record makes the single repeated terminal
    node safe on resume.
    """
    store = CheckpointStore()
    state, pending_node = _state_for_run(ticket_id, run_id, store)
    started_at = time.monotonic()
    max_steps = agent_max_steps()
    max_wall_seconds = agent_max_wall_seconds()
    while pending_node is not None:
        failure = budget_failure_reason(state, started_at, time.monotonic(), max_steps, max_wall_seconds)
        if failure:
            failed_state: SmartHelpState = {
                **state,
                "status": "FAILED",
                "error": failure,
                "current_node": pending_node,
            }
            validated_failed_state = validate_agent_state(failed_state).model_dump()
            if run_id:
                store.save(run_id, ticket_id, validated_failed_state, None)
            raise AgentBudgetExceeded(failure)
        handler = NODE_HANDLERS[pending_node]
        running_state: SmartHelpState = {**state, "current_node": pending_node}
        yield pending_node, "RUNNING", running_state
        update = handler(state)
        state.update(update)
        validated_state = validate_agent_state(state).model_dump()
        pending_node = next_node(validated_state)
        state = validated_state
        if run_id:
            store.save(run_id, ticket_id, state, pending_node)
        yield state["current_node"], "COMPLETED", state



def final_result(state: SmartHelpState) -> dict[str, Any]:
    return AiAnalysisResult(
        contractVersion=CONTRACT_VERSION,
        ticketId=state["ticket_id"],
        category=state.get("category"),
        priority=state.get("priority", "MEDIUM"),
        confidence=round(float(state.get("confidence", 0.0)), 2),
        generatedResponse=state.get("generated_response", ""),
        sensitive=bool(state.get("is_sensitive", False)),
        finalStatus=state.get("final_status"),
        evidence=state.get("evidence", []),
        path=" -> ".join(state.get("path", [])),
    ).model_dump(mode="json")


def run_analysis(ticket_id: int, run_id: str | None = None) -> dict[str, Any]:
    state: SmartHelpState | None = None
    for _, status, transition_state in _run_with_checkpoints(ticket_id, run_id):
        if status == "COMPLETED":
            state = transition_state
    if state is None:
        state, _ = _state_for_run(ticket_id, run_id, CheckpointStore())
    return final_result(validate_agent_state(state).model_dump())


def stream_analysis(ticket_id: int, run_id: str | None = None):
    for node, status, state in _run_with_checkpoints(ticket_id, run_id):
        yield workflow_event(ticket_id, node, status, state, NODE_MESSAGES.get(node))
