# Production Readiness

Phase 14 prepares `red-bank` for controlled production deployment. Production
approval requires evidence for every gate in this document.

## Release Gates

| Gate | Required evidence |
| --- | --- |
| Phase 13 observability | Dashboard import evidence, monitor dry-run evidence, and production OpenSearch readiness evidence. |
| Secrets | No production secret in Git, images, Postman collections, or profile config; secret manager/deployment references mapped per service. |
| CI | Successful Maven `verify` on the exact revision being promoted. |
| Images | Successful Docker build for every runtime service from the exact revision being promoted. |
| Vulnerability scanning | Dependency, filesystem, secret, misconfiguration, and image scans reviewed with no unaccepted critical/high findings. |
| Security hardening | Security checklist completed by engineering and release owner. |
| Rate limiting | API gateway rate limiting enabled and production thresholds approved. |
| Backups | Latest database backup exists for every production database and restore test evidence is current. |
| Disaster recovery | DR test evidence proves recovery time, data integrity checks, and owner escalation path. |
| Rollback | Application rollback and database migration rollback/mitigation plan approved before deploy. |
| SLO ownership | Service SLOs, dashboards, alerts, and on-call owner assigned. |

## Automation

CI lives in:

```text
.github/workflows/ci.yml
```

The workflow runs:

- Maven `verify`
- Trivy filesystem vulnerability, secret, and misconfiguration scan
- Docker image build for every runtime service
- Trivy image vulnerability scan for every runtime service

Runtime service images use:

```text
docker/app/Dockerfile
```

Build a local service image:

```bash
docker build --file docker/app/Dockerfile --build-arg SERVICE=api-gateway --tag redbank/api-gateway:local .
```

## Secret Management

Production secrets must be injected by the deployment platform or secret
manager. `application.yml` and `application-prod.yml` may contain only
environment-variable placeholders and safe defaults.

Before production approval:

1. Review `.env.prod.example` and map every `replace-with-secret-manager-value`
   to a secret-manager key.
2. Verify each runtime service can access only its own database credentials and
   required downstream credentials.
3. Rotate any secret exposed in local logs, terminal history, Postman exports,
   screenshots, or incident tickets.
4. Verify debug, mock OTP, and debug-token endpoints are unavailable in prod.
5. Verify CI and image scans do not report committed secret material.

## Gateway Rate Limiting

`api-gateway` enforces gateway API rate limits before authentication. The
limiter:

- skips non-gateway routes,
- keys bearer-token requests by token hash,
- keys unauthenticated/public requests by route and client address,
- returns `429` with `Retry-After` when exhausted.

Configuration:

| Variable | Default | Purpose |
| --- | --- | --- |
| `REDBANK_GATEWAY_RATE_LIMIT_ENABLED` | `true` | Enables gateway rate limiting. |
| `REDBANK_GATEWAY_RATE_LIMIT_CAPACITY` | `120` | Maximum burst tokens per bucket. |
| `REDBANK_GATEWAY_RATE_LIMIT_REFILL_TOKENS` | `120` | Tokens restored per refill window. |
| `REDBANK_GATEWAY_RATE_LIMIT_REFILL_PERIOD_SECONDS` | `60` | Refill window length. |
| `REDBANK_GATEWAY_RATE_LIMIT_MAX_BUCKETS` | `50000` | Memory guard for bucket cardinality. |

Production thresholds must be reviewed against login, OTP, transfer, and admin
traffic patterns before launch. Multi-instance gateway deployments should add a
shared limiter at the ingress or gateway infrastructure layer.

## Production Release Evidence

Store release evidence outside the repo in the approved operational record:

- Git SHA and image digests
- CI run URL
- scan run URLs and accepted-risk tickets
- migration review and rollback plan
- backup snapshot IDs
- restore or DR test transcript
- dashboard and monitor verification
- SLO/on-call owner approval

Related runbooks:

- [Database Backup and Restore](./runbooks/database-backup-restore.md)
- [Disaster Recovery](./runbooks/disaster-recovery.md)
- [Deployment Rollback and Migrations](./runbooks/deployment-rollback-and-migrations.md)
- [Service SLOs and On-Call Ownership](./runbooks/service-slos-oncall.md)
- [Production Security Checklist](./runbooks/production-security-checklist.md)
- [Observability Alert Runbook](./runbooks/observability-alerts.md)
- [OpenSearch Production Operations](./runbooks/opensearch-production-operations.md)
