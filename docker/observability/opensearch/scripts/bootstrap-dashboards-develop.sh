#!/bin/sh
set -eu

: "${OPENSEARCH_URL:=https://opensearch:9200}"
: "${OPENSEARCH_USERNAME:=admin}"
: "${OPENSEARCH_PASSWORD:?OPENSEARCH_PASSWORD is required}"
: "${OPENSEARCH_DASHBOARDS_URL:=http://opensearch-dashboards:5601}"
: "${OPENSEARCH_DASHBOARDS_USERNAME:=$OPENSEARCH_USERNAME}"
: "${OPENSEARCH_DASHBOARDS_PASSWORD:=$OPENSEARCH_PASSWORD}"

ROOT=/observability/opensearch
ASSET_INDEX=redbank-observability-assets-v1

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

wait_for_dashboards() {
  tries=0
  until curl -fsSk \
    -u "$OPENSEARCH_DASHBOARDS_USERNAME:$OPENSEARCH_DASHBOARDS_PASSWORD" \
    -H "osd-xsrf: true" \
    "$OPENSEARCH_DASHBOARDS_URL/api/status" >/dev/null 2>&1; do
    tries=$((tries + 1))
    if [ "$tries" -ge 60 ]; then
      echo "OpenSearch Dashboards did not become ready in time" >&2
      exit 1
    fi
    sleep 2
  done
}

index_exists() {
  curl -fsSk \
    -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
    -I "$OPENSEARCH_URL/$1" >/dev/null 2>&1
}

ensure_asset_index() {
  if index_exists "$ASSET_INDEX"; then
    return
  fi

  index_body=/tmp/redbank-observability-assets-index.json
  cat > "$index_body" <<EOF
{
  "settings": {
    "index.number_of_shards": "1",
    "index.number_of_replicas": "0"
  },
  "mappings": {
    "dynamic": false,
    "properties": {
      "schemaVersion": { "type": "integer" },
      "environment": { "type": "keyword" },
      "owner": { "type": "keyword" },
      "description": { "type": "match_only_text" },
      "dataViews": { "type": "object", "enabled": false },
      "dashboards": { "type": "object", "enabled": false },
      "probes": { "type": "object", "enabled": false }
    }
  }
}
EOF
  curl_os PUT "/$ASSET_INDEX" "$index_body" >/dev/null
  rm -f "$index_body"
}

import_dashboards() {
  ndjson="$ROOT/dashboards/redbank-develop-dashboards.ndjson"
  response=/tmp/redbank-dashboards-import-response.json

  status=$(curl -sSk \
    -o "$response" \
    -w "%{http_code}" \
    -u "$OPENSEARCH_DASHBOARDS_USERNAME:$OPENSEARCH_DASHBOARDS_PASSWORD" \
    -H "osd-xsrf: true" \
    -H "securitytenant: global" \
    -X POST \
    -F "file=@$ndjson" \
    "$OPENSEARCH_DASHBOARDS_URL/api/saved_objects/_import?overwrite=true")

  if [ "$status" != "200" ]; then
    cat "$response" >&2
    exit 1
  fi

  if ! grep -q '"success":true' "$response"; then
    cat "$response" >&2
    exit 1
  fi

  rm -f "$response"
  echo "Imported OpenSearch Dashboards saved objects."
}

put_catalogs() {
  ensure_asset_index
  curl_os PUT "/$ASSET_INDEX/_doc/redbank-develop-dashboard-catalog" \
    "$ROOT/dashboards/redbank-develop-dashboard-catalog.json" >/dev/null
  curl_os PUT "/$ASSET_INDEX/_doc/redbank-develop-platform-probes" \
    "$ROOT/alerting/platform-probes/redbank-develop-platform-probes.json" >/dev/null
  echo "Indexed develop dashboard and platform probe catalogs."
}

wait_for_dashboards
import_dashboards
put_catalogs

echo "Develop OpenSearch Dashboards assets are ready."
