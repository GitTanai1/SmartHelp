# SmartHelp

SmartHelp is an AI-powered customer support portal built as a learning project. Phase 1 contains only the runnable project skeleton: Angular frontend, Spring Boot backend, FastAPI AI service, and a MySQL development service.

AI functionality is not implemented yet. LangChain and LangGraph are part of the planned architecture and will be introduced in later phases.

## Architecture

```text
Angular
  -> Spring Boot
  -> MySQL

Spring Boot
  -> FastAPI
  -> LangChain / LangGraph
```

In Phase 1, only health checks and the frontend shell exist. CRUD, JDBC repositories, database schema, LangChain, LangGraph, SSE, and workflow visualization are intentionally not implemented yet.

## Prerequisites

- Java 21
- Maven, or use the included Maven Wrapper in `backend/`
- Node.js compatible with Angular 20
- npm
- Python 3.x
- Docker with Docker Compose for MySQL

## Running The Project

Start MySQL:

```powershell
docker compose up -d mysql
```

Start Spring Boot:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Start FastAPI:

```powershell
cd ai-service
python -m pip install -r requirements.txt
python -m uvicorn main:app --reload --port 8000
```

Start Angular:

```powershell
cd frontend
npm install
npm start
```

## Health Checks

Backend:

```text
GET http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "smarthelp-backend"
}
```

AI service:

```text
GET http://localhost:8000/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "smarthelp-ai"
}
```

## Configuration

Copy `.env.example` to `.env` if you want Docker Compose to use custom values.

Spring Boot reads database and AI service values from environment variables:

- `SERVER_PORT`
- `SMARTHELP_DB_URL`
- `SMARTHELP_DB_USERNAME`
- `SMARTHELP_DB_PASSWORD`
- `SMARTHELP_AI_BASE_URL`

FastAPI reads:

- `SPRING_BOOT_BASE_URL`
- `LLM_API_KEY`
- `LLM_BASE_URL`
- `LLM_MODEL`

Do not commit real secrets.
