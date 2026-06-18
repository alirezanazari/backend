#!/bin/sh
set -eu

: "${OPENSEARCH_URL:=https://opensearch:9200}"
: "${OPENSEARCH_USERNAME:=admin}"
: "${OPENSEARCH_PASSWORD:?OPENSEARCH_PASSWORD is required}"

PHASE_TAG=13.4-failure-injection

curl_os() {
  method=$1
  path=$2
  body=${3:-}

  if [ -n "$body" ]; then
    curl -fsSk \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 1 \
      -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
      -H "Content-Type: application/json" \
      -X "$method" \
      --data-binary "@$body" \
      "$OPENSEARCH_URL$path"
  else
    curl -fsSk \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 1 \
      -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
      -X "$method" \
      "$OPENSEARCH_URL$path"
  fi
}

find_monitor_id() {
  name=$1
  search_body=/tmp/redbank-monitor-search.json
  search_response=/tmp/redbank-monitor-search-response.json
  monitor_response=/tmp/redbank-monitor-get-response.json

  cat > "$search_body" <<EOF
{
  "query": {
    "match": {
      "monitor.name": "$name"
    }
  },
  "size": 50
}
EOF

  curl_os GET "/_plugins/_alerting/monitors/_search" "$search_body" > "$search_response"
  sed 's/"_id":"/\
"_id":"/g' "$search_response" \
    | sed -n 's/^"_id":"\([^"]*\)".*/\1/p' \
    | while read -r candidate_id; do
        [ -n "$candidate_id" ] || continue
        if curl_os GET "/_plugins/_alerting/monitors/$candidate_id" > "$monitor_response" 2>/dev/null; then
          candidate_name=$(sed -n 's/.*"monitor":{[^}]*"name":"\([^"]*\)".*/\1/p' "$monitor_response")
          if [ "$candidate_name" = "$name" ]; then
            echo "$candidate_id"
            break
          fi
        fi
      done
  rm -f "$search_body" "$search_response" "$monitor_response"
}

delete_synthetic_documents() {
  delete_body=/tmp/redbank-alert-cleanup.json
  cat > "$delete_body" <<EOF
{
  "query": {
    "term": {
      "redbank.phase": "$PHASE_TAG"
    }
  }
}
EOF

  curl_os POST "/logs-redbank.*-develop-*/_delete_by_query?conflicts=proceed&refresh=true" "$delete_body" >/dev/null 2>&1 || true
  curl_os POST "/metrics-redbank.develop-*/_delete_by_query?conflicts=proceed&refresh=true" "$delete_body" >/dev/null 2>&1 || true
  curl_os POST "/deadletter-redbank.*/_delete_by_query?conflicts=proceed&refresh=true" "$delete_body" >/dev/null 2>&1 || true
  rm -f "$delete_body"
}

inject_synthetic_documents() {
  now=$(date -u +"%Y-%m-%dT%H:%M:%S.000Z")
  bulk=/tmp/redbank-alert-failure-injection.ndjson

  : > "$bulk"

  cat >> "$bulk" <<EOF
{"index":{"_index":"logs-redbank.api-gateway-develop"}}
{"@timestamp":"$now","message":"phase 13.4 synthetic HTTP 500 latency failure","log":{"level":"ERROR"},"service":{"name":"api-gateway","environment":"develop"},"event":{"duration":2000000000},"http":{"route":"/phase-13-4/failure","response":{"status_code":500},"request":{"method":"GET"}},"error":{"code":"PHASE_13_4_SYNTHETIC"},"redbank":{"phase":"$PHASE_TAG"}}
{"index":{"_index":"logs-redbank.api-gateway-develop"}}
{"@timestamp":"$now","message":"phase 13.4 synthetic DEBUG log","log":{"level":"DEBUG"},"service":{"name":"api-gateway","environment":"develop"},"redbank":{"phase":"$PHASE_TAG"}}
EOF

  i=0
  while [ "$i" -lt 10 ]; do
    cat >> "$bulk" <<EOF
{"index":{"_index":"logs-redbank.iam-service-develop"}}
{"@timestamp":"$now","message":"phase 13.4 synthetic auth failure","log":{"level":"WARN"},"service":{"name":"iam-service","environment":"develop"},"event":{"action":"auth.login.failed"},"error":{"code":"AUTH_INVALID_CREDENTIALS"},"redbank":{"phase":"$PHASE_TAG"}}
EOF
    i=$((i + 1))
  done

  i=0
  while [ "$i" -lt 5 ]; do
    cat >> "$bulk" <<EOF
{"index":{"_index":"logs-redbank.transfer-service-develop"}}
{"@timestamp":"$now","message":"phase 13.4 synthetic transfer failure","log":{"level":"ERROR"},"service":{"name":"transfer-service","environment":"develop"},"http":{"route":"/v1/transfers","response":{"status_code":409}},"error":{"code":"LEDGER_INSUFFICIENT_FUNDS"},"redbank":{"phase":"$PHASE_TAG"}}
EOF
    i=$((i + 1))
  done

  cat >> "$bulk" <<EOF
{"index":{"_index":"metrics-redbank.develop"}}
{"@timestamp":"$now","name":"hikaricp.connections.pending","service":{"name":"account-service"},"deployment":{"environment":"develop"},"value":1,"redbank":{"phase":"$PHASE_TAG"}}
{"index":{"_index":"metrics-redbank.develop"}}
{"@timestamp":"$now","name":"redbank.outbox.backlog","service":{"name":"ledger-service"},"deployment":{"environment":"develop"},"value":101,"redbank":{"phase":"$PHASE_TAG"}}
{"index":{"_index":"metrics-redbank.develop"}}
{"@timestamp":"$now","name":"redbank.outbox.oldest.pending.seconds","service":{"name":"ledger-service"},"deployment":{"environment":"develop"},"value":301,"redbank":{"phase":"$PHASE_TAG"}}
{"index":{"_index":"deadletter-redbank.develop"}}
{"@timestamp":"$now","message":"phase 13.4 synthetic dead letter","service":{"name":"data-prepper","environment":"develop"},"redbank":{"ingestion_pipeline":"logs-redbank-develop","phase":"$PHASE_TAG"}}
EOF

  curl_os POST "/_bulk?refresh=true" "$bulk" >/dev/null
  rm -f "$bulk"
}

assert_monitor_triggers() {
  name=$1
  monitor_id=$(find_monitor_id "$name")

  if [ -z "$monitor_id" ]; then
    echo "Monitor not found: $name" >&2
    exit 1
  fi

  response=/tmp/redbank-monitor-execute-response.json
  curl_os POST "/_plugins/_alerting/monitors/$monitor_id/_execute?dryrun=true" > "$response"

  if ! grep -q '"triggered":true' "$response"; then
    echo "Monitor did not trigger during synthetic failure injection: $name" >&2
    cat "$response" >&2
    exit 1
  fi

  rm -f "$response"
  echo "Verified alert monitor: $name"
}

trap delete_synthetic_documents EXIT

delete_synthetic_documents
inject_synthetic_documents

for monitor in \
  redbank-http-5xx-develop \
  redbank-route-latency-develop \
  redbank-db-pool-exhaustion-develop \
  redbank-outbox-backlog-develop \
  redbank-ingestion-failure-develop \
  redbank-debug-log-develop \
  redbank-auth-anomaly-develop \
  redbank-ledger-transfer-failure-develop
do
  assert_monitor_triggers "$monitor"
done

curl_os GET "/_cluster/health" >/dev/null
curl_os GET "/_cat/allocation?format=json&bytes=b" >/dev/null

echo "Phase 13.4 alert failure injection checks passed."
