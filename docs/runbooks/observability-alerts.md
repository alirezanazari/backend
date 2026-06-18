# Observability Alert Runbook

Phase 13.4 alert definitions live under:

```text
docker/observability/opensearch/alerting/monitors
docker/observability/opensearch/alerting/platform-probes
docker/observability/opensearch/scripts/verify-alerts-develop.sh
```

OpenSearch Alerting monitors have empty actions in local/develop. Shared
develop and production must attach approved notification destinations during
promotion.

## Recreate Develop Alerts

```sh
docker compose --env-file .env.develop -f docker-compose.develop.yml --profile observability up -d
docker compose --env-file .env.develop -f docker-compose.develop.yml --profile observability run --rm --entrypoint sh opensearch-bootstrap /observability/opensearch/scripts/verify-alerts-develop.sh
```

The verification script injects synthetic failure documents, executes each
monitor with `dryrun=true`, verifies that each monitor triggers, and removes the
synthetic documents.

## HTTP 5xx

- Definition: `redbank-http-5xx-develop.json`
- Signal: `logs-redbank.*-develop-*`, `http.response.status_code >= 500`
- First checks: identify `service.name`, `http.route`, recent deploys, and
  matching `trace.id` or `correlation.id`.
- Controlled failure: synthetic HTTP 500 log in
  `verify-alerts-develop.sh`.

## Route Latency

- Definition: `redbank-route-latency-develop.json`
- Signal: p95 of `event.duration` over five minutes.
- Threshold: `1_000_000_000` ns in develop.
- First checks: split by route, compare downstream spans, inspect database pool
  metrics, then check recent release/config changes.

## Database Pool Exhaustion

- Definition: `redbank-db-pool-exhaustion-develop.json`
- Signal: `hikaricp.connections.pending > 0`.
- First checks: active/max/pending connection metrics, slow queries, connection
  leaks, database health, and application thread pools.

## Outbox Backlog

- Definition: `redbank-outbox-backlog-develop.json`
- Signals: `redbank.outbox.backlog`, `redbank.outbox.oldest.pending.seconds`,
  and `redbank.outbox.deadletter.count`.
- First checks: relay process health, database locks, downstream availability,
  retry volume, and idempotency keys before replaying anything.

## Ingestion Failure

- Definition: `redbank-ingestion-failure-develop.json`
- Signal: documents in `deadletter-redbank.*`.
- First checks: Data Prepper logs, dead-letter payload shape, index template
  mismatches, OpenSearch bulk response errors, and parser failures.

## Collector Or Data Prepper Delivery Failure

- Definition: `platform-probes/redbank-develop-platform-probes.json`
- Signals: Data Prepper Prometheus metrics and collector health endpoint.
- First checks: `http://localhost:4900/metrics/prometheus`,
  `http://localhost:13133/`, collector exporter errors, and Data Prepper bulk
  request failures.

## Authentication Anomaly

- Definition: `redbank-auth-anomaly-develop.json`
- Signals: IAM auth failures, OTP throttles, account lockouts, and IAM error
  codes.
- First checks: rate limiting, source IP/device patterns, OTP provider health,
  and whether the spike matches a test or release window.

## Ledger Or Transfer Failure Spike

- Definition: `redbank-ledger-transfer-failure-develop.json`
- Signals: transfer/ledger `error.code` and HTTP failures.
- First checks: safe reason code distribution, idempotency state, ledger
  consistency checks, and downstream balance/hold services.

## DEBUG Logs

- Definition: `redbank-debug-log-develop.json`
- Signal: `log.level: DEBUG` in application log indexes.
- First checks: runtime logging overrides, profile/env mixups, and whether
  sensitive values were emitted.

## OpenSearch Cluster Health

- Definition: `platform-probes/redbank-develop-platform-probes.json`
- Probe: `GET /_cluster/health`
- Warning: yellow. Critical: red.
- First checks: unassigned shards, node health, disk pressure, JVM heap, and
  recent index/template changes.

## OpenSearch Disk Watermark

- Definition: `platform-probes/redbank-develop-platform-probes.json`
- Probe: `GET /_cat/allocation?format=json&bytes=b`
- Warning: `disk.percent >= 85`. Critical: `disk.percent >= 90`.
- First checks: retention policies, shard growth, dead-letter volume, snapshot
  status, and host/container disk capacity.

## Failed Or Stale Snapshots

- Definition: `platform-probes/redbank-develop-platform-probes.json`
- Probe: `GET /_snapshot/_all/*?ignore_unavailable=true`
- Critical: failed/partial snapshot or no successful snapshot in the required
  window.
- Develop default: definition-only until a develop snapshot repository is
  configured.
- First checks: repository credentials, network access, repository capacity,
  snapshot policy history, and restore verification evidence.
