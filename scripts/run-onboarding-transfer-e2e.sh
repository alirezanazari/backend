#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COLLECTION="$ROOT_DIR/docs/postman/onboarding-transfer-e2e.postman_collection.json"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.develop}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

GATEWAY_BASE_URL="${GATEWAY_BASE_URL:-http://localhost:8080}"
IAM_BASE_URL="${IAM_BASE_URL:-http://localhost:8081}"
ACCOUNT_BASE_URL="${ACCOUNT_BASE_URL:-http://localhost:8082}"
LEDGER_BASE_URL="${LEDGER_BASE_URL:-http://localhost:8083}"
FEATURE_FLAG_BASE_URL="${FEATURE_FLAG_BASE_URL:-http://localhost:8086}"
TRANSFER_BASE_URL="${TRANSFER_BASE_URL:-http://localhost:8087}"
INTERNAL_SERVICE_TOKEN="${REDBANK_INTERNAL_SERVICE_TOKEN_SECRET:-local-develop-internal-service-token-change-me}"
PROVISIONING_WAIT_MS="${PROVISIONING_WAIT_MS:-6500}"

if [[ "${REDBANK_LEDGER_CLIENT_ENABLED:-false}" != "true" ]]; then
  printf 'Onboarding transfer E2E requires REDBANK_LEDGER_CLIENT_ENABLED=true. Update %s and restart account-service.\n' "$ENV_FILE" >&2
  exit 1
fi

for health_url in \
  "$GATEWAY_BASE_URL/actuator/health" \
  "$IAM_BASE_URL/actuator/health" \
  "$ACCOUNT_BASE_URL/actuator/health" \
  "$LEDGER_BASE_URL/actuator/health" \
  "$FEATURE_FLAG_BASE_URL/actuator/health" \
  "$TRANSFER_BASE_URL/actuator/health"; do
  if ! curl --fail --silent --show-error "$health_url" >/dev/null; then
    printf 'Required service is not healthy: %s\n' "$health_url" >&2
    exit 1
  fi
done

if command -v newman >/dev/null 2>&1; then
  NEWMAN=(newman)
elif command -v npx >/dev/null 2>&1; then
  NEWMAN=(npx --yes newman)
else
  printf 'Newman or npx is required. Install Newman with: npm install --global newman\n' >&2
  exit 1
fi

"${NEWMAN[@]}" run "$COLLECTION" \
  --reporters cli \
  --timeout-request 15000 \
  --env-var "gatewayBaseUrl=$GATEWAY_BASE_URL" \
  --env-var "ledgerBaseUrl=$LEDGER_BASE_URL" \
  --env-var "internalServiceToken=$INTERNAL_SERVICE_TOKEN" \
  --env-var "provisioningWaitMs=$PROVISIONING_WAIT_MS" \
  "$@"
