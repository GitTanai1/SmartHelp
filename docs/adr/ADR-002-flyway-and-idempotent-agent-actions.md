# ADR-002: Flyway migrations and idempotent Java-owned agent actions

Status: Accepted

The former duplicate schema scripts could drift and did not provide a safe
history for production changes. Flyway migrations are the production schema
authority. Legacy scripts remain only for local reset/sample-data workflows.

Agent resolution and escalation are single Java transactions: create the AI
note, update ticket state, mark an idempotency action complete, and append an
application audit event. A repeated key with the same request returns the
current ticket; a reused key for another action is rejected.

Consequences: callers must supply `Idempotency-Key`; future agent writes must
use the same Java-owned pattern before they are exposed.
