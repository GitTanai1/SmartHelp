"""Versioned public contracts at the Python agent-runtime boundary.

These models validate FastAPI and SSE payloads before they leave the service.
Java retains matching DTOs during the compatibility period; publishing a shared
schema or generated client is the next migration step.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


CONTRACT_VERSION = "v1"

NodeStatus = Literal["PENDING", "RUNNING", "COMPLETED", "SKIPPED", "FAILED"]


class AnalyzeTicketRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticketId: int = Field(gt=0)


class Evidence(BaseModel):
    model_config = ConfigDict(extra="forbid")

    articleId: int | None = None
    title: str
    categoryId: int | None = None


class WorkflowPublicState(BaseModel):
    model_config = ConfigDict(extra="forbid")

    category: str | None = None
    priority: str | None = None
    confidence: float = Field(ge=0, le=1)
    sensitive: bool
    finalStatus: Literal["RESOLVED", "ESCALATED", "WAITING_FOR_APPROVAL"] | None = None
    knowledgeCount: int = Field(ge=0)
    evidence: list[Evidence] = Field(default_factory=list)
    generatedResponse: str = ""
    verificationFailed: bool = False
    path: list[str] = Field(default_factory=list)


class WorkflowEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal["v1"] = CONTRACT_VERSION
    ticketId: int = Field(gt=0)
    runId: str | None = Field(default=None, max_length=100)
    node: str = Field(min_length=1, max_length=80)
    status: NodeStatus
    state: WorkflowPublicState
    message: str = Field(min_length=1, max_length=1_000)


class AiAnalysisResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    contractVersion: Literal["v1"] = CONTRACT_VERSION
    ticketId: int = Field(gt=0)
    category: str | None = None
    priority: str
    confidence: float = Field(ge=0, le=1)
    generatedResponse: str
    sensitive: bool
    finalStatus: Literal["RESOLVED", "ESCALATED", "WAITING_FOR_APPROVAL"] | None = None
    evidence: list[Evidence] = Field(default_factory=list)
    path: str
