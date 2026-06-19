#!/bin/sh
set -eu

ROOT=${1:-docker/observability/opensearch/production}
DEPLOY_ROOT=${2:-deploy/observability/opensearch/production}
required_dirs="index-templates ism-policies security/roles snapshot-repositories"

for dir in $required_dirs; do
  if [ ! -d "$ROOT/$dir" ]; then
    echo "Missing production asset directory: $ROOT/$dir" >&2
    exit 1
  fi
done

find "$ROOT" -name '*.json' -print | while read -r file; do
  jq empty "$file"
done

if [ -d "$DEPLOY_ROOT" ]; then
  if command -v ruby >/dev/null 2>&1; then
    find "$DEPLOY_ROOT" \( -name '*.yaml' -o -name '*.yml' \) -print \
      | xargs ruby -e 'require "yaml"; ARGV.each { |file| YAML.load_file(file) }'
  else
    echo "Ruby is not available; skipped YAML parse validation for $DEPLOY_ROOT." >&2
  fi
fi

if grep -R '"environment": "develop"\|develop-' "$ROOT" >/dev/null; then
  echo "Production assets must not contain develop environment markers." >&2
  exit 1
fi

role_count=$(find "$ROOT/security/roles" -name '*.json' | wc -l | tr -d ' ')
if [ "$role_count" -lt 5 ]; then
  echo "Expected at least five production security roles; found $role_count." >&2
  exit 1
fi

for policy in logs deadletter metrics traces; do
  if [ ! -f "$ROOT/ism-policies/redbank-prod-$policy-policy.json" ]; then
    echo "Missing production ISM policy for $policy." >&2
    exit 1
  fi
done

echo "Production OpenSearch assets pass static safety checks."
