import os
import asyncio
import json

from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from graph import run_analysis, stream_analysis
from state import workflow_event


app = FastAPI(title="SmartHelp AI Service")

SPRING_BOOT_BASE_URL = os.getenv("SPRING_BOOT_BASE_URL", "http://localhost:8080")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4.1-mini")


class AnalyzeTicketRequest(BaseModel):
    ticketId: int


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "UP",
        "service": "smarthelp-ai",
        "llmConfigured": bool(LLM_API_KEY),
    }


@app.post("/analyze")
def analyze(request: AnalyzeTicketRequest) -> dict[str, object]:
    return run_analysis(request.ticketId)


@app.get("/workflow/{ticket_id}/stream")
async def stream_workflow(ticket_id: int) -> StreamingResponse:
    async def event_source():
        try:
            for event in stream_analysis(ticket_id):
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

    return StreamingResponse(
        event_source(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
