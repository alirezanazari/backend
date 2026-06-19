#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.develop"
LOG_DIR="$ROOT_DIR/logs/develop"
COMPOSE_ARGS=(--env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.develop.yml")

START_DOCKER=true
START_OBSERVABILITY=false
BUILD_SERVICES=true
CLEAN_BUILD=false
FRESH_START=false
HEALTH_TIMEOUT_SECONDS=180
REUSE_HEALTH_TIMEOUT_SECONDS=30

SERVICE_NAMES=(
  ledger-service
  account-service
  feature-flag-service
  iam-service
  card-service
  kyc-service
  transfer-service
  pfm-service
  api-gateway
)

SERVICE_PORTS=(
  8083
  8082
  8086
  8081
  8084
  8085
  8087
  8088
  8080
)

PIDS=()
NAMES=()
PORTS=()
LOGS=()
REUSED_NAMES=()
REUSED_PORTS=()

usage() {
  cat <<'USAGE'
Usage: scripts/run-develop-services.sh [options]

Starts local develop infrastructure and all Spring services in one terminal.
Logs are written to logs/develop/<service>.log.

Options:
  --observability   Start OpenSearch, Dashboards, Data Prepper, OTEL, Fluent Bit
  --no-docker       Do not start Docker Compose infrastructure
  --skip-build      Do not run Maven package before starting services
  --clean           Run Maven clean install instead of install
  --fresh           Stop existing processes on service ports before starting
  --help            Show this help

Examples:
  scripts/run-develop-services.sh
  scripts/run-develop-services.sh --observability
  scripts/run-develop-services.sh --skip-build
  scripts/run-develop-services.sh --no-docker --skip-build --fresh
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --observability)
      START_OBSERVABILITY=true
      ;;
    --no-docker)
      START_DOCKER=false
      ;;
    --skip-build)
      BUILD_SERVICES=false
      ;;
    --clean)
      CLEAN_BUILD=true
      ;;
    --fresh)
      FRESH_START=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

cleanup() {
  local exit_code=$?

  if [[ ${#PIDS[@]} -gt 0 ]]; then
    echo
    echo "Stopping Spring services..."
    for pid in "${PIDS[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
      fi
    done
    for pid in "${PIDS[@]}"; do
      wait "$pid" 2>/dev/null || true
    done
  fi

  exit "$exit_code"
}

trap cleanup EXIT INT TERM

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

wait_for_container_health() {
  local container="$1"
  local timeout_seconds="$2"
  local deadline=$((SECONDS + timeout_seconds))
  local status

  printf 'Waiting for %s to become healthy' "$container"
  while (( SECONDS < deadline )); do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" == "healthy" || "$status" == "running" ]]; then
      echo " OK"
      return 0
    fi
    printf '.'
    sleep 2
  done

  echo
  echo "Timed out waiting for $container. Current status: ${status:-unknown}" >&2
  return 1
}

wait_for_url() {
  local name="$1"
  local url="$2"
  local timeout_seconds="$3"
  local deadline=$((SECONDS + timeout_seconds))

  printf 'Waiting for %s' "$name"
  while (( SECONDS < deadline )); do
    if curl --fail --silent --show-error --max-time 2 "$url" >/dev/null 2>&1; then
      echo " OK"
      return 0
    fi
    printf '.'
    sleep 2
  done

  echo
  echo "Timed out waiting for $name at $url" >&2
  return 1
}

wait_for_opensearch() {
  local port="${REDBANK_OPENSEARCH_PORT:-9200}"
  local password="${REDBANK_OPENSEARCH_PASSWORD:-RedbankDevelopAdmin123!}"
  local url="https://localhost:${port}/_cluster/health"
  local deadline=$((SECONDS + 300))

  printf 'Waiting for OpenSearch'
  while (( SECONDS < deadline )); do
    if curl --fail --silent --show-error --max-time 3 -k -u "admin:${password}" "$url" >/dev/null 2>&1; then
      echo " OK"
      return 0
    fi
    printf '.'
    sleep 3
  done

  echo
  echo "Timed out waiting for OpenSearch at $url" >&2
  return 1
}

port_in_use() {
  local port="$1"

  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v nc >/dev/null 2>&1; then
    nc -z localhost "$port" >/dev/null 2>&1
  else
    return 1
  fi
}

stop_existing_service_ports() {
  if ! command -v lsof >/dev/null 2>&1; then
    echo "--fresh requires lsof to find existing service processes." >&2
    exit 1
  fi

  local killed_pids=""

  for port in "${SERVICE_PORTS[@]}"; do
    local pids
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -z "$pids" ]]; then
      continue
    fi

    while IFS= read -r pid; do
      if [[ -z "$pid" || " $killed_pids " == *" $pid "* ]]; then
        continue
      fi

      echo "Stopping existing process $pid on service port $port"
      if ! kill "$pid"; then
        echo "Could not stop process $pid on service port $port." >&2
      fi
      killed_pids="$killed_pids $pid"
    done <<< "$pids"
  done

  if [[ -z "$killed_pids" ]]; then
    return 0
  fi

  local graceful_deadline=$((SECONDS + 10))
  while (( SECONDS < graceful_deadline )); do
    local any_port_in_use=false
    for port in "${SERVICE_PORTS[@]}"; do
      if port_in_use "$port"; then
        any_port_in_use=true
        break
      fi
    done

    if [[ "$any_port_in_use" == false ]]; then
      return 0
    fi
    sleep 1
  done

  local force_killed_pids=""
  for port in "${SERVICE_PORTS[@]}"; do
    local stubborn_pids
    stubborn_pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -z "$stubborn_pids" ]]; then
      continue
    fi

    while IFS= read -r pid; do
      if [[ -z "$pid" || " $force_killed_pids " == *" $pid "* ]]; then
        continue
      fi

      echo "Force-stopping process $pid on service port $port"
      if ! kill -KILL "$pid"; then
        echo "Could not force-stop process $pid on service port $port." >&2
      fi
      force_killed_pids="$force_killed_pids $pid"
    done <<< "$stubborn_pids"
  done

  local force_deadline=$((SECONDS + 10))
  while (( SECONDS < force_deadline )); do
    local any_port_in_use=false
    for port in "${SERVICE_PORTS[@]}"; do
      if port_in_use "$port"; then
        any_port_in_use=true
        break
      fi
    done

    if [[ "$any_port_in_use" == false ]]; then
      return 0
    fi
    sleep 1
  done

  echo "Some service ports are still in use after force stop. Stop them manually, then rerun." >&2
  for port in "${SERVICE_PORTS[@]}"; do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2 || true
  done
  exit 1
}

find_service_jar() {
  local service="$1"
  local target_dir="$ROOT_DIR/$service/target"

  if [[ ! -d "$target_dir" ]]; then
    return 0
  fi

  find "$target_dir" -maxdepth 1 -type f \
    -name "$service-*.jar" ! -name "*.original" | sort | tail -n 1
}

class_files_contain_compiler_errors() {
  local path="$1"

  if [[ ! -e "$path" ]]; then
    return 1
  fi

  if command -v rg >/dev/null 2>&1; then
    rg -a -q "Unresolved compilation problem" "$path" 2>/dev/null
  else
    grep -R -a -q "Unresolved compilation problem" "$path" 2>/dev/null
  fi
}

jar_contains_compiler_errors() {
  local jar_path="$1"
  local status

  if ! command -v unzip >/dev/null 2>&1 || ! command -v strings >/dev/null 2>&1; then
    return 1
  fi

  set +o pipefail
  unzip -p "$jar_path" "*.class" 2>/dev/null | strings | grep -q "Unresolved compilation problem"
  status=$?
  set -o pipefail

  return "$status"
}

validate_service_artifacts() {
  local has_error=false
  local service

  for service in "${SERVICE_NAMES[@]}"; do
    local classes_dir="$ROOT_DIR/$service/target/classes"
    local jar_path
    jar_path="$(find_service_jar "$service")"

    if [[ -z "$jar_path" ]]; then
      echo "Missing jar for $service. Run without --skip-build first." >&2
      has_error=true
      continue
    fi

    if [[ -d "$classes_dir" ]] && class_files_contain_compiler_errors "$classes_dir"; then
      echo "Bad compiled classes found for $service in $classes_dir." >&2
      has_error=true
    fi

    if jar_contains_compiler_errors "$jar_path"; then
      echo "Bad compiled classes found inside $jar_path." >&2
      has_error=true
    fi

    if [[ -d "$classes_dir" ]] && find "$classes_dir" -type f -newer "$jar_path" -print -quit | grep -q .; then
      echo "Jar for $service is older than compiled classes: $jar_path." >&2
      has_error=true
    fi
  done

  if [[ "$has_error" == true ]]; then
    echo >&2
    echo "Rebuild cleanly, then start again:" >&2
    echo "  scripts/run-develop-services.sh --fresh --clean" >&2
    echo "  # or, if Docker/Postgres is already running:" >&2
    echo "  scripts/run-develop-services.sh --no-docker --fresh --clean" >&2
    exit 1
  fi
}

start_service() {
  local service="$1"
  local port="$2"
  local jar_path
  local log_path="$LOG_DIR/$service.log"
  local health_url="http://localhost:${port}/actuator/health"

  if port_in_use "$port"; then
    local reuse_deadline=$((SECONDS + REUSE_HEALTH_TIMEOUT_SECONDS))

    printf 'Port %s is already in use; checking %s health' "$port" "$service"
    while (( SECONDS < reuse_deadline )); do
      if curl --fail --silent --show-error --max-time 2 "$health_url" >/dev/null 2>&1; then
        echo " OK"
        echo "Reusing already-running $service on :$port"
        REUSED_NAMES+=("$service")
        REUSED_PORTS+=("$port")
        return 0
      fi
      printf '.'
      sleep 2
    done
    echo

    if curl --fail --silent --show-error --max-time 5 "$health_url" >/dev/null 2>&1; then
      echo "Reusing already-running $service on :$port"
      REUSED_NAMES+=("$service")
      REUSED_PORTS+=("$port")
      return 0
    fi

    echo "Port $port is already in use, but $health_url is not healthy; cannot start $service." >&2
    if command -v lsof >/dev/null 2>&1; then
      lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2 || true
    fi
    exit 1
  fi

  jar_path="$(find_service_jar "$service")"
  if [[ -z "$jar_path" ]]; then
    echo "No jar found for $service. Run without --skip-build first." >&2
    exit 1
  fi

  : > "$log_path"
  echo "Starting $service on :$port -> $log_path"
  (
    cd "$ROOT_DIR"
    REDBANK_SERVICE_NODE_NAME="local-$service" java -jar "$jar_path"
  ) >"$log_path" 2>&1 &

  PIDS+=("$!")
  NAMES+=("$service")
  PORTS+=("$port")
  LOGS+=("$log_path")
}

wait_for_service_health() {
  local service="$1"
  local port="$2"
  local log_path="$3"
  local pid="$4"
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  local url="http://localhost:${port}/actuator/health"

  printf 'Waiting for %s' "$service"
  while (( SECONDS < deadline )); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo
      echo "$service exited before becoming healthy. Last log lines:" >&2
      tail -80 "$log_path" >&2 || true
      exit 1
    fi

    if curl --fail --silent --show-error --max-time 2 "$url" >/dev/null 2>&1; then
      echo " OK"
      return 0
    fi

    printf '.'
    sleep 2
  done

  echo
  echo "Timed out waiting for $service at $url. Last log lines:" >&2
  tail -80 "$log_path" >&2 || true
  exit 1
}

monitor_services() {
  while true; do
    for i in "${!PIDS[@]}"; do
      if ! kill -0 "${PIDS[$i]}" 2>/dev/null; then
        echo "${NAMES[$i]} stopped unexpectedly. See ${LOGS[$i]}" >&2
        exit 1
      fi
    done
    sleep 5
  done
}

cd "$ROOT_DIR"

require_command curl
require_command java

if [[ "$START_DOCKER" == true ]]; then
  require_command docker
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it from .env.develop.example first." >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-develop}"
unset SERVER_PORT

mkdir -p "$LOG_DIR"

if [[ "$START_DOCKER" == true ]]; then
  if [[ "$START_OBSERVABILITY" == true ]]; then
    echo "Starting Docker infrastructure with observability..."
    docker compose "${COMPOSE_ARGS[@]}" --profile observability up -d
    wait_for_container_health redbank-postgres 120
    wait_for_opensearch
    wait_for_url "Data Prepper" "http://localhost:${REDBANK_DATA_PREPPER_INGEST_PORT:-2021}/health" 180
  else
    echo "Starting Docker PostgreSQL..."
    docker compose "${COMPOSE_ARGS[@]}" up -d postgres
    wait_for_container_health redbank-postgres 120
  fi
fi

if [[ "$BUILD_SERVICES" == true ]]; then
  JAVAC_EXECUTABLE="${JAVA_HOME:+$JAVA_HOME/bin/javac}"
  if [[ -z "$JAVAC_EXECUTABLE" || ! -x "$JAVAC_EXECUTABLE" ]]; then
    JAVAC_EXECUTABLE="$(command -v javac || true)"
  fi
  if [[ -z "$JAVAC_EXECUTABLE" || ! -x "$JAVAC_EXECUTABLE" ]]; then
    echo "Could not find javac. Install/use Java 21 JDK before building services." >&2
    exit 1
  fi

  MAVEN_BUILD_FLAGS=(
    -Dmaven.test.skip=true
    -Dmaven.compiler.fork=true
    "-Dmaven.compiler.executable=$JAVAC_EXECUTABLE"
    -Dmaven.compiler.useIncrementalCompilation=false
  )

  if [[ "$CLEAN_BUILD" == true ]]; then
    echo "Building service jars with Maven clean install..."
    ./mvnw clean install "${MAVEN_BUILD_FLAGS[@]}"
  else
    echo "Building service jars with Maven install..."
    ./mvnw install "${MAVEN_BUILD_FLAGS[@]}"
  fi
fi

validate_service_artifacts

if [[ "$FRESH_START" == true ]]; then
  stop_existing_service_ports
fi

for i in "${!SERVICE_NAMES[@]}"; do
  start_service "${SERVICE_NAMES[$i]}" "${SERVICE_PORTS[$i]}"
done

for i in "${!PIDS[@]}"; do
  wait_for_service_health "${NAMES[$i]}" "${PORTS[$i]}" "${LOGS[$i]}" "${PIDS[$i]}"
done

echo
echo "All develop services are healthy."
echo "Gateway: http://localhost:8080"
echo "Swagger: http://localhost:8080/swagger-ui.html"
echo "Logs: $LOG_DIR"
if [[ "$START_OBSERVABILITY" == true ]]; then
  echo "OpenSearch Dashboards: http://localhost:${REDBANK_OPENSEARCH_DASHBOARDS_PORT:-5601}"
fi
if [[ ${#REUSED_NAMES[@]} -gt 0 ]]; then
  echo "Reused already-running services:"
  for i in "${!REUSED_NAMES[@]}"; do
    echo "  - ${REUSED_NAMES[$i]} on :${REUSED_PORTS[$i]}"
  done
fi
echo
if [[ ${#PIDS[@]} -eq 0 ]]; then
  echo "No services were started by this script; existing services are left running."
  exit 0
fi

echo "Keep this terminal open. Press Ctrl+C to stop services started by this script."

monitor_services
