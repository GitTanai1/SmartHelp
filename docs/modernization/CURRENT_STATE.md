# SmartHelp Current State

Audit date: 2026-09-26

## Repository structure

`frontend/` is an Angular 20 single-page application. `backend/` is a Java 21,
Spring Boot 4 application using Spring MVC and `JdbcTemplate`. `ai-service/`
is a Python FastAPI service containing a fixed LangGraph workflow. `database/`
contains MySQL schema and seed scripts; closely related copies also live in
`backend/src/main/resources`. Root Docker Compose starts MySQL only, while
`render.yaml` describes separate backend and AI Render services and Vercel hosts
the frontend.

## Existing architecture and request flow

The browser uses Angular services to call `/api/**` on Spring Boot. Spring
controllers call application services, which call JDBC repositories against
MySQL. For AI analysis, `AIController` validates ticket existence then calls
the Python `/analyze` or workflow SSE endpoint with Java's `HttpClient`.
Python reads tickets and knowledge through Spring REST endpoints, executes a
static seven-node LangGraph graph, and writes an AI reply plus ticket status
back through those same endpoints. SSE events travel Python -> Spring ->
Angular's native `EventSource`.

The Python implementation is a bounded, single LangGraph workflow with
optional direct LLM calls for classification, sensitivity, and drafting. It
uses a `ContextBuilder` to bound untrusted ticket and retrieval text, returns
knowledge-article provenance (article ID, title, and category), and routes
business writes through Java-owned, idempotent endpoints. Java persists a
run/event ledger for streamed executions. Python persists only bounded,
validated workflow state and its next node to a configured checkpoint directory
after each transition. A same-run stream can resume an interrupted run; Java
continues to own approval and terminal business state.

## Existing strengths to preserve

- Clear three-process separation already exists for UI, business API, and AI.
- The LangGraph path is finite and has deterministic fallback behavior without
  an API key.
- Controllers use request DTOs for most writes and repositories use prepared
  JDBC statements.
- Ticket, category, user, knowledge, and response CRUD functionality is
  present, with basic Java unit/controller tests and deterministic AI scenarios.
- CORS is allow-list based rather than wildcard based.

## Weaknesses

### P0 — correctness and security

- OIDC JWT mode now authenticates requests and enforces database-backed
  customer ticket/profile ownership and agent-only mutations. It remains
  opt-in until a real issuer and Angular client are configured; tenant
  isolation does not yet exist.
- Tenant isolation is not implemented; OIDC is intentionally opt-in until a
  real issuer and Angular client integration are available.
- The default local profile intentionally permits requests and must never be
  deployed as a production profile.
- Streamed run events can contain support-content-derived data, so retention
  and redaction policy need a product/security decision before production.

### P1 — maintainability

- Java packages are still organized primarily by technical layer rather than
  domain modules.
- API contracts remain manually represented in Java, Python, and TypeScript,
  but `docs/openapi.json` is a published v1 artifact checked by CI. Generated
  clients and broader cross-language contract tests remain future work.
- The run ledger supports cancellation, event cursors, and a Python
  checkpoint/resume protocol. The Angular explorer does not yet automatically
  reconnect and replay cursor-based events.

### P2 — production engineering

- Request IDs, readiness health, CI, Dependabot, and CodeQL now exist, but
  OpenTelemetry, structured cross-service metrics, secret scanning, and a
  reproducible integration environment still do not.
- The deployment configuration needs an external MySQL provider and Docker was
  not available in this audit environment.

### P3 — product polish

- The current frontend is Angular, not React. Migration should be route-by-route
  only after API contracts and authorization are stable.
- The Angular ticket detail has an operator-facing persisted run explorer, but
  no approval UI, React migration, demo recording, or measured benchmark report
  is checked in.

## Existing tests and baseline

- Java: JUnit/Mockito service and standalone MockMvc tests.
- Python: `test_workflow.py`, a script with three mocked deterministic
  end-to-end scenarios and two routing checks.
- Frontend: Angular's Vitest unit-test target now verifies versioned agent-run
  API calls with `HttpTestingController`. Component, accessibility, and E2E
  coverage remain to be added.
- Integration/E2E: none found.

Baseline attempted on 2026-09-26:

| Command | Result |
| --- | --- |
| `backend/.mvnw.cmd test` | Blocked: installed JDK is 20.0.1; project requires Java release 21. |
| `ai-service/python test_workflow.py` | Passed after installing pinned requirements: five checks and three mocked scenarios passed. |
| `frontend/npm run build` | Passed after `npm ci`; initial bundle is 626.34 kB raw / 125.54 kB estimated transfer. |
| Docker build | Blocked: Docker CLI is not installed. |

`npm ci` reported three dependency vulnerabilities (two moderate, one high). They
need a reviewed dependency upgrade rather than an unbounded `npm audit fix`.

## Configuration and secret audit

No committed live API key was found. Sample local MySQL passwords are in
`.env.example` and Compose defaults; they must remain development-only and be
replaced by deployment secret injection. `LLM_API_KEY` is environment supplied.

## Decision for the next increment

Do not rewrite Angular to React or MySQL to PostgreSQL during foundation work.
First make the existing API trustworthy: authenticated identity and resource
authorization, atomic/idempotent agent writes, migrations, and traceable API
contracts. The atomic AI resolution endpoint, request correlation, and Flyway
migrations introduced in this increment are intentionally compatible with the
current stack.
