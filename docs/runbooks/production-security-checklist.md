# Production Security Checklist

Complete this checklist before the first production deployment and before any
release that changes auth, gateway behavior, secrets, payments, ledger, cards,
or operational access.

## Configuration

- `SPRING_PROFILES_ACTIVE=prod` for every runtime service.
- Debug endpoints disabled.
- Mock OTP disabled.
- Production JWT issuer configured.
- Production signing/verifying keys injected from secret manager.
- Internal service token injected from secret manager.
- Database passwords injected from secret manager.
- External provider credentials injected from secret manager.
- ECS JSON logs enabled.
- Sensitive field redaction verified.

## Gateway

- HTTPS/TLS terminates at approved infrastructure.
- App-level encryption enabled when required by mobile release policy.
- Gateway request body size limit approved.
- Rate limiting enabled.
- Public endpoints reviewed.
- Admin endpoints require admin/system token.
- Untrusted identity/internal headers are stripped before forwarding.

## Services

- Startup safety guards enabled.
- Actuator exposure limited to approved endpoints.
- Flyway production execution mode approved.
- Internal APIs not reachable publicly.
- Idempotency required for financial writes.
- Ledger and transfer consistency checks ready.
- Audit/event records retained according to policy.

## CI And Supply Chain

- Maven verification passed.
- Dependency and filesystem scans reviewed.
- Container image scans reviewed.
- Image digests recorded.
- Base image versions reviewed.
- No committed production secrets found.

## Operational Access

- Least-privilege database roles assigned.
- OpenSearch roles assigned through production security bootstrap.
- Break-glass access owner and expiry defined.
- Support/read-only access separated from admin/operator access.
- Access approval evidence stored outside the repo.

## Approval

Record:

- release owner,
- security reviewer,
- affected services,
- accepted risks and expiry,
- evidence links,
- deployment window.
