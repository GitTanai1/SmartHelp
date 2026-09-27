# SmartHelp Threat Model

## Assets

- Ticket and customer data in MySQL
- Knowledge articles and retrieved evidence
- OIDC bearer tokens
- Agent action/idempotency records and run-event payloads
- LLM provider credentials

## Trust boundaries

Browser → Spring Boot is an authenticated API boundary in production. Spring
Boot → Python carries a caller token and correlation ID. Python → Spring Boot
returns through Java authorization for every tool call. The LLM and retrieved
text are untrusted inputs, never authorization sources.

## Current mitigations

| Threat | Mitigation |
| --- | --- |
| Cross-customer ticket access | OIDC email-to-user mapping and server-side ticket ownership checks |
| Agent duplicate writes | Java transaction plus idempotency-key ledger |
| Partial agent actions | Java-owned transactional resolution/escalation |
| High-risk agent resolution | Java-owned pending approval; only an agent-role operator may approve the write |
| Approval bypass after cancellation | Run ID propagates to tools; cancelled/waiting runs cannot perform a resolution write |
| Prompt injection | Bounded untrusted context, prompt delimiters, deterministic injection escalation, evaluation case |
| Run debugging/data tampering | Java-owned persisted run ledger; agent-only access |
| Credential leaks | Environment-provided secrets, `.env` ignore, CI CodeQL/Dependabot |

## Open risks

Tenant isolation, Angular OIDC login, full Python checkpoint/resume, model
provider evaluation, and end-to-end OpenTelemetry remain open roadmap work.
