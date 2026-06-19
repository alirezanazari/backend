# Database Backup and Restore Runbook

This runbook covers production PostgreSQL databases for `iam-service`,
`account-service`, `ledger-service`, `card-service`, `kyc-service`,
`feature-flag-service`, `transfer-service`, and `pfm-service`.

## Backup Policy

Minimum production policy:

- full logical backup for every service database at least daily,
- point-in-time recovery retained for at least seven days,
- encrypted backup storage outside the primary database host,
- backup integrity check after every scheduled backup,
- restore rehearsal before production launch and at least monthly.

Financial ledgers and transfer records require coordinated backup timestamps.
Record the backup start/end time for every service database in the release or
incident evidence.

## Pre-Deployment Backup

Before a production release that includes schema or data migration:

1. Announce migration window and freeze unrelated schema changes.
2. Confirm no backup or maintenance job is already running.
3. Capture backup IDs for every affected database.
4. Verify the backup completed successfully.
5. Verify PITR archive shipping is current.
6. Record migration version, Git SHA, image digest, and backup IDs.

## Restore Test

Restore tests must never target production databases.

1. Provision an isolated restore environment with no path back to production.
2. Restore the selected backup or PITR timestamp for every affected database.
3. Run Flyway validation against restored databases.
4. Start services with production-like configuration and isolated secrets.
5. Run smoke tests through the gateway.
6. Verify core invariants:
   - user identities and devices load,
   - account/profile state matches expected counts,
   - ledger balances equal entry sums,
   - card order and card status state is present,
   - transfer idempotency records and status history are present,
   - KYC/referral state is present,
   - feature flags and targeting rules are present.
7. Record restore duration, errors, row counts, invariant output, and owner.

## Emergency Restore

1. Declare incident severity and assign incident commander.
2. Stop writes or route traffic to maintenance mode when data corruption is
   suspected.
3. Select the restore timestamp with engineering, product, and incident owner.
4. Restore into a clean environment first and verify invariants.
5. Promote restored databases only after sign-off.
6. Keep original damaged databases read-only until the post-incident review is
   complete.

## Evidence

Capture:

- affected service/database names,
- backup IDs or PITR timestamp,
- restore environment ID,
- migration versions,
- invariant command output,
- elapsed backup and restore time,
- approving owner.
