# Service SLOs and On-Call Ownership

SLOs define launch expectations for production operations. They are initial
targets and must be recalibrated with production traffic.

## Ownership

| Service | Primary owner | Escalation |
| --- | --- | --- |
| api-gateway | Platform/backend on-call | Security owner |
| iam-service | Identity on-call | Security owner |
| account-service | Accounts on-call | Ledger owner |
| ledger-service | Ledger on-call | Incident commander |
| card-service | Cards on-call | Accounts owner |
| kyc-service | KYC on-call | Compliance owner |
| feature-flag-service | Platform/backend on-call | Release owner |
| transfer-service | Payments on-call | Ledger owner |
| pfm-service | PFM on-call | Accounts owner |
| OpenSearch observability | Platform/on-call | Infrastructure owner |

Replace role names with named people or rotation aliases before production
launch.

## Initial SLOs

| Service group | Availability | Latency | Error budget signal |
| --- | --- | --- | --- |
| api-gateway | 99.9% monthly | p95 under 500 ms | 5xx and gateway timeout rate |
| identity/auth | 99.9% monthly | p95 under 800 ms | login/register/device verification failure rate |
| account/card/KYC/PFM | 99.5% monthly | p95 under 1000 ms | 5xx and business-critical route failures |
| ledger/transfer | 99.9% monthly | p95 under 1000 ms | failed posting/transfer and reconciliation alerts |
| observability ingestion | 99.5% monthly | logs visible within 5 minutes | ingestion dead-letter and collector/exporter failures |

## Alert Ownership

Every alert must have:

- owning rotation,
- severity,
- first-response target,
- runbook link,
- dashboard link,
- escalation path.

Minimum production alerts:

- HTTP 5xx spike,
- route p95 latency breach,
- database pool exhaustion,
- outbox backlog or dead-letter growth,
- auth anomaly spike,
- ledger/transfer failure spike,
- OpenSearch cluster red/yellow,
- disk watermark breach,
- failed or stale snapshots,
- ingestion dead-letter growth.

## Review Cadence

- Weekly during controlled launch.
- Monthly after stable production traffic.
- After every incident or significant release.
