# Security Policy

## Supported security posture

The default local profile is intentionally unauthenticated for development only.
It must not be deployed. Production deployments must enable OIDC with
`SMARTHELP_OIDC_ENABLED=true` and configure `SMARTHELP_OIDC_ISSUER_URI`.

OIDC mode validates JWTs in Spring Boot, maps the verified `email` claim to a
SmartHelp database user, and applies server-side customer ownership and agent
role checks. The same bearer token is forwarded to Python only for calls back
to Java; Python is not an authorization authority.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Contact the project
maintainer privately with reproduction steps, affected component, and impact.
Do not include credentials, access tokens, or customer data in reports.

## Current limitations

- Multi-tenant isolation is not implemented.
- The Angular OIDC sign-in/client flow is not yet implemented.
- Run-event payloads are restricted to agent operators, but may contain ticket
  content and should be treated as sensitive operational data.
- Local Docker and external database credentials in example files are samples,
  not production secrets.

## Automated checks

Pull requests and pushes run CodeQL, dependency update automation, and a Gitleaks
history scan. These controls reduce exposure but do not replace review: never
commit credentials, bearer tokens, database dumps, or production prompt data.
