# SmartHelp

AI-native customer-support automation with bounded LangGraph workflows,
grounded knowledge retrieval, Java-owned side effects, and live execution
updates. It is being evolved incrementally from its original teaching-project
foundation rather than rewritten wholesale.

---

## Features

- **Ticket management** — create, list, filter, update, and delete support tickets
- **Knowledge base** — create and search support articles used by the AI workflow
- **User management** — customers and support agents
- **AI analysis** — LangGraph workflow classifies tickets, searches knowledge, resolves or escalates
- **Live workflow visualization** — real-time SVG graph driven by SSE events from LangGraph execution
- **Responsive Angular UI** — dashboard, ticket list with filters, ticket detail, knowledge base

---

## Architecture

```
Browser
  ↓ REST / SSE
Angular (port 4200)
  ↓ REST
Spring Boot (port 8080)
  ↓ JDBC
MySQL (port 3306)

Spring Boot (port 8080)
  ↓ REST
Python FastAPI AI Service (port 8000)
  ↓ LangChain + LangGraph
OpenAI-compatible LLM
```

---

## Technology Stack

| Layer | Technology |
| --- | --- |
| Frontend | Angular 20, TypeScript, Bootstrap 5, RxJS |
| Backend | Java 21, Spring Boot 4, Spring MVC, Spring JDBC, JdbcTemplate |
| Database | MySQL 8 |
| AI Service | Python 3, FastAPI, LangChain, LangGraph |
| Communication | HTTP REST, Server-Sent Events (SSE) |
| Testing | JUnit 5, Mockito, JaCoCo |
| Logging | SLF4J + Logback |
| Operations | Spring Boot Actuator health probes and Micrometer metrics |

---

## Database Schema

Core operational tables:

```
users          → tickets (one user → many tickets)
categories     → tickets (one category → many tickets, category_id nullable)
categories     → knowledge_articles
tickets        → ticket_responses (ON DELETE CASCADE)
```

The production schema is versioned under `backend/src/main/resources/db/migration`.
`database/schema.sql` remains a local reset script only.

---

## REST API Overview

`/api/v1` is the versioned contract. The original `/api` routes remain as a
temporary compatibility alias while the Angular client and Python tools migrate.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/actuator/health` | Liveness and readiness status |
| POST/GET/PUT/DELETE | `/api/tickets` | Ticket CRUD |
| POST/GET | `/api/tickets/{id}/responses` | Ticket responses |
| POST | `/api/tickets/{id}/analyze` | Blocking AI analysis |
| GET | `/api/tickets/{id}/workflow` | SSE live workflow stream |
| POST | `/api/tickets/{id}/ai-resolution` | Internal idempotent AI resolution |
| POST | `/api/tickets/{id}/ai-escalation` | Internal idempotent AI escalation |
| GET | `/api/v1/tickets/{id}/agent-runs` | Persisted operator run history |
| GET | `/api/v1/tickets/{id}/agent-runs/{runId}/events` | Persisted workflow events |
| POST/GET/PUT/DELETE | `/api/users` | User CRUD |
| GET | `/api/categories` | Category lookup |
| POST/GET/PUT/DELETE | `/api/knowledge` | Knowledge article CRUD |

---

## LangGraph Workflow

```
CLASSIFY_TICKET
      ↓
SEARCH_KNOWLEDGE
      ↓
CHECK_CONFIDENCE
   /         \
High          Low
  ↓             ↓
GENERATE     ESCALATE
  ↓
CHECK_SENSITIVITY
   /           \
Sensitive    Not sensitive
  ↓               ↓
ESCALATE       VERIFY_RESPONSE
                   ↓
             REQUEST_APPROVAL
```

Three test scenarios (a grounded resolution now waits for an authorized operator's approval):
1. Billing ticket with matching articles → **WAITING_FOR_APPROVAL**
2. Hardware warranty with no articles → **ESCALATED** (low confidence)
3. Security/unauthorized access ticket → **ESCALATED** (sensitive)

---

## Setup

### Prerequisites

- Java 21 (tested with Eclipse Temurin 21.0.12)
- Maven (or use `mvnw.cmd` wrapper in `backend/`)
- Node.js 22.x + npm 11.x
- Python 3.10+
- MySQL 8.0 (local or Docker)

---

## MySQL Setup

### Option A: Local MySQL (no Docker)

```powershell
# 1. Start MySQL server
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe' `
    --basedir='C:\Program Files\MySQL\MySQL Server 8.0' `
    --datadir='<your-data-dir>' `
    --port=3306 --console

# 2. Create the local application user/database (in a new terminal)
mysql -h 127.0.0.1 -P 3306 -u root < database/dev-user.sql
# 3. Start the backend once. Flyway creates/migrates the schema.
# 4. Optionally load idempotent sample data after Flyway completes.
Get-Content -Raw backend/src/main/resources/data.sql |
  mysql -h 127.0.0.1 -P 3306 -u smarthelp -psmarthelp smarthelp
```

### Option B: Docker Compose (requires Docker)

```powershell
docker compose up -d mysql
# Start the backend once to apply Flyway migrations to the empty database.
# Then load only optional idempotent sample data:
Get-Content -Raw backend/src/main/resources/data.sql |
  docker exec -i smarthelp-mysql mysql -u smarthelp -psmarthelp smarthelp
```

---

## Backend Startup

```powershell
$env:JAVA_HOME='C:\path\to\jdk21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd backend
.\mvnw.cmd spring-boot:run
```

Verify: `Invoke-RestMethod http://localhost:8080/actuator/health`

The maintained v1 HTTP contract is [docs/openapi.json](docs/openapi.json).
It covers stable ticket and agent-run endpoints; the legacy `/api` aliases are
deliberately excluded.

Expected:
```json
{ "status": "UP", "service": "smarthelp-backend", "database": "UP" }
```

---

## Frontend Startup

```powershell
cd frontend
npm install
npm start
```

Open: `http://localhost:4200`

---

## AI Service Startup

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000 --reload
```

Verify: `Invoke-RestMethod http://localhost:8000/health`

---

## Environment Variables

### Spring Boot (`backend/src/main/resources/application.properties`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Server port |
| `SMARTHELP_DB_URL` | `jdbc:mysql://localhost:3306/smarthelp` | MySQL URL |
| `SMARTHELP_DB_USERNAME` | `smarthelp` | DB username |
| `SMARTHELP_DB_PASSWORD` | `smarthelp` | DB password |
| `SMARTHELP_AI_BASE_URL` | `http://localhost:8000` | AI service URL |
| `SMARTHELP_FRONTEND_ORIGIN` | `http://localhost:4200` | CORS origin |
| `SMARTHELP_OIDC_ENABLED` | `false` | Enables production OIDC JWT validation |
| `SMARTHELP_OIDC_ISSUER_URI` | *(required if OIDC enabled)* | Trusted OIDC issuer |

### AI Service (`ai-service/.env.example`)

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_BOOT_BASE_URL` | `http://localhost:8080` | Backend URL for tools |
| `LLM_API_KEY` | *(empty)* | OpenAI-compatible key (leave empty for deterministic mode) |
| `LLM_BASE_URL` | *(empty)* | Custom LLM provider URL |
| `LLM_MODEL` | `gpt-4.1-mini` | Model name |
| `SMARTHELP_CONFIDENCE_THRESHOLD` | `0.70` | Auto-resolve threshold |

---

## Tests

### Backend (JUnit + Mockito + JaCoCo)

```powershell
cd backend
.\mvnw.cmd test
```

Results: 16 tests, 0 failures. JaCoCo report at `target/site/jacoco/index.html`.

### AI Workflow (Python, deterministic)

```powershell
cd ai-service
.\.venv\Scripts\python.exe test_workflow.py
```

Results: all 3 scenarios pass.

---

## Coverage

| Metric | Result |
| --- | --- |
| Instructions | 28.1% (722/2572) |
| Lines | 31.1% (159/511) |
| Methods | 35.6% (62/174) |

Coverage reflects that JDBC repositories and AI integration classes require
live infrastructure to test. Service and controller logic that CAN be unit
tested has good individual coverage.

---

## Example API Requests

```powershell
# Health check
Invoke-RestMethod http://localhost:8080/actuator/health

# Create a ticket
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/tickets `
  -ContentType 'application/json' `
  -Body '{"userId":1,"subject":"Cannot log in","description":"Password reset email not arriving","priority":"HIGH"}'

# List open tickets
Invoke-RestMethod 'http://localhost:8080/api/tickets?status=OPEN'

# Run AI analysis (blocking)
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/tickets/1/analyze -Body '{}'
```

---

## AI Workflow Example

```powershell
# Open SSE stream (keep terminal open — events arrive in real time)
Invoke-WebRequest http://localhost:8080/api/tickets/1/workflow -Method Get
```

Events arrive as:
```
data: {"ticketId":1,"node":"CLASSIFY_TICKET","status":"RUNNING",...}
data: {"ticketId":1,"node":"CLASSIFY_TICKET","status":"COMPLETED","state":{"category":"Billing",...}}
...
data: {"ticketId":1,"node":"RESOLVE","status":"COMPLETED","state":{"finalStatus":"WAITING_FOR_APPROVAL",...}}
```

---

## Known Limitations

OIDC JWT resource-server mode is now available, but Angular sign-in/client
configuration and tenant isolation are not yet implemented. The default local
profile is unauthenticated and must not be deployed.

High-confidence AI resolutions are proposals, not direct writes: an authorized
support operator approves or rejects the proposed resolution in the Agent Run
Explorer. Low-confidence and sensitive requests continue to escalate.

The Angular workspace includes a Vitest API-contract suite; run it with
`cd frontend && npm test`.

For a complete local container topology, run `docker compose up --build` from
the repository root. It starts MySQL, the Spring API, the Python runtime, and
the Angular SPA at `http://localhost:4200`. The Compose profile is intentionally
local-open (`SMARTHELP_OIDC_ENABLED=false`); do not use it for production.

A safe read-only k6 scenario and measurement runbook live in
[`load-tests/`](load-tests/); no performance figures are claimed until a run is
captured against a documented environment.

See [SECURITY.md](SECURITY.md) and the [threat model](docs/threat-model/README.md)
for the current security posture and known gaps.

1. **Docker optional** — Docker Compose is provided but Docker is not required. Local MySQL works.
2. **No resumable execution yet** — Streamed events are persisted and viewable in
   the Agent Run Explorer, but reconnect cursors and Python checkpoint/recovery
   are not implemented.
3. **Production identity integration pending** — Spring Security supports OIDC
   JWT resource-server mode, but an identity-provider configuration, Angular
   sign-in flow, and tenant isolation are still required before deployment.
4. **LLM optional** — Without `LLM_API_KEY`, the workflow runs in deterministic mode with keyword matching.

---

## Learning Roadmap

Read `docs/study/00-study-roadmap.md` for the recommended learning path through
all 23 study documents plus file-by-file source explanations.

Key concepts demonstrated:

| Concept | File |
| --- | --- |
| Spring MVC | `TicketController.java` |
| JdbcTemplate + RowMapper | `TicketRepository.java` |
| Dependency injection | `TicketService.java` |
| REST API design | `05-api-design.md` |
| LangGraph conditional edges | `graph.py` |
| SSE streaming | `AIController.java` + `workflow-graph.component.ts` |
| JUnit + Mockito | `TicketServiceTest.java` |
