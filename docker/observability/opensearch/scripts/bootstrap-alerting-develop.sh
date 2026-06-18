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

find_monitor_ids() {
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

  status=$(curl -sSk \
    -o "$search_response" \
    -w "%{http_code}" \
    -u "$OPENSEARCH_USERNAME:$OPENSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -X GET \
    --data-binary "@$search_body" \
    "$OPENSEARCH_URL/_plugins/_alerting/monitors/_search")

  case "$status" in
    200)
      sed 's/"_id":"/\
"_id":"/g' "$search_response" \
        | sed -n 's/^"_id":"\([^"]*\)".*/\1/p' \
        | while read -r candidate_id; do
            [ -n "$candidate_id" ] || continue
            if curl_os GET "/_plugins/_alerting/monitors/$candidate_id" > "$monitor_response" 2>/dev/null; then
              candidate_name=$(sed -n 's/.*"monitor":{[^}]*"name":"\([^"]*\)".*/\1/p' "$monitor_response")
              if [ "$candidate_name" = "$name" ]; then
                echo "$candidate_id"
              fi
            fi
          done
      ;;
    404)
      # The alerting config index may not exist until the first monitor is created.
      true
      ;;
    *)
      cat "$search_response" >&2
      exit 1
      ;;
  esac

  rm -f "$search_body" "$search_response" "$monitor_response"
}

put_monitor() {
  file=$1
  name=$(basename "$file" .json)
  monitor_ids=$(find_monitor_ids "$name")
  monitor_id=$(printf '%s\n' "$monitor_ids" | sed -n '1p')

  if [ -n "$monitor_id" ]; then
    curl_os PUT "/_plugins/_alerting/monitors/$monitor_id" "$file" >/dev/null
    printf '%s\n' "$monitor_ids" | sed '1d' | while read -r duplicate_id; do
      [ -n "$duplicate_id" ] || continue
      curl_os DELETE "/_plugins/_alerting/monitors/$duplicate_id" >/dev/null
    done
    echo "Updated alert monitor: $name"
  else
    curl_os POST "/_plugins/_alerting/monitors" "$file" >/dev/null
    echo "Created alert monitor: $name"
  fi
}

wait_for_opensearch

for file in "$ROOT"/alerting/monitors/*.json; do
  [ -e "$file" ] || continue
  put_monitor "$file"
done

echo "Develop OpenSearch alert monitors are ready."
