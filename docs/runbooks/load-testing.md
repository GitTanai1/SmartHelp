# Load-Test Runbook

## Purpose

Measure the read path without mutating support data. The checked-in k6 scenario
is a reproducible baseline, not evidence of a particular production capacity.

## Preconditions

- Use a disposable/local environment or written approval for the target.
- Record commit SHA, Java/Python versions, database size, VU count, and whether
  OIDC was enabled.
- In OIDC mode, export a short-lived least-privilege test token; never put it in
  the script, repository, or test output.

## Run

```bash
k6 run -e SMARTHELP_BASE_URL=http://localhost:8080/api/v1 load-tests/tickets-smoke.js
```

## Record

Save raw k6 output outside the repository's source tree or in an intentionally
reviewed benchmark report. Record p50/p95/p99, request rate, error rate, JVM
and MySQL resource usage, and any agent metric values. Do not extrapolate these
results to agent execution, which has a different latency and cost profile.
