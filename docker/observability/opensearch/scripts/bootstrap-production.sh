#!/bin/sh
set -eu

: "${REDBANK_PRODUCTION_OPENSEARCH_BOOTSTRAP:?Set REDBANK_PRODUCTION_OPENSEARCH_BOOTSTRAP=confirm-prod-opensearch-bootstrap}"
: "${OPENSEARCH_URL:?OPENSEARCH_URL is required}"
: "${OPENSEARCH_USERNAME:?OPENSEARCH_USERNAME is required}"
: "${OPENSEARCH_PASSWORD:?OPENSEARCH_PASSWORD is required}"
: "${REDBANK_SNAPSHOT_REPOSITORY:=redbank-prod-observability-snapshots}"
: "${REDBANK_SNAPSHOT_BUCKET:?REDBANK_SNAPSHOT_BUCKET is required}"
: "${REDBANK_SNAPSHOT_BASE_PATH:=prod/opensearch}"
: "${REDBANK_SNAPSHOT_REGION:?REDBANK_SNAPSHOT_REGION is required}"
: "${REDBANK_SNAPSHOT_ENDPOINT:?REDBANK_SNAPSHOT_ENDPOINT is required}"

if [ "$REDBANK_PRODUCTION_OPENSEARCH_BOOTSTRAP" != "confirm-prod-opensearch-bootstrap" ]; then
  echo "Refusing production bootstrap without explicit confirmation." >&2
  exit 1
fi

case "$OPENSEARCH_URL" in
  http://*|*localhost*|*127.0.0.1*|*0.0.0.0*|*opensearch:9200*)
    echo "Refusing production bootstrap against non-production-looking OpenSearch URL: $OPENSEARCH_URL" >&2
    exit 1
    ;;
esac

ROOT=/observability/opensearch
PROD_ROOT="$ROOT/production"

curl_os() {
  method=$1
  path=$2
  body=${3:-}

  if [ -n "$body" ]; then
    curl -fsS \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
      -H "Content-Type: application/json" \
      -X "$method" \
      --data-binary "@$body" \
      "$OPENSEARCH_URL$path"
  else
    curl -fsS \
      --retry 5 \
      --retry-all-errors \
      --retry-delay 2 \
      -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
      -X "$method" \
      "$OPENSEARCH_URL$path"
  fi
}

escape_sed() {
  printf '%s' "$1" | sed 's/[&|\\]/\\&/g'
}

wait_for_opensearch() {
  tries=0
  until curl_os GET "/_cluster/health?wait_for_status=yellow&timeout=10s" >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -ge 60 ]; then
      echo "Production OpenSearch did not become healthy in time" >&2
      exit 1
    fi
    sleep 5
  done
}

put_assets() {
  curl_os PUT "/_plugins/_ism/policies/redbank-prod-logs-policy" \
    "$PROD_ROOT/ism-policies/redbank-prod-logs-policy.json" >/dev/null
  curl_os PUT "/_plugins/_ism/policies/redbank-prod-deadletter-policy" \
    "$PROD_ROOT/ism-policies/redbank-prod-deadletter-policy.json" >/dev/null
  curl_os PUT "/_plugins/_ism/policies/redbank-prod-traces-policy" \
    "$PROD_ROOT/ism-policies/redbank-prod-traces-policy.json" >/dev/null
  curl_os PUT "/_plugins/_ism/policies/redbank-prod-metrics-policy" \
    "$PROD_ROOT/ism-policies/redbank-prod-metrics-policy.json" >/dev/null

  curl_os PUT "/_index_template/logs-redbank-prod-template" \
    "$PROD_ROOT/index-templates/logs-redbank-prod-template.json" >/dev/null
  curl_os PUT "/_index_template/deadletter-redbank-prod-template" \
    "$PROD_ROOT/index-templates/deadletter-redbank-prod-template.json" >/dev/null
  curl_os PUT "/_index_template/otel-traces-redbank-prod-template" \
    "$PROD_ROOT/index-templates/otel-traces-redbank-prod-template.json" >/dev/null
  curl_os PUT "/_index_template/otel-service-map-redbank-prod-template" \
    "$PROD_ROOT/index-templates/otel-service-map-redbank-prod-template.json" >/dev/null
  curl_os PUT "/_index_template/metrics-redbank-prod-template" \
    "$PROD_ROOT/index-templates/metrics-redbank-prod-template.json" >/dev/null
}

put_roles() {
  for role_file in "$PROD_ROOT"/security/roles/*.json; do
    role_name=$(basename "$role_file" .json)
    curl_os PUT "/_plugins/_security/api/roles/$role_name" "$role_file" >/dev/null
    echo "Applied production role: $role_name"
  done
}

put_snapshot_repository() {
  rendered=/tmp/redbank-prod-snapshot-repository.json
  sed \
    -e "s|__REDBANK_SNAPSHOT_BUCKET__|$(escape_sed "$REDBANK_SNAPSHOT_BUCKET")|g" \
    -e "s|__REDBANK_SNAPSHOT_BASE_PATH__|$(escape_sed "$REDBANK_SNAPSHOT_BASE_PATH")|g" \
    -e "s|__REDBANK_SNAPSHOT_REGION__|$(escape_sed "$REDBANK_SNAPSHOT_REGION")|g" \
    -e "s|__REDBANK_SNAPSHOT_ENDPOINT__|$(escape_sed "$REDBANK_SNAPSHOT_ENDPOINT")|g" \
    "$PROD_ROOT/snapshot-repositories/redbank-prod-s3-repository.template.json" \
    > "$rendered"

  curl_os PUT "/_snapshot/$REDBANK_SNAPSHOT_REPOSITORY?verify=true" "$rendered" >/dev/null
  rm -f "$rendered"
  echo "Registered production snapshot repository: $REDBANK_SNAPSHOT_REPOSITORY"
}

index_exists() {
  curl -fsS \
    -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
    -I "$OPENSEARCH_URL/$1" >/dev/null 2>&1
}

create_write_alias() {
  alias_name=$1
  index_name="${alias_name}-000001"

  if index_exists "$index_name"; then
    return
  fi

  tmp_file="/tmp/${index_name}.json"
  cat > "$tmp_file" <<EOF
{
  "settings": {
    "plugins.index_state_management.rollover_alias": "$alias_name"
  },
  "aliases": {
    "$alias_name": {
      "is_write_index": true
    }
  }
}
EOF
  curl_os PUT "/$index_name" "$tmp_file" >/dev/null
  rm -f "$tmp_file"
}

wait_for_opensearch
put_assets
put_roles
put_snapshot_repository

for service in \
  api-gateway \
  iam-service \
  account-service \
  ledger-service \
  card-service \
  kyc-service \
  feature-flag-service \
  transfer-service \
  pfm-service
do
  create_write_alias "logs-redbank.${service}-prod"
done

create_write_alias "deadletter-redbank.prod"
create_write_alias "metrics-redbank.prod"

echo "Production OpenSearch observability assets are ready."
