# Deployment Rollback and Migration Runbook

Production releases must be reversible at the application layer and explicit
about database migration risk.

## Pre-Deploy Checklist

1. CI passed for the exact Git SHA.
2. Images built and scanned for every service.
3. Image digests recorded.
4. Migration diff reviewed by service owner and database owner.
5. Backup IDs recorded for affected databases.
6. Rollback owner and decision deadline assigned.
7. Smoke tests and SLO dashboards ready.

## Application Rollback

Use application rollback when the new image is faulty and the database schema
remains compatible with the previous image.

1. Stop rollout or disable traffic to the faulty version.
2. Redeploy previous known-good image digest.
3. Keep the same production secrets and runtime config unless the incident is
   config-related.
4. Verify `/actuator/health` for every affected service.
5. Run gateway smoke tests.
6. Confirm alerts and SLO dashboards recover.
7. Keep incident notes with image digests and timestamps.

## Database Migration Rules

Production migrations are forward-only. Avoid destructive changes in the same
release that introduces new code.

Preferred pattern:

1. Expand schema with nullable columns or new tables.
2. Deploy code that writes both old and new shapes when needed.
3. Backfill through a controlled job.
4. Switch reads to the new shape.
5. Remove old shape in a later release after backup and rollback windows pass.

Dangerous migrations require a mitigation plan:

- table rewrite,
- data deletion,
- column type change,
- unique constraint on existing data,
- non-null change,
- index build that can lock writes,
- ledger, transfer, card, or identity state rewrite.

## Migration Failure

1. Stop the rollout.
2. Preserve migration logs.
3. Identify the last successful Flyway version.
4. If no data was changed, repair metadata only after owner approval.
5. If data changed, restore to isolated environment and compare with prod.
6. Decide whether to fix forward or restore from backup.
7. Record decision, approver, backup IDs, and resulting schema version.

## Post-Deploy Watch

For at least one SLO window after deploy:

- HTTP 5xx rate,
- p95 latency,
- database pool pending connections,
- outbox backlog,
- auth anomaly alerts,
- ledger/transfer failure alerts,
- OpenSearch ingestion health.
