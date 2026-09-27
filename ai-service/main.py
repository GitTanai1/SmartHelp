import os
import asyncio
import hmac
import json
import uuid
from typing import Annotated

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from contracts import AiAnalysisResult, AnalyzeTicketRequest
from graph import run_analysis, stream_analysis
from state import workflow_event
from tools import agent_run_id, authorization_header, idempotency_key, request_id


app = FastAPI(title="SmartHelp AI Service")

SPRING_BOOT_BASE_URL = os.getenv("SPRING_BOOT_BASE_URL", "http://localhost:8080")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4.1-mini")
AGENT_SHARED_SECRET = os.getenv("SMARTHELP_AGENT_SHARED_SECRET", "")
REQUIRE_AGENT_TOKEN = os.getenv("SMARTHELP_AGENT_TOKEN_REQUIRED", "false").lower() == "true"

if REQUIRE_AGENT_TOKEN and not AGENT_SHARED_SECRET:
    raise RuntimeError("SMARTHELP_AGENT_SHARED_SECRET is required when SMARTHELP_AGENT_TOKEN_REQUIRED=true")


def internal_request_authorized(presented_token: str | None, expected_secret: str, required: bool) -> bool:
    """Allow local-open development, but require a constant-time token check when configured."""
    if not required:
        return True
    return presented_token is not None and hmac.compare_digest(presented_token, expected_secret)


def require_internal_request(presented_token: str | None) -> None:
    if not internal_request_authorized(presented_token, AGENT_SHARED_SECRET, REQUIRE_AGENT_TOKEN):
        raise HTTPException(status_code=401, detail="Agent runtime accepts requests only from the core API")


def resolution_idempotency_key(run_id: str | None) -> str:
    """Make a resumed run reuse its Java-owned side-effect idempotency key."""
    return f"{run_id}:escalate" if run_id else f"{uuid.uuid4()}:escalate"


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "UP",
        "service": "smarthelp-ai",
        "llmConfigured": bool(LLM_API_KEY),
    }


@app.post("/analyze")
def analyze(
    request: AnalyzeTicketRequest,
    request_id_header: Annotated[str | None, Header(alias="X-Request-ID")] = None,
    authorization: Annotated[str | None, Header()] = None,
    agent_run_id_header: Annotated[str | None, Header(alias="X-Agent-Run-ID")] = None,
    agent_token: Annotated[str | None, Header(alias="X-SmartHelp-Agent-Token")] = None,
) -> AiAnalysisResult:
    require_internal_request(agent_token)
    request_token = request_id.set(request_id_header)
    idempotency_token = idempotency_key.set(resolution_idempotency_key(agent_run_id_header))
    authorization_token = authorization_header.set(authorization)
    run_id_token = agent_run_id.set(agent_run_id_header)
    try:
        return AiAnalysisResult.model_validate(run_analysis(request.ticketId, agent_run_id_header))
    finally:
        idempotency_key.reset(idempotency_token)
        authorization_header.reset(authorization_token)
        agent_run_id.reset(run_id_token)
        request_id.reset(request_token)


@app.get("/workflow/{ticket_id}/stream")
async def stream_workflow(
    ticket_id: int,
    request_id_header: Annotated[str | None, Header(alias="X-Request-ID")] = None,
    authorization: Annotated[str | None, Header()] = None,
    agent_run_id_header: Annotated[str | None, Header(alias="X-Agent-Run-ID")] = None,
    agent_token: Annotated[str | None, Header(alias="X-SmartHelp-Agent-Token")] = None,
) -> StreamingResponse:
    require_internal_request(agent_token)
    async def event_source():
        request_token = request_id.set(request_id_header)
        idempotency_token = idempotency_key.set(resolution_idempotency_key(agent_run_id_header))
        authorization_token = authorization_header.set(authorization)
        run_id_token = agent_run_id.set(agent_run_id_header)
        try:
            for event in stream_analysis(ticket_id, agent_run_id_header):
                yield f"data: {json.dumps(event)}\n\n"
                await asyncio.sleep(0.15)
        except Exception as exc:
            state = {
                "ticket_id": ticket_id,
                "error": str(exc),
                "path": [],
                "confidence": 0.0,
            }
            event = workflow_event(ticket_id, "WORKFLOW", "FAILED", state, str(exc))
            yield f"data: {json.dumps(event)}\n\n"
        finally:
            idempotency_key.reset(idempotency_token)
            authorization_header.reset(authorization_token)
            agent_run_id.reset(run_id_token)
            request_id.reset(request_token)

    return StreamingResponse(
        event_source(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
