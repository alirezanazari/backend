# red-bank Backend

Digital banking backend.

This repository is designed as real microservices from the start, with production-grade boundaries, security, financial consistency, and operational concerns documented before implementation.

## Project Structure

```text
red-bank
  contract   shared API/value contracts only
  api-gateway          public HTTP boundary and routing
  iam-service          identity/authentication foundation
  account-service      profiles and account metadata
  ledger-service       financial source of truth
  card-service         card lifecycle
  kyc-service          customer verification and referrals
  feature-flag-service global and user-targeted runtime feature controls
  transfer-service     money movement orchestration and history
  pfm-service          personal finance and vault-backed boxes
  docs                 architecture and implementation documents
  docker               local infrastructure support
```

## Useful Local URLs

| Service | URL |
| --- | --- |
| API Gateway health | `http://localhost:8080/actuator/health` |
| API Gateway OpenAPI UI | `http://localhost:8080/swagger-ui.html` |
| IAM health | `http://localhost:8081/actuator/health` |
| IAM OpenAPI UI | `http://localhost:8081/swagger-ui.html` |
| Account health | `http://localhost:8082/actuator/health` |
| Account OpenAPI UI | `http://localhost:8082/swagger-ui.html` |
| Ledger health | `http://localhost:8083/actuator/health` |
| Ledger OpenAPI UI | `http://localhost:8083/swagger-ui.html` |
| Card health | `http://localhost:8084/actuator/health` |
| Card OpenAPI UI | `http://localhost:8084/swagger-ui.html` |
| KYC health | `http://localhost:8085/actuator/health` |
| KYC OpenAPI UI | `http://localhost:8085/swagger-ui.html` |
| Feature Flag health | `http://localhost:8086/actuator/health` |
| Feature Flag OpenAPI UI | `http://localhost:8086/swagger-ui.html` |
| Transfer health | `http://localhost:8087/actuator/health` |
| Transfer OpenAPI UI | `http://localhost:8087/swagger-ui.html` |
| PFM health | `http://localhost:8088/actuator/health` |
| PFM OpenAPI UI | `http://localhost:8088/swagger-ui.html` |

## Documentation

- [Architecture Overview](./docs/architecture.md)
- [API Contract](./docs/api-contract.md)
- [API Requests](./docs/api-requests.md)
- [API Fields](./docs/api-fields.md)
- [System Access](./docs/system-access.md)
- [Environments](./docs/environments.md)
- [Microservices Design](./docs/microservices.md)
- [Database Design](./docs/databases.md)
- [Security Design](./docs/security.md)
- [Testing Strategy](./docs/testing.md)
- [Observability and Centralized Logging](./docs/observability.md)
- [Image and Asset Storage](./docs/image-storage.md)
- [Implementation Roadmap](./docs/implementation-roadmap.md)

## Tests

Docker is required because persistence integration tests use PostgreSQL 16
Testcontainers:

```bash
./mvnw test
```

With the required develop services running, execute the register-to-transfer
flow with:

```bash
scripts/run-onboarding-transfer-e2e.sh
```
