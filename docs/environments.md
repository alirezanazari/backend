# Environments

This document defines how `red-bank` handles runtime environments, configuration, environment variables, debug mode, mock OTP, and deployment.

The project has two real environments:

```text
develop
prod
```

Developer machines use the `develop` profile with local Docker infrastructure.

## Environment Goals

### develop

Purpose:

```text
build, test, debug, integrate, and verify services before production
```

Allowed:

- Local/Postman testing.
- Debug token generation when explicitly enabled.
- Mock OTP `111111` when explicitly enabled.
- Verbose logs when needed.
- Test data.
- Local Docker Compose infrastructure.
- Non-production signing keys.

Not allowed:

- Production user data.
- Production secrets.
- Production JWT signing keys.
- Production third-party credentials.

### prod

Purpose:

```text
real production traffic and real customer data
```

Required:

- Debug mode disabled.
- Mock OTP disabled.
- Production secrets injected securely.
- HTTPS/TLS.
- Strict logging without sensitive data.
- Real JWT signing keys.
- Real database credentials.
- Admin/internal access locked down.
- Monitoring and alerts enabled.

Never allowed:

- Debug token endpoints.
- Mock OTP.
- Fake users or test data mixed with real users.
- Hardcoded secrets.
- Developer-only bypasses.

## Spring Profiles

Use Spring profiles:

```text
develop
prod
```

Common config:

```text
application.yml
```

Develop config:

```text
application-develop.yml
```

Production config:

```text
application-prod.yml
```

Important rule:

```text
profile files can define safe defaults, but secrets must come from environment variables or a secrets manager.
```

## Switching Environment

### IntelliJ IDEA

For each service run configuration:

```text
Active profiles: develop
```

or set environment variable:

```text
SPRING_PROFILES_ACTIVE=develop
```

For production simulation:

```text
SPRING_PROFILES_ACTIVE=prod
```

Never run production profile with debug variables enabled.

### Terminal

Develop:

```bash
SPRING_PROFILES_ACTIVE=develop ./mvnw spring-boot:run
```

Prod simulation:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

### Docker Compose

Develop should use Docker Compose for local dependencies:

```text
docker-compose.develop.yml
```

Example:

```bash
docker compose --env-file .env.develop -f docker-compose.develop.yml up
```

Start the optional local observability stack with:

```bash
docker compose --env-file .env.develop -f docker-compose.develop.yml --profile observability up -d
```

OpenSearch Dashboards is exposed on `http://localhost:5601`. The local
develop username is `admin`, and the password comes from
`REDBANK_OPENSEARCH_PASSWORD`.

Production should not depend on local Docker Compose as the deployment strategy. Production should run images with environment variables/secrets supplied by the deployment platform.

## Configuration Rule

Use this priority:

```text
environment variables / secret manager
profile-specific config
common config
code defaults only for harmless values
```

Do not put secrets in:

- Git repository
- `application.yml`
- `application-prod.yml`
- Docker image
- README examples with real values
- Postman collections

## Environment Variables

Use uppercase environment variable names.

Use this naming style:

```text
REDBANK_<SERVICE>_<SETTING>
```

Common examples:

| Variable | develop | prod | Purpose |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `develop` | `prod` | Active Spring profile. |
| `SERVER_PORT` | service-specific | service-specific | Service HTTP port. |
| `REDBANK_DEBUG_ENABLED` | `false` or `true` | `false` | Enables debug endpoints. |
| `REDBANK_DEBUG_MOCK_OTP_ENABLED` | `false` or `true` | `false` | Allows OTP `111111`. |
| `REDBANK_JWT_ISSUER` | `red-bank-develop` | `red-bank` | JWT issuer. |
| `REDBANK_JWT_AUDIENCE` | `red-bank-api` | `red-bank-api` | JWT audience. |
| `REDBANK_JWT_ACCESS_TOKEN_TTL_SECONDS` | `900` | `900` | User access token lifetime. |
| `REDBANK_JWT_SIGNING_SECRET` | develop secret | prod secret | Current HS256 token signing/verification secret. |
| `REDBANK_JWT_PRIVATE_KEY` | develop secret | prod secret | JWT signing private key. |
| `REDBANK_JWT_PUBLIC_KEY` | develop secret | prod secret | JWT verification public key. |
| `REDBANK_GATEWAY_PUBLIC_BASE_URL` | develop URL | prod URL | Public gateway base URL. |
| `REDBANK_GATEWAY_APP_ENCRYPTION_ENABLED` | `false` | deployment-controlled | Enables gateway X25519/AES-GCM request decryption. |
| `REDBANK_GATEWAY_KEY_EXCHANGE_TTL_SECONDS` | `300` | `300` | Temporary gateway encryption-session lifetime. |
| `REDBANK_GATEWAY_MAX_ENCRYPTED_BODY_BYTES` | `1048576` | policy-controlled | Maximum encrypted and decrypted request body size. |
| `REDBANK_GATEWAY_RATE_LIMIT_ENABLED` | `true` | `true` | Enables API gateway rate limiting. |
| `REDBANK_GATEWAY_RATE_LIMIT_CAPACITY` | `120` | policy-controlled | Maximum burst requests per limiter bucket. |
| `REDBANK_GATEWAY_RATE_LIMIT_REFILL_TOKENS` | `120` | policy-controlled | Requests restored per refill window. |
| `REDBANK_GATEWAY_RATE_LIMIT_REFILL_PERIOD_SECONDS` | `60` | policy-controlled | Rate-limit refill window length. |
| `REDBANK_GATEWAY_RATE_LIMIT_MAX_BUCKETS` | `50000` | policy-controlled | Memory guard for in-gateway rate-limit buckets. |
| `REDBANK_INTERNAL_SERVICE_TOKEN_SECRET` | develop secret | prod secret | Internal service token secret. |
| `REDBANK_LEDGER_CLIENT_ENABLED` | `false` or `true` | `true` | Lets account-service call ledger-service for account creation and balances. |
| `REDBANK_LEDGER_BASE_URL` | `http://localhost:8083` | prod URL | Internal ledger-service base URL. |
| `REDBANK_DB_HOST` | local/develop host | prod host | Database host. |
| `REDBANK_DB_PORT` | `5432` | prod value | Database port. |
| `REDBANK_DB_NAME` | service DB | service DB | Database name. |
| `REDBANK_DB_USERNAME` | develop username | prod username | Database username. |
| `REDBANK_DB_PASSWORD` | develop secret | prod secret | Database password. |
| `REDBANK_FLYWAY_ENABLED` | `true` | deployment-controlled | Database migration toggle. |
| `REDBANK_LOG_LEVEL` | `DEBUG` or `INFO` | `INFO` | Application log level. |
| `REDBANK_LOG_FORMAT` | `text` or `ecs` | `text` on developer machines, `ecs` in prod | Console log format. |
| `REDBANK_OBSERVABILITY_ENVIRONMENT` | `develop` | `prod` | Telemetry environment attribute. |
| `REDBANK_SERVICE_VERSION` | built artifact version | promoted image version | Telemetry service version. |
| `OPENSEARCH_VERSION` | pinned local stack version | mirrored production image tag | OpenSearch container version. |
| `OPENSEARCH_DASHBOARDS_VERSION` | pinned local stack version | mirrored production image tag | OpenSearch Dashboards container version. |
| `DATA_PREPPER_VERSION` | pinned local stack version | mirrored production image tag | OpenSearch Data Prepper container version. |
| `FLUENT_BIT_VERSION` | pinned local stack version | mirrored production image tag | Fluent Bit container version. |
| `REDBANK_OPENSEARCH_PASSWORD` | develop-only secret | secret-manager value | OpenSearch admin password for local/bootstrap automation. |
| `REDBANK_DATA_PREPPER_INGEST_PORT` | `2021` | internal only | Data Prepper HTTP log ingestion port. |
| `REDBANK_DATA_PREPPER_API_PORT` | `4900` | internal only | Data Prepper metrics/API port. |
| `REDBANK_OTEL_EXPORTER_OTLP_ENDPOINT` | develop collector URL | production collector URL | OTLP metrics and traces endpoint. |
| `REDBANK_OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | `grpc` | OTLP export protocol. |
| `REDBANK_EXTERNAL_TRANSFER_BASE_URL` | mock URL | real URL | Third-party transfer provider URL. |
| `REDBANK_EXTERNAL_TRANSFER_API_KEY` | mock secret | prod secret | Third-party transfer credential. |

## Service-Specific Variables

Prefer service-specific database variables when services run separately:

```text
IAM_DB_HOST
IAM_DB_NAME
IAM_DB_USERNAME
IAM_DB_PASSWORD

ACCOUNT_DB_HOST
ACCOUNT_DB_NAME
ACCOUNT_DB_USERNAME
ACCOUNT_DB_PASSWORD

LEDGER_DB_HOST
LEDGER_DB_NAME
LEDGER_DB_USERNAME
LEDGER_DB_PASSWORD

TRANSFER_DB_HOST
TRANSFER_DB_NAME
TRANSFER_DB_USERNAME
TRANSFER_DB_PASSWORD

FEATURE_FLAG_DB_HOST
FEATURE_FLAG_DB_NAME
FEATURE_FLAG_DB_USERNAME
FEATURE_FLAG_DB_PASSWORD
```

This is clearer than sharing one generic DB variable set across every service.

## Example Spring Configuration

Common `application.yml`:

```yaml
spring:
  application:
    name: ${APP_NAME:red-bank-service}

server:
  port: ${SERVER_PORT:8080}

redbank:
  debug:
    enabled: ${REDBANK_DEBUG_ENABLED:false}
    mock-otp:
      enabled: ${REDBANK_DEBUG_MOCK_OTP_ENABLED:false}
  jwt:
    issuer: ${REDBANK_JWT_ISSUER}
    audience: ${REDBANK_JWT_AUDIENCE:red-bank-api}
    access-token-ttl-seconds: ${REDBANK_JWT_ACCESS_TOKEN_TTL_SECONDS:900}
```

Important:

```text
do not hardcode active profile inside application.yml
SPRING_PROFILES_ACTIVE must be provided by IntelliJ, terminal, Docker Compose, or deployment platform
```

Develop `application-develop.yml`:

```yaml
logging:
  level:
    root: ${REDBANK_LOG_LEVEL:INFO}
    redbank: DEBUG

redbank:
  debug:
    enabled: ${REDBANK_DEBUG_ENABLED:false}
    mock-otp:
      enabled: ${REDBANK_DEBUG_MOCK_OTP_ENABLED:false}
```

Prod `application-prod.yml`:

```yaml
logging:
  level:
    root: INFO
    redbank: INFO

redbank:
  debug:
    enabled: false
    mock-otp:
      enabled: false
```

Important:

```text
production config sets safe debug defaults to false, and startup guards still verify final resolved values
```

## Debug and Mock OTP

Debug tools are allowed only in `develop`.

Allowed in develop only:

- `/debug/v1/tokens/user`
- `/debug/v1/tokens/admin`
- `/debug/v1/tokens/service`
- Mock OTP `111111`

Required properties:

```text
REDBANK_DEBUG_ENABLED=true
REDBANK_DEBUG_MOCK_OTP_ENABLED=true
```

Production values:

```text
REDBANK_DEBUG_ENABLED=false
REDBANK_DEBUG_MOCK_OTP_ENABLED=false
```

Startup guard:

```text
if SPRING_PROFILES_ACTIVE=prod and debug is enabled, fail startup
if SPRING_PROFILES_ACTIVE=prod and mock OTP is enabled, fail startup
```

The debug behavior must be implemented as guarded code, not as comments or manual discipline.

## Safe Debug Implementation

Use these guards:

```text
@Profile("develop")
@ConditionalOnProperty(name = "redbank.debug.enabled", havingValue = "true")
```

For mock OTP:

```text
@Profile("develop")
@ConditionalOnProperty(name = "redbank.debug.mock-otp.enabled", havingValue = "true")
```

Add a startup guard in every service that has debug behavior:

```text
if active profile contains prod and redbank.debug.enabled is true, throw startup exception
if active profile contains prod and redbank.debug.mock-otp.enabled is true, throw startup exception
```

## Environment Files

Use example env files in the repository:

```text
.env.develop.example
.env.prod.example
```

Do not commit real env files:

```text
.env.develop
.env.prod
```

Recommended `.gitignore`:

```text
.env
.env.*
!.env.example
!.env.develop.example
!.env.prod.example
```

## Develop Deployment

Develop deployment can be:

```text
local Docker Compose
shared develop server
temporary cloud environment
```

Develop deployment steps:

1. Build service artifact/image.
2. Set `SPRING_PROFILES_ACTIVE=develop`.
3. Inject develop environment variables.
4. Start PostgreSQL and dependencies.
5. Run Flyway migrations.
6. Start services.
7. Start API Gateway.
8. Verify `/actuator/health`.
9. Test API through gateway.
10. Optionally enable debug features for controlled testing.

Develop can use mock third-party services:

```text
external transfer provider mock
debug token generator
mock OTP
seeded test data
```

## Prod Deployment

Production deployment must use the same built artifact/image as develop, with production configuration injected externally.

Production deployment steps:

1. Build immutable image once.
2. Run automated tests.
3. Deploy same image to develop.
4. Verify develop.
5. Promote same image to prod.
6. Set `SPRING_PROFILES_ACTIVE=prod`.
7. Inject prod secrets through secret manager or deployment platform.
8. Run database migrations in controlled release step.
9. Start services.
10. Verify health checks.
11. Verify logs, metrics, alerts.
12. Keep debug and mock OTP disabled.

Production must not:

- Build directly on production server.
- Use develop secrets.
- Use debug token endpoints.
- Use mock OTP.
- Use local Docker Compose as the production deployment model.

## Database Migrations By Environment

Use Flyway migrations for all environments.

Rules:

- Migration files are the same for develop and prod.
- Develop can be reset if needed.
- Prod migrations must be forward-only.
- Prod migrations must be reviewed before deployment.
- Dangerous prod migrations need backup and rollback planning.

Recommended:

```text
develop: Flyway runs automatically on service startup
prod: Flyway runs as controlled deployment step
```

This avoids accidental production schema changes during app startup.

## Secrets

Develop secrets:

- Can live in local `.env.develop`.
- Can use fake/mock values.
- Must not be production-compatible.

Prod secrets:

- Must come from secret manager or deployment platform.
- Must not be committed.
- Must be rotated.
- Must be restricted by service.

Examples of secrets:

- Database password
- JWT private key
- Service token secret
- Third-party API key
- Encryption key material

## Logging By Environment

Developer machine:

```text
human-readable console logs by default
ECS JSON available for ingestion testing
debug logs can be enabled temporarily
```

Set `REDBANK_LOG_FORMAT=ecs` locally only when testing structured log ingestion.

Shared develop:

```text
centralized non-production log platform
separate credentials and indexes from prod
short retention
```

The local/develop observability Compose profile starts OpenSearch, OpenSearch
Dashboards, Data Prepper, and Fluent Bit. Fluent Bit reads container stdout and
`logs/develop/*.log`, then forwards only Redbank ECS JSON records. Data Prepper
parses, redacts, validates, routes valid develop service logs to
`logs-redbank.<service>-develop`, and routes invalid records to
`deadletter-redbank.develop`.

When testing ingestion from locally started services, run them with
`REDBANK_LOG_FORMAT=ecs` or `scripts/run-develop-services.sh --ecs-logs`.
Human-readable text logs are intentionally ignored by Fluent Bit.

Prod:

```text
centralized production log platform
ECS JSON written to stdout
INFO by default
no sensitive data
no passwords
no OTP values
no full tokens
no full card numbers
no raw request body for sensitive APIs
```

Production profile defaults to ECS JSON logs. Set `REDBANK_LOG_FORMAT=text`
only for controlled local production-profile simulation.

Use separate self-hosted OpenSearch Observability stacks for shared `develop`
and `prod`. Use Data Prepper plus OpenTelemetry Collector or Fluent Bit for
ingestion.

Foreign managed observability services must not be required for runtime or
recovery unless legal eligibility, payment, and account continuity have been
confirmed for the Iranian organization. Mirror approved observability images
and packages into an internal registry.

Production OpenSearch infrastructure assets live under
`deploy/observability/opensearch/production` and
`docker/observability/opensearch/production`. The production bootstrap is
guarded and requires a production-only endpoint, credentials, TLS trust, and
encrypted external snapshot repository variables.

See:

- [Observability and Centralized Logging](./observability.md)
- [OpenSearch Observability Platform](./opensearch-platform.md)

## Environment Startup Validation

Every service should validate dangerous settings at startup.

Fail startup in prod if:

- `REDBANK_DEBUG_ENABLED=true`
- `REDBANK_DEBUG_MOCK_OTP_ENABLED=true`
- JWT issuer is develop/debug issuer
- JWT signing key is missing
- Database password is missing
- Required service URLs are missing
- External provider credentials are missing
- TLS requirement is disabled for public gateway
- Production logging collector or destination is missing

## Implementation Checklist

1. Create `application.yml`.
2. Create `application-develop.yml`.
3. Create `application-prod.yml`.
4. Add environment variable placeholders.
5. Add `.env.develop.example`.
6. Add `.env.prod.example`.
7. Add startup guard for prod safety.
8. Add debug property class.
9. Add tests for prod guard.
10. Add IntelliJ run configuration notes.
11. Add Docker Compose develop file.
12. Verify service starts with `develop`.
13. Verify service refuses unsafe debug config in `prod`.

## Developer Quick Start

For local development:

```bash
SPRING_PROFILES_ACTIVE=develop
REDBANK_DEBUG_ENABLED=true
REDBANK_DEBUG_MOCK_OTP_ENABLED=true
```

Then start dependencies:

```bash
docker compose --env-file .env.develop -f docker-compose.develop.yml up
```

Optional observability stack:

```bash
docker compose --env-file .env.develop -f docker-compose.develop.yml --profile observability up -d
```

Then start service from IntelliJ with:

```text
Active profile: develop
```

or from terminal:

```bash
SPRING_PROFILES_ACTIVE=develop ./mvnw spring-boot:run
```

## Production Quick Check

Before prod release, verify:

```text
SPRING_PROFILES_ACTIVE=prod
REDBANK_DEBUG_ENABLED=false
REDBANK_DEBUG_MOCK_OTP_ENABLED=false
```

And verify:

- Debug endpoints return 404 or are not registered.
- OTP `111111` does not work.
- Tokens use prod issuer.
- Services use prod secrets.
- Gateway rate limiting is enabled and production thresholds are approved.
- Health checks pass.
- Metrics and alerts are active.
