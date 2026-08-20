from pydantic import BaseModel, Field

from langchain_core.prompts import PromptTemplate


class ClassificationOutput(BaseModel):
    category: str | None = Field(
        default=None,
        description="Best support category, or null when no category fits.",
    )
    priority: str = Field(
        default="MEDIUM",
        description="One of LOW, MEDIUM, or HIGH.",
    )


class SensitivityOutput(BaseModel):
    sensitive: bool = Field(description="True when a human should review before resolution.")
    reason: str = Field(default="", description="Short reason for the sensitivity decision.")


CLASSIFICATION_PROMPT = PromptTemplate.from_template(
    """
You classify customer support tickets for SmartHelp.

Allowed categories:
- Billing
- Account Access
- Subscription
- Refunds
- Security
- Technical Support

Allowed priorities: LOW, MEDIUM, HIGH.

Ticket:
{ticket_text}

Return the most likely category and priority. Use null category when the ticket is outside SmartHelp support.
"""
)

RESPONSE_PROMPT = PromptTemplate.from_template(
    """
You are SmartHelp's support assistant.

Answer only from the provided knowledge articles. If the knowledge is insufficient, say the ticket should be escalated.
Keep the response concise, polite, and actionable.

Ticket:
{ticket_text}

Knowledge articles:
{knowledge_text}
"""
)

SENSITIVITY_PROMPT = PromptTemplate.from_template(
    """
Decide if this support ticket must be reviewed by a human before any automatic resolution.

Sensitive examples include unauthorized access, suspected account compromise, fraud, stolen credentials,
privacy requests, legal threats, chargebacks, disputed charges, and urgent outages.

Ticket:
{ticket_text}

Return a boolean and a short reason.
"""
)
