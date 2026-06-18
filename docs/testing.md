# Testing Strategy

This project uses a test pyramid that keeps domain feedback fast while exercising
financial and persistence behavior against the production database engine.

## Test Layers

### Unit Tests

Unit tests cover rules that do not require Spring or a database:

- IAM registration validation and OTP expiry, hashing, and attempt limits
- Ledger posting validation
- Gateway access, JWT, encryption, key exchange, routing, and correlation rules
- Outbox dispatcher retry and delivery behavior
- HTTP client error mapping and request construction

Run a single unit test:

```bash
./mvnw -pl iam-service -am -Dtest=RegistrationValidatorTest test
```

### Integration Tests

Every database-owning service runs its controller integration suite against a
PostgreSQL 16 Testcontainer. Spring Boot supplies the container JDBC connection,
and Flyway creates the schema before the tests run.

The suites cover:

- IAM registration, login, device verification, credential hashing, and outbox
- Account registration, profile/account rules, account lifecycle, and outbox
- Ledger account lifecycle, double-entry transfers, referral rewards,
  idempotency, inbox deduplication, insufficient funds, and concurrent posting
- Card lifecycle and inbox deduplication
- KYC, immutable referrals, and one-time referral rewards
- Feature flag evaluation, targeting, overrides, and audit history
- Transfer orchestration, idempotency replay, failures, and recent destinations
- PFM box creation, editing, listing, and ledger-backed transactions

Docker must be available:

```bash
./mvnw test
```

Run one service:

```bash
./mvnw -pl ledger-service -am test
```

### Consumer Contract Tests

Service-to-service HTTP contracts are verified at the consumer boundary with
`MockRestServiceServer`. These tests assert paths, headers, request JSON,
response JSON, and downstream error mapping without starting the provider:

- `AccountOutboxDispatcherTest`
- `KycHttpClientsTest`
- `CardHttpFeatureFlagClientTest`
- `PfmHttpClientsTest`
- `TransferHttpFeatureFlagClientTest`
- `TransferHttpLedgerClientTest`
- `TransferHttpPartyClientTest`

Shared request and response records remain in the `contract` module. Provider
controller integration tests verify that the matching HTTP shapes are accepted
and returned. Any service-to-service field change must update both the consumer
contract test and the provider integration test in the same change.

### End-to-End Tests

The focused Postman collection is:

```text
docs/postman/onboarding-transfer-e2e.postman_collection.json
```

It verifies this flow:

1. Register source user.
2. Register source profile and account.
3. Login and verify the source device with the develop mock OTP.
4. Register destination user, profile, and account.
5. Wait for account outbox delivery to create both ledger accounts.
6. Fund the source through the referral-reward ledger path.
7. Transfer funds through the gateway.
8. Replay the same idempotency key.
9. Assert that source and destination balances moved exactly once.

Required services:

- API Gateway
- IAM
- Account
- Ledger
- Feature Flag
- Transfer

Required develop settings:

```text
SPRING_PROFILES_ACTIVE=develop
REDBANK_DEBUG_MOCK_OTP_ENABLED=true
REDBANK_LEDGER_CLIENT_ENABLED=true
REDBANK_TRANSFER_FEATURE_FLAG_CLIENT_ENABLED=true
REDBANK_TRANSFER_PARTY_CLIENT_ENABLED=true
REDBANK_TRANSFER_LEDGER_CLIENT_ENABLED=true
```

With the services running and either Newman or `npx` available:

```bash
scripts/run-onboarding-transfer-e2e.sh
```

Override local endpoints or the internal token through environment variables:

```bash
GATEWAY_BASE_URL=http://localhost:9080 \
LEDGER_BASE_URL=http://localhost:9083 \
REDBANK_INTERNAL_SERVICE_TOKEN_SECRET=local-secret \
scripts/run-onboarding-transfer-e2e.sh
```

The broader exploratory collection remains available at:

```text
docs/postman/red-bank.postman_collection.json
```

## Financial Test Rules

Financial changes require tests for the normal path and applicable edge cases:

- zero or negative amount
- insufficient funds
- currency mismatch
- source equals destination
- duplicate reference
- same idempotency key with the same request
- same idempotency key with a different request
- concurrent updates to one balance
- transaction rollback when any entry fails
- one debit and one credit with equal amounts

H2 is not used for persistence integration tests because emulation does not
provide enough confidence in PostgreSQL constraints, locking, transaction
behavior, identity columns, or SQL dialect compatibility.

## Pull Request Gate

A change is ready to merge when:

1. Relevant unit tests pass.
2. Relevant PostgreSQL integration tests pass.
3. Consumer and provider contract tests pass for changed internal APIs.
4. `./mvnw test` passes for shared or cross-service changes.
5. The onboarding and transfer E2E collection passes for onboarding, ledger, or transfer
   workflow changes.
