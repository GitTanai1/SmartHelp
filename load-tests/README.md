# SmartHelp Load Tests

`tickets-smoke.js` is a safe-by-default k6 read workload for the readiness and
ticket-list endpoints. It does not create data or start agent runs.

Run against an approved environment with k6 installed:

```bash
k6 run -e SMARTHELP_BASE_URL=http://localhost:8080/api/v1 load-tests/tickets-smoke.js
```

For OIDC mode, supply a short-lived test bearer token through the environment:

```bash
k6 run -e SMARTHELP_BASE_URL=https://api.example.com/api/v1 \
  -e SMARTHELP_BEARER_TOKEN="$SMARTHELP_BEARER_TOKEN" load-tests/tickets-smoke.js
```

Set `SMARTHELP_LOAD_VUS` to change the default five virtual users. Capture the
k6 output with the tested commit, environment, database size, and configuration
before making any performance claim. Agent-write workloads require isolated
fixtures and an explicit cleanup plan, so they are intentionally not automated
by this starter scenario.
