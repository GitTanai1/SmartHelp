"""Bounded composition of untrusted ticket and knowledge context for the LLM."""

from typing import Any

from pydantic import BaseModel, Field


class ContextBudget(BaseModel):
    """Character budgets are a deterministic, provider-neutral token proxy."""

    ticket_characters: int = Field(default=4_000, ge=1)
    article_characters: int = Field(default=1_500, ge=1)
    max_articles: int = Field(default=3, ge=0)


class BuiltContext(BaseModel):
    ticket_text: str
    knowledge_text: str
    included_article_ids: list[int] = Field(default_factory=list)
    truncated: bool = False


class ContextBuilder:
    """
    Creates model input from explicitly bounded, untrusted data.

    Policy and tool permissions deliberately do not live here: they belong to
    application workflow code and cannot be supplied by retrieved content.
    """

    def __init__(self, budget: ContextBudget | None = None) -> None:
        self._budget = budget or ContextBudget()

    def build(self, ticket_text: str, articles: list[dict[str, Any]]) -> BuiltContext:
        bounded_ticket = ticket_text[: self._budget.ticket_characters]
        truncated = len(bounded_ticket) < len(ticket_text)
        segments: list[str] = []
        article_ids: list[int] = []

        for article in articles[: self._budget.max_articles]:
            content = str(article.get("content", ""))
            bounded_content = content[: self._budget.article_characters]
            truncated = truncated or len(bounded_content) < len(content)
            title = str(article.get("title", "Untitled"))[:200]
            segments.append(f"{title}: {bounded_content}")
            if isinstance(article.get("id"), int):
                article_ids.append(article["id"])

        return BuiltContext(
            ticket_text=bounded_ticket,
            knowledge_text="\n\n".join(segments),
            included_article_ids=article_ids,
            truncated=truncated,
        )
