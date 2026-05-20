# red-bank Backend

Real production digital banking backend.

This repository is designed as real microservices from the start, with production-grade boundaries, security, financial consistency, and operational concerns documented before implementation.

## Documentation

- [Architecture Overview](./docs/architecture.md)
- [API Contract](./docs/api-contract.md)
- [Microservices Design](./docs/microservices.md)
- [Database Design](./docs/databases.md)
- [Security Design](./docs/security.md)
- [Implementation Roadmap](./docs/implementation-roadmap.md)

## Current Architectural Direction

The system is a banking-style backend with separate services for identity, accounts, cards, KYC, personal finance, transfers, feature flags, and ledger.

Implementation will start small, but the boundaries are designed so the project can grow without being rewritten.
