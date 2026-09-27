# Data Model and Mutation Guarantees

MySQL remains the current relational store. Flyway migrations in
`backend/src/main/resources/db/migration` are the production schema authority;
legacy schema and seed scripts are local-development artifacts.

Key operational tables:

| Table | Purpose |
| --- | --- |
| `tickets`, `responses`, `users`, `knowledge_articles` | Core support domain |
| `agent_action_executions` | Idempotency record for side-effecting agent writes |
| `audit_events` | Append-only application audit trail |
| `agent_runs`, `agent_run_events` | Java-owned workflow history for streamed executions; each run stores the originating request ID for incident correlation |

Resolution and escalation are Java application-service transactions. A successful
operation writes the response/status change, idempotency state, and audit event
as one unit of work. Replaying an identical idempotency key returns the stored
result; reusing it for a different action is rejected.

Flyway V6 adds composite indexes for the actual JDBC read paths: customer/status
ticket lists ordered by recency, ticket responses ordered chronologically,
category-filtered knowledge ordered by update time, and run approval history.
They are query-pattern indexes, not a claim of measured production tuning;
capture `EXPLAIN` output and workload results before adding broader indexes.

Flyway V7 adds a nullable, indexed `agent_runs.request_id`. It is correlation
metadata rather than an authorization input, and remains nullable to preserve
historical runs created before request-ID propagation existed.

The current schema is user-scoped, not tenant-scoped. Adding tenants requires a
deliberate data migration and authorization review before multi-tenant claims
can be made.
