"""Durable, bounded checkpoints for the local SmartHelp agent runtime.

Java remains the system of record for run status, approvals, and business
writes.  This store holds only the validated Python workflow state and the
next deterministic node so a restarted runtime can continue an in-flight run
without replaying completed nodes.  A shared durable volume must be configured
for production deployments through ``SMARTHELP_CHECKPOINT_DIR``.
"""

import json
import os
import tempfile
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


CHECKPOINT_VERSION = 1


class AgentCheckpoint(BaseModel):
    model_config = ConfigDict(extra="forbid")

    version: int = CHECKPOINT_VERSION
    run_id: str = Field(min_length=1, max_length=100)
    ticket_id: int = Field(gt=0)
    next_node: str | None = Field(default=None, max_length=80)
    state: dict[str, Any]


class CheckpointStore:
    """Atomically save and restore one JSON checkpoint per durable run ID."""

    def __init__(self, directory: str | Path | None = None):
        configured = directory or os.getenv("SMARTHELP_CHECKPOINT_DIR", ".runtime/agent-checkpoints")
        self.directory = Path(configured)

    def load(self, run_id: str, ticket_id: int) -> AgentCheckpoint | None:
        path = self._path(run_id)
        if not path.is_file():
            return None
        try:
            checkpoint = AgentCheckpoint.model_validate_json(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as exc:
            raise RuntimeError(f"Checkpoint for agent run {run_id} is unreadable") from exc
        if checkpoint.version != CHECKPOINT_VERSION:
            raise RuntimeError(f"Checkpoint for agent run {run_id} uses an unsupported version")
        if checkpoint.ticket_id != ticket_id:
            raise RuntimeError("Checkpoint ticket does not match the requested agent run")
        return checkpoint

    def save(self, run_id: str, ticket_id: int, state: dict[str, Any], next_node: str | None) -> None:
        checkpoint = AgentCheckpoint(
            run_id=run_id,
            ticket_id=ticket_id,
            next_node=next_node,
            state=state,
        )
        self.directory.mkdir(parents=True, exist_ok=True)
        destination = self._path(run_id)
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=self.directory, prefix=f".{destination.name}.", delete=False
        ) as temporary:
            temporary.write(checkpoint.model_dump_json())
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary_path = Path(temporary.name)
        os.replace(temporary_path, destination)

    @staticmethod
    def _safe_run_id(run_id: str) -> str:
        if not run_id or any(character not in "0123456789abcdefABCDEF-" for character in run_id):
            raise RuntimeError("Agent run ID is not safe for checkpoint storage")
        return run_id

    def _path(self, run_id: str) -> Path:
        return self.directory / f"{self._safe_run_id(run_id)}.json"
