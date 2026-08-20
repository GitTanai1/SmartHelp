# SmartHelp

An AI-powered customer support portal built as a teaching project. The finished
system demonstrates Spring MVC, JdbcTemplate, Angular, LangChain, LangGraph,
and Server-Sent Events working together end-to-end.

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

---

## Database Schema

Five tables:

```
users          → tickets (one user → many tickets)
categories     → tickets (one category → many tickets, category_id nullable)
categories     → knowledge_articles
tickets        → ticket_responses (ON DELETE CASCADE)
```

See `database/schema.sql` for the complete definition.

---

## REST API Overview

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Liveness + database status |
| POST/GET/PUT/DELETE | `/api/tickets` | Ticket CRUD |
| POST/GET | `/api/tickets/{id}/responses` | Ticket responses |
| POST | `/api/tickets/{id}/analyze` | Blocking AI analysis |
| GET | `/api/tickets/{id}/workflow` | SSE live workflow stream |
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
ESCALATE       RESOLVE
```

Three test scenarios:
1. Billing ticket with matching articles → **RESOLVED**
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

# 2. Load schema and seed data (in a new terminal)
mysql -h 127.0.0.1 -P 3306 -u root < database/dev-user.sql
mysql -h 127.0.0.1 -P 3306 -u root < database/schema.sql
mysql -h 127.0.0.1 -P 3306 -u root < database/seed.sql
```

### Option B: Docker Compose (requires Docker)

```powershell
docker compose up -d mysql
mysql -h 127.0.0.1 -P 3306 -u root -psmarthelp-root < database/schema.sql
mysql -h 127.0.0.1 -P 3306 -u root -psmarthelp-root < database/seed.sql
```

---

## Backend Startup

```powershell
$env:JAVA_HOME='C:\path\to\jdk21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd backend
.\mvnw.cmd spring-boot:run
```

Verify: `Invoke-RestMethod http://localhost:8080/api/health`

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
Invoke-RestMethod http://localhost:8080/api/health

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
data: {"ticketId":1,"node":"RESOLVE","status":"COMPLETED","state":{"finalStatus":"RESOLVED",...}}
```

---

## Known Limitations

1. **Docker optional** — Docker Compose is provided but Docker is not required. Local MySQL works.
2. **SSE not replayed** — Workflow events are live-only. After a browser refresh, per-node history is gone.
3. **No authentication** — User role is in the schema but Spring Security / JWT are out of scope.
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
