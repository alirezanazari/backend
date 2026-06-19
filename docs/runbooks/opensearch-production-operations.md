# OpenSearch Production Operations Runbook

This runbook covers Phase 13.5 production OpenSearch operations. It assumes
the production cluster is separate from develop and is reachable only through
internal networking.

## Asset Locations

```text
deploy/observability/opensearch/production
docker/observability/opensearch/production
docker/observability/opensearch/scripts/bootstrap-production.sh
docker/observability/opensearch/scripts/validate-production-assets.sh
```

Production bootstrap is intentionally guarded. It refuses localhost and develop
Compose targets, requires explicit confirmation, and requires a production
snapshot repository before applying assets.

## Access Approval

Production access is approved per incident or maintenance window.

Required approval record:

- Requester, approver, and business reason.
- Target role: reader, support reader, observability admin, or cluster
  operator.
- Start and end time.
- Data scope and customer-impact justification.
- Ticket or incident ID.

Rules:

- Ingestion credentials are service credentials only and must not be used by
  humans.
- Support access uses `redbank_support_reader`, not broad production reader.
- Operator access is time-bounded and audited.
- Emergency access is reviewed after the incident.

## Production Bootstrap

Use only from deployment automation or an approved operator workstation inside
the production network.

Required environment:

```sh
REDBANK_PRODUCTION_OPENSEARCH_BOOTSTRAP=confirm-prod-opensearch-bootstrap
OPENSEARCH_URL=https://internal-prod-opensearch.example.com:9200
OPENSEARCH_USERNAME=replace-with-secret-manager-value
OPENSEARCH_PASSWORD=replace-with-secret-manager-value
REDBANK_SNAPSHOT_REPOSITORY=redbank-prod-observability-snapshots
REDBANK_SNAPSHOT_BUCKET=replace-with-prod-snapshot-bucket
REDBANK_SNAPSHOT_BASE_PATH=prod/opensearch
REDBANK_SNAPSHOT_REGION=replace-with-region
REDBANK_SNAPSHOT_ENDPOINT=https://replace-with-internal-s3-compatible-endpoint
```

Command:

```sh
sh docker/observability/opensearch/scripts/bootstrap-production.sh
```

The script applies production ISM policies, index templates, OpenSearch
Security roles, the snapshot repository, and production write aliases.

Role mappings are environment-specific. Use
`docker/observability/opensearch/production/security/role-mappings.example.json`
as the reviewed mapping contract, then apply mappings through the approved
identity provider or OpenSearch Security API.

## Incident Handling

1. Triage platform health alerts outside the affected OpenSearch cluster.
2. Check cluster health, node count, disk watermarks, JVM heap, thread pools,
   and shard allocation.
3. Check Data Prepper rejected records, queues, DLQ writes, and bulk response
   failures.
4. Check OpenTelemetry Collector exporter errors and retry queues.
5. Confirm whether ingestion is degraded, delayed, or stopped.
6. Preserve incident evidence before deleting indexes or changing retention.
7. Prefer scaling capacity or restoring failed nodes before changing templates.
8. Record every operator action in the incident ticket.

Do not copy production telemetry into develop. Restore production-like data only
into an isolated approved restore cluster.

## Snapshot Restore

Production restore test cadence:

- Before production readiness signoff.
- At least quarterly.
- Before every OpenSearch upgrade.
- After snapshot repository or credential changes.

Procedure:

1. Provision a clean compatible restore cluster with no route from production
   ingestion.
2. Register the same encrypted snapshot repository as read-only.
3. Restore selected telemetry indexes, Dashboards assets, Alerting monitors,
   and security assets needed for validation.
4. Compare source and restored document counts for selected indexes.
5. Open restored dashboards and run representative searches.
6. Verify restored monitor definitions exist but notification actions are not
   pointed at production channels unless explicitly testing them.
7. Record snapshot ID, restore cluster ID, counts, screenshots or query output,
   and final pass/fail.

Pass criteria:

- No failed shards.
- Required dashboards and monitors are present.
- Selected counts match expected source counts.
- Restore cluster remains isolated.

## Node Failure Test

Run on production-like infrastructure, not on the live production cluster unless
approved as a controlled resilience exercise.

1. Start steady telemetry load.
2. Remove one data-capable node.
3. Confirm ingestion continues and cluster does not become red.
4. Restore the node and confirm shard recovery.
5. Repeat for one cluster-manager-eligible node.
6. Confirm alerts fire and resolve.

Pass criteria:

- One-node failure does not stop ingestion.
- No permanent telemetry loss.
- Operators can identify degraded state from alerts and dashboards.

## Ingestion Backpressure Test

1. Run steady telemetry load.
2. Throttle OpenSearch bulk indexing or temporarily block Data Prepper egress.
3. Observe Data Prepper queues, retries, rejects, and DLQ behavior.
4. Restore OpenSearch throughput.
5. Confirm recovery without manual deletion.

Pass criteria:

- Backpressure is visible.
- Alerts fire.
- DLQ records are searchable.
- Pipeline recovers after the bottleneck is removed.

## Upgrade Rehearsal

1. Read OpenSearch, Dashboards, Data Prepper, collector, and Fluent Bit release
   notes.
2. Mirror the target image set into the internal registry.
3. Restore the latest production-like snapshot into a test cluster.
4. Upgrade one supported version step at a time.
5. Validate ingestion, templates, ISM, Dashboards, alerts, roles, and snapshots.
6. Take a fresh snapshot before production upgrade.

Rollback is based on snapshot restoration to a compatible cluster, not on an
unplanned binary downgrade.

## Recovery Artifacts

The following must be available from the internal registry before production
readiness is approved:

- OpenSearch image.
- OpenSearch Dashboards image.
- Data Prepper image.
- OpenTelemetry Collector image.
- Fluent Bit image.
- Bootstrap utility image.
- Exact deployment manifests and production asset files.
- Certificate chain and secret-manager recovery procedure.

See `deploy/observability/opensearch/production/image-mirror-manifest.yaml` for
the image list and required evidence.
