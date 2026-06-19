# Disaster Recovery Runbook

Disaster recovery covers loss of application runtime, database availability,
observability availability, or a deployment region.

## Recovery Objectives

Initial production targets:

| System | RTO | RPO |
| --- | --- | --- |
| API gateway and stateless services | 60 minutes | 0 minutes after image promotion |
| PostgreSQL service databases | 4 hours | 15 minutes |
| Observability platform | 8 hours | 1 hour |
| Deployment metadata and runbooks | 4 hours | 0 minutes |

RTO/RPO targets must be revisited after the first production load test and each
major architecture change.

## DR Prerequisites

- Immutable image digests are stored for the promoted release.
- Production secrets can be restored from secret manager by an approved owner.
- Database backups and PITR archives are encrypted and available outside the
  failed host.
- OpenSearch snapshots are available outside the failed cluster.
- DNS, TLS certificates, and internal network policy can be recreated.
- On-call owner can reach database, infrastructure, and security approvers.

## DR Test

Run at least one production-like DR test before launch:

1. Pick a release SHA and backup timestamp.
2. Provision clean application, database, and observability infrastructure.
3. Restore secrets through the approved path.
4. Restore databases and run invariant checks.
5. Restore OpenSearch snapshots into an isolated cluster.
6. Deploy the same promoted images by digest.
7. Start services with `SPRING_PROFILES_ACTIVE=prod`.
8. Verify gateway smoke tests, health probes, logs, metrics, and alerts.
9. Record actual RTO/RPO and all manual steps.

## Incident Flow

1. Declare incident severity.
2. Assign incident commander and service owners.
3. Decide whether to fail forward, roll back, or restore.
4. Freeze non-incident production changes.
5. Preserve logs, backup IDs, image digests, and deployment state.
6. Execute the selected recovery path.
7. Verify customer-facing and ledger/transfer invariants.
8. Communicate status and recovery completion.
9. Open post-incident review with action items.

## Recovery Paths

Application-only failure:

- redeploy the last known-good image digest,
- verify gateway and service health,
- verify alerts return to normal.

Database failure:

- stop writes,
- restore to isolated environment,
- validate invariants,
- promote restored database after approval.

Observability failure:

- keep application runtime isolated from observability outage,
- restore OpenSearch from snapshot,
- verify ingestion and alerting after recovery.

Region or host failure:

- rebuild infrastructure from approved manifests,
- restore secrets, databases, and observability,
- promote DNS or gateway routing after validation.
