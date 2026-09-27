# ADR-001: Modular monolith with a Python agent boundary

Status: Accepted

SmartHelp keeps ticket, knowledge, response, authorization, and agent-action
business ownership in one Spring Boot application. The Python service remains
an independent boundary because LangGraph/LLM dependencies and execution
characteristics differ from Java's transactional domain API.

Python may retrieve evidence and propose bounded actions. Java validates JWTs,
owns transactions, idempotency, audit records, and ticket mutations. This
avoids premature microservices while preserving a clear AI integration boundary.

Consequences: Java-to-Python calls carry correlation and bearer-token context;
Python tool calls return to Java rather than accessing MySQL directly.
