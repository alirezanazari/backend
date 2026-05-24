# red-bank Backend

Digital banking backend.

This repository is designed as real microservices from the start, with production-grade boundaries, security, financial consistency, and operational concerns documented before implementation.

## Project Structure

```text
red-bank
  contract   shared API/value contracts only
  api-gateway          public HTTP boundary and routing
  iam-service          identity/authentication foundation
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
- [Implementation Roadmap](./docs/implementation-roadmap.md)
