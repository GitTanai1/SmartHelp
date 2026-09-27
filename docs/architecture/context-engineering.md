# Context Engineering

`ai-service/context.py` defines `ContextBuilder`, which constructs bounded
prompt context rather than concatenating a ticket and the full knowledge base.

| Layer | Current behavior |
| --- | --- |
| Policy | Static prompt policy directs the model to treat ticket and retrieval text as untrusted. |
| Task and domain state | Ticket description, selected category, priority, and confidence are included. |
| Retrieval | At most the configured number of articles and characters are included. |
| Evidence | Article ID, title, and category are returned to the API response and workflow event. |
| Tool history | Not persisted in prompt state; Java audit/run records remain outside model context. |

Configured character budgets are a deterministic safety guard, not an exact
token accounting mechanism. The builder indicates truncation so future UI and
telemetry can expose it. Authorization and workflow policy are never derived
from retrieved text.

The next increment will add typed durable state, compacted conversation
summaries where conversations are introduced, and provider token/cost reporting.
