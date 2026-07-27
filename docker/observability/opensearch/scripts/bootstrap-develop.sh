#!/bin/sh
set -eu

: "${OPENSEARCH_URL:=https://opensearch:9200}"
: "${OPENSEARCH_USERNAME:=admin}"
: "${OPENSEARCH_PASSWORD:?OPENSEARCH_PASSWORD is required}"

ROOT=/observability/opensearch

curl_os() {
  method=$1
  path=$2
  body=${3:-}

  if [ -n "$body" ]; then
    curl -fsSk \
      -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
      -H "Content-Type: application/json" \
      -X "$method" \
      --data-binary "@$body" \
      "$OPENSEARCH_URL$path"
  else
    curl -fsSk \
      -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
      -X "$method" \
      "$OPENSEARCH_URL$path"
  fi
}

curl_os_allow_conflict() {
  method=$1
  path=$2
  body=$3
  response_file=/tmp/opensearch-bootstrap-response.json

  status=$(curl -sSk \
    -o "$response_file" \
    -w "%{http_code}" \
    -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -X "$method" \
    --data-binary "@$body" \
    "$OPENSEARCH_URL$path")

  case "$status" in
    200|201|409)
      rm -f "$response_file"
      ;;
    *)
      cat "$response_file" >&2
      rm -f "$response_file"
      exit 1
      ;;
  esac
}

wait_for_opensearch() {
  tries=0
  until curl_os GET "/_cluster/health?wait_for_status=yellow&timeout=5s" >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -ge 60 ]; then
      echo "OpenSearch did not become healthy in time" >&2
      exit 1
    fi
    sleep 2
  done
}

put_assets() {
  curl_os_allow_conflict PUT "/_plugins/_ism/policies/redbank-develop-logs-policy" \
    "$ROOT/ism-policies/redbank-develop-logs-policy.json"
  curl_os_allow_conflict PUT "/_plugins/_ism/policies/redbank-develop-deadletter-policy" \
    "$ROOT/ism-policies/redbank-develop-deadletter-policy.json"
  curl_os_allow_conflict PUT "/_plugins/_ism/policies/redbank-develop-traces-policy" \
    "$ROOT/ism-policies/redbank-develop-traces-policy.json"
  curl_os_allow_conflict PUT "/_plugins/_ism/policies/redbank-develop-metrics-policy" \
    "$ROOT/ism-policies/redbank-develop-metrics-policy.json"

  curl_os_allow_conflict PUT "/_index_template/logs-redbank-template" \
    "$ROOT/index-templates/logs-redbank-template.json"
  curl_os_allow_conflict PUT "/_index_template/deadletter-redbank-template" \
    "$ROOT/index-templates/deadletter-redbank-template.json"
  curl_os_allow_conflict PUT "/_index_template/otel-traces-redbank-template" \
    "$ROOT/index-templates/otel-traces-redbank-template.json"
  curl_os_allow_conflict PUT "/_index_template/otel-service-map-redbank-template" \
    "$ROOT/index-templates/otel-service-map-redbank-template.json"
  curl_os_allow_conflict PUT "/_index_template/metrics-redbank-template" \
    "$ROOT/index-templates/metrics-redbank-template.json"
}

index_exists() {
  curl -fsSk \
    -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
    -I "$OPENSEARCH_URL/$1" >/dev/null 2>&1
}

alias_exists() {
  curl -fsSk \
    -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
    -I "$OPENSEARCH_URL/_alias/$1" >/dev/null 2>&1
}

create_write_alias() {
  alias_name=$1
  index_name="${alias_name}-000001"

  if alias_exists "$alias_name"; then
    return
  fi

  if index_exists "$index_name"; then
    tmp_file="/tmp/${index_name}-alias.json"
    cat > "$tmp_file" <<EOF
{
  "actions": [
    {
      "add": {
        "index": "$index_name",
        "alias": "$alias_name",
        "is_write_index": true
      }
    }
  ]
}
EOF
    curl_os POST "/_aliases" "$tmp_file" >/dev/null
    rm -f "$tmp_file"
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
  create_write_alias "logs-redbank.${service}-develop"
done

create_write_alias "deadletter-redbank.develop"
create_write_alias "metrics-redbank.develop"

echo "Develop OpenSearch observability assets are ready."
