# Contributing

Keep changes small and independently verifiable. Do not commit `.env` files,
access tokens, real customer data, or generated build outputs.

Before opening a pull request, run the checks that apply:

```powershell
cd backend; .\mvnw.cmd test
cd ..\ai-service; python test_workflow.py; python ..\evals\run_deterministic.py
cd ..\frontend; npm ci; npm run build
```

The repository CI uses Java 21. If local Java differs, run Maven validation and
let the Java 21 CI job provide the authoritative test result.

New agent write actions must be Java-owned, transactional, idempotent, audited,
and protected by server-side authorization before being exposed to Python.
