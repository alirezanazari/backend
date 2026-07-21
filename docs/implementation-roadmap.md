# Implementation Roadmap

This roadmap is intentionally small-step. The goal is to build a real production backend in a controlled sequence, with each dependency introduced before another service relies on it.

## Phase 0: Workspace Preparation

Topic:

```text
prepare develop/prod environment foundation
```

Tasks:

- Verify Java 21
- Verify Docker Desktop and Docker CLI
- Choose Maven Wrapper for all services
- Create repository structure
- Create `contract` module for shared API/value contracts
- Add root README and docs
- Add root Docker Compose
- Add shared local PostgreSQL containers
- Define `develop` and `prod` profiles
- Define environment variable strategy
- Create environment example files

Small steps:

1. Open project in IntelliJ IDEA.
2. Confirm Java SDK is set to Java 21.
3. Start Docker Desktop.
4. Run `docker --version`.
5. Create root Git repository.
6. Create service folders one by one.
7. Create `contract` module.
8. Create first Spring Boot service with Maven Wrapper.
9. Create `application.yml`, `application-develop.yml`, and `application-prod.yml`.
10. Create `.env.develop.example` and `.env.prod.example`.
11. Run first service with `SPRING_PROFILES_ACTIVE=develop`.
12. Verify prod profile refuses unsafe debug config.
13. Call health endpoint from browser/Postman.

Engineering focus:

- What a repository is
- What Maven Wrapper does
- Why Docker Compose helps local development
- How Spring profiles switch configuration
- Why secrets come from environment variables
- How Spring Boot starts
- What an actuator health endpoint means

## Phase 1: Common Service Foundation

Topic:

```text
standard structure for every service and gateway
```

Tasks:

- Create API gateway foundation
- Add `contract` as dependency where public contracts are needed
- Define package structure
- Add standard `ApiRequest<T>` and `ApiResponse<T>` envelope
- Add `context` envelope for both request and response
- Add `status` object with `StatusCode` enum
- Add global failure response format through `context.status`
- Add validation
- Add correlation ID
- Add gateway key-exchange and decryption boundary if app-level encryption is enabled
- Add environment profile rules and debug safety guards
- Add logging pattern
- Add OpenAPI
- Add health checks

Small steps:

1. Create `api-gateway` foundation.
2. Create `iam-service` foundation.
3. Add Spring Web.
4. Add Spring Validation.
5. Add Spring Actuator.
6. Add OpenAPI.
7. Create `GET /actuator/health`.
8. Create request-side `context` DTO in `contract`.
9. Create response-side `context` DTO in `contract`.
10. Create generic `ApiRequest<T>` wrapper in `contract`.
11. Create generic `ApiResponse<T>` wrapper in `contract`.
12. Create global exception handler.
13. Create standard failure response with `context.status`.
14. Create request validation example.
15. Validate request ID, account, OS, app, device, and network context.
16. Implement gateway route to IAM.
17. Implement `POST /api/v1/security/key-exchange` in gateway if app-level encryption is enabled.
18. Decrypt encrypted request fields in gateway memory.
19. Forward raw values to internal services.
20. Add debug startup guard for `prod`.
21. Add develop-only debug token generator under `/debug/v1/**`.
22. Test with Postman through gateway.

Engineering focus:

- Controller vs service vs repository
- DTO validation
- API envelope design
- Request context validation
- Gateway-owned request decryption
- Safe develop debug access
- HTTP status codes
- Why error formats should be consistent
- Why logs need correlation IDs

## Phase 2: IAM Service

Topic:

```text
identity, registration, login, device verification
```

Tasks:

- Create IAM database
- Add Flyway migrations
- Implement user registration
- Store password hash
- Implement login
- Implement JWT generation
- Implement device OTP

Small steps:

1. Create `iam_db` in Docker Compose.
2. Add Flyway to `iam-service`.
3. Create `users` table.
4. Create `credentials` table.
5. Implement `POST /api/v1/iam/register`.
6. Validate username, phone number, national ID, password, name, province, city, and address.
7. Accept selected `jobId` as onboarding data.
8. Hash password.
9. Return limited JWT and `ACCOUNT_REQUIRED`.
10. Create `devices` table.
11. Create `otp_challenges` table.
12. Implement `POST /api/v1/iam/login`.
13. If device is not verified, return `DEVICE_VERIFICATION_REQUIRED`.
14. Implement `POST /api/v1/iam/device-verify`.
15. Return `401 Unauthorized` for missing, invalid, or expired access tokens.
16. Add develop-only mock OTP support for `111111`.
17. Add startup guard so mock OTP cannot be enabled in `prod`.
18. Add unit tests for password and device rules.
19. Add integration tests for register and login.

Engineering focus:

- Password hashing
- JWT access tokens
- Token expiration and `401 Unauthorized`
- Why OTP should be hashed
- Why token claims should be minimal
- Why mock OTP must be develop-only
- Why request encryption belongs at the gateway boundary

## Phase 3: Account Service

Topic:

```text
profiles and account metadata
```

Tasks:

- Create account database
- Implement profile model
- Implement account model
- Create default personal profile
- Create default saving account
- Enforce max 5 profiles
- Implement account list
- Implement profile list
- Implement owner lookup
- Implement account close

Small steps:

1. Create `account-service`.
2. Add `account_db`.
3. Add Flyway.
4. Create `profiles` table.
5. Create `bank_accounts` table.
6. Implement `POST /api/v1/account/register`.
7. Create personal profile automatically.
8. Validate selected `jobId` exists in KYC job list.
9. Save selected `jobId` on created profile/account record.
10. Create default saving account automatically.
11. Auto-generate 18-digit account number.
12. Auto-generate IBAN.
13. Return profile and account response.
14. Implement `POST /api/v1/profile/create`.
15. Implement `POST /api/v1/profile/list`.
16. Implement `POST /api/v1/profile/edit`.
17. Implement `POST /api/v1/profile/active/set`.
18. Implement `POST /api/v1/account/list`.
19. Implement `POST /api/v1/account/balance/get`.
20. Implement `POST /api/v1/account/owner/get` as client-facing destination lookup endpoint.
21. Implement `POST /api/v1/account/close`.
22. Implement `POST /api/v1/account/open` for closed account/profile reopen.
23. Prevent closing account if ledger balance is not zero.
24. Add tests for max profile count.
25. Add tests for account close, reopen, and profile edit rules.

Engineering focus:

- Domain rules
- Transaction boundaries
- Ownership checks
- Why account metadata and money balance are separate

## Phase 4: Ledger Service

Topic:

```text
financial source of truth
```

Tasks:

- Create ledger database
- Create ledger accounts
- Create double-entry ledger transactions
- Maintain balances safely
- Add internal APIs for posting transfer and referral reward

Small steps:

1. Create `ledger-service`.
2. Add `ledger_db`.
3. Create `ledger_accounts` table.
4. Create `ledger_transactions` table.
5. Create `ledger_entries` table.
6. Create `ledger_balances` table.
7. Implement ledger account creation.
8. Create ledger account when account-service creates bank account.
9. Implement internal transfer posting.
10. Validate debit equals credit.
11. Update balances transactionally.
12. Add optimistic locking on balance rows.
13. Add tests for successful transfer.
14. Add tests for insufficient funds.
15. Add tests for duplicate ledger reference.

Engineering focus:

- Why money systems need a ledger
- Double-entry accounting basics
- Database transactions
- Optimistic locking
- Idempotency in financial systems

## Phase 5: Card Service

Topic:

```text
card lifecycle
```

Tasks:

- Create card database
- Create default card after account registration
- Implement card list
- Implement order
- Implement freeze/unfreeze
- Implement expire-card

Small steps:

1. Create `card-service`.
2. Add `card_db`.
3. Create `cards` table.
4. Create `card_orders` table.
5. Create `card_status_history` table.
6. Implement internal default card creation.
7. Implement `POST /api/v1/card/list`.
8. Implement `POST /api/v1/card/order`.
9. Implement `POST /api/v1/card/freeze`.
10. Implement `POST /api/v1/card/unfreeze`.
11. Implement `POST /api/v1/card/block`.
12. Implement `POST /api/v1/card/unblock`.
13. Implement `POST /api/v1/card/expire`.
14. Add tests for status transitions.

Engineering focus:

- State machines
- User action vs system/admin action
- Why raw card numbers are sensitive

## Phase 6: KYC Service

Topic:

```text
KYC and referral
```

Tasks:

- Create KYC database
- Implement job list
- Implement referral set
- Implement KYC status
- Implement KYC completion
- Trigger referral reward after KYC approval

Small steps:

1. Create `kyc-service`.
2. Add `kyc_db`.
3. Create `jobs` table.
4. Seed sample jobs with Flyway.
5. Implement `POST /api/v1/kyc/jobs/list`.
6. Create `kyc_cases` table.
7. Create `referral_codes` table and generate one code after KYC approval.
8. Create `referrals` table.
9. Implement `POST /api/v1/kyc/referral/set`.
10. Resolve entered codes from the KYC-owned referral code registry.
11. Make referral immutable after registration flow.
12. Implement `POST /api/v1/kyc/status/get`.
13. Implement temporary admin/test endpoint to approve KYC.
14. On KYC approved, request referral reward.
15. Reward only if referrer has active account.
16. Reward only once.
17. Add tests for referral rules.

Engineering focus:

- Workflow status
- Immutable business decisions
- One-time reward logic
- Cross-service validation

## Phase 7: Feature Flag Service

Topic:

```text
runtime behavior control
```

Tasks:

- Create feature flag database
- Implement flag list
- Implement update flag
- Add audit log
- Use flags from transfer/card/referral flows

Small steps:

1. Create `feature-flag-service`.
2. Add `feature_flag_db`.
3. Create `feature_flags` table.
4. Create `feature_flag_audit_logs` table.
5. Seed initial flags.
6. Implement `POST /api/v1/feature-flags/list`.
7. Implement `POST /api/v1/feature-flags/update`.
8. Add admin authorization for mutation APIs.
9. Use flag in transfer-service.
10. Use flag in card-service.
11. Use flag in KYC referral reward.
12. Add client-visible subject evaluation.
13. Add explicit user allow/deny overrides.
14. Add ordered rules for profile, account, currency, job, and balance.
15. Load evaluation attributes from account-service.
16. Audit targeting replacements.

Engineering focus:

- Operational controls
- Admin-only APIs
- Audit logging
- Deterministic override and rule precedence
- Trusted targeting context
- Avoiding redeploys for simple switches

## Phase 8: Transfer Service

Topic:

```text
money movement orchestration
```

Tasks:

- Create transfer database
- Implement method list
- Implement create-transfer
- Add idempotency
- Call feature flags
- Validate source and destination
- Call ledger
- Store successful recent list

Small steps:

1. Create `transfer-service`.
2. Add `transfer_db`.
3. Create `transfer_methods` table.
4. Seed transfer methods.
5. Implement `POST /api/v1/transfers/methods/list`.
6. Create `transfers` table.
7. Create `idempotency_keys` table.
8. Implement `POST /api/v1/transfers/create`.
9. Require `Idempotency-Key` header.
10. Check method enabled from feature-flag-service.
11. Validate source ownership through account-service/card-service.
12. Validate destination exists for internal transfer.
13. Create transfer as `PENDING`.
14. Call ledger to post transaction.
15. Mark transfer `SUCCESS` or `FAILED`.
16. Implement `POST /api/v1/transfers/recent/list`.
17. Return only successful transfers.
18. Add tests for retry with same idempotency key.
19. Add tests for insufficient funds.
20. Add tests for disabled transfer method.

Engineering focus:

- Idempotency
- Distributed transactions problem
- Saga-like orchestration
- Why retries are dangerous without design

## Phase 9: PFM Service

Topic:

```text
personal finance and boxes
```

Tasks:

- Create PFM database
- Implement box presets
- Implement box create
- Implement box list
- Implement box edit
- Implement box close
- Implement transaction list

Small steps:

1. Create `pfm-service`.
2. Add `pfm_db`.
3. Create `box_presets` table.
4. Seed presets.
5. Implement `POST /api/v1/pfm/box-presets/list`.
6. Create `boxes` table.
7. Implement `POST /api/v1/pfm/boxes/create`.
8. On box create, ask account-service to create VAULT account.
9. Ask ledger-service to create ledger account.
10. Store box metadata.
11. Implement `POST /api/v1/pfm/boxes/list`.
12. Implement `POST /api/v1/pfm/boxes/edit`.
13. Implement `POST /api/v1/pfm/boxes/close` with a zero-balance check.
14. Implement `POST /api/v1/pfm/transactions/list`.
15. Read transaction information from ledger or a read model.
16. Add tests for box-account relation.

Engineering focus:

- Read model vs source of truth
- User-facing model vs financial model
- Coordinating multiple services safely

## Phase 10: API Gateway Hardening

Topic:

```text
production public entry point hardening
```

Tasks:

- Complete routing to all services
- Verify app-level key exchange if enabled
- Verify request payload decryption before forwarding
- Validate JWT at gateway
- Add correlation ID
- Add rate limiting later

Small steps:

1. Confirm `api-gateway` is the only public entry point.
2. Configure routes for every service.
3. Route `/api/v1/iam/**` to IAM.
4. Route `/api/v1/account/**` to account-service.
5. Route `/api/v1/card/**` to card-service.
6. Route `/api/v1/kyc/**` to kyc-service.
7. Route `/api/v1/pfm/**` to pfm-service.
8. Route `/api/v1/transfers/**` to transfer-service.
9. Verify `POST /api/v1/security/key-exchange` if app-level encryption is enabled.
10. Verify encrypted request fields are decrypted only in gateway memory.
11. Verify raw values are forwarded to internal services.
12. Add JWT validation.
13. Forward user identity headers carefully only after validation.
14. Add request correlation ID.
15. Test all APIs through gateway.

Engineering focus:

- API Gateway pattern
- Edge security
- Routing
- Why services still need authorization checks

## Phase 11: Events and Reliability

Topic:

```text
make service communication reliable
```

Tasks:

- Add outbox tables
- Add inbox tables
- Add local event publisher
- Add message broker later
- Add retry handling

Small steps:

1. Add outbox table to IAM.
2. Add outbox table to account-service.
3. Add outbox table to ledger-service.
4. Create event publisher job.
5. Publish events after database commits.
6. Add inbox table to consumers.
7. Ignore duplicate events safely.
8. Add retry count.
9. Add dead-letter status.
10. Later replace simple publisher with Kafka or RabbitMQ.

Implemented baseline:

- IAM writes `USER_REGISTERED` and `DEVICE_VERIFIED` facts to its transactional
  outbox.
- Account writes ledger/card delivery requests to its transactional outbox and
  dispatches them in sequence with bounded retries.
- Ledger writes account lifecycle and posted-transaction facts to its
  transactional outbox.
- IAM and ledger use a scheduled in-process publisher. This preserves the
  polling, retry, and dead-letter boundary while no broker is configured.
- Account deliveries include stable `X-Event-Id` and `X-Event-Type` headers.
- Ledger and card claim delivered event IDs in transactional inbox tables,
  store the successful response, and return that response for duplicate
  deliveries without repeating the business mutation.
- Outbox delivery moves from `PENDING` to `PUBLISHED`, or to `DEAD_LETTER`
  after the retry limit.

Exit criteria:

- Business state and its outbox event commit or roll back together.
- A repeated account lifecycle/default-card delivery creates one inbox record
  and one business result.
- Failed publication increments `retry_count` and eventually becomes
  `DEAD_LETTER`.
- Replacing the in-process publisher with Kafka or RabbitMQ does not require
  changing producer transaction boundaries or consumer idempotency rules.

Engineering focus:

- Why events get lost
- Transactional outbox
- Idempotent consumers
- Retry and dead-letter design

## Phase 12: Testing Strategy

Topic:

```text
confidence before production
```

Tasks:

- Unit tests
- Integration tests
- Contract tests
- End-to-end tests
- Postman collection

Small steps:

1. Write unit tests for domain rules.
2. Write integration tests with PostgreSQL container.
3. Test IAM register and login.
4. Test account register.
5. Test ledger transfer.
6. Test transfer idempotency.
7. Test referral reward.
8. Create Postman collection.
9. Run full local flow from register to transfer.

Implemented baseline:

- Unit tests cover IAM validation and OTP rules, ledger posting rules, gateway
  security, event dispatch, and service HTTP clients.
- Every database-owning service runs controller integration tests against a
  PostgreSQL 16 Testcontainer with its real Flyway migrations.
- IAM integration tests cover registration, login, device verification,
  password hashing, and account-completion token behavior.
- Account integration tests cover account registration and lifecycle rules.
- Ledger integration tests cover double-entry transfers, insufficient funds,
  referral rewards, transfer idempotency, inbox deduplication, and concurrent
  balance updates.
- KYC integration tests cover immutable referrals and one-time referral reward.
- Transfer integration tests cover orchestration idempotency, replayed failures,
  feature flags, and recent destinations.
- Service HTTP client tests act as consumer contract tests and are paired with
  provider controller integration tests.
- The broad Postman collection documents all implemented APIs.
- A focused Newman collection runs the full local flow from user
  registration through an idempotent account-to-account transfer and verifies
  final ledger balances.

Exit criteria:

- `./mvnw test` passes with Docker available.
- Flyway migrations and repository queries are exercised on PostgreSQL rather
  than an emulated database dialect.
- IAM register/login, account register, ledger transfer, transfer idempotency,
  and referral reward have automated integration coverage.
- Changed internal HTTP contracts have both consumer and provider coverage.
- `scripts/run-onboarding-transfer-e2e.sh` passes against the required local develop
  services.

Engineering focus:

- Test pyramid
- Why database tests matter
- Why financial systems need edge-case tests

## Phase 13: Observability Platform

Topic:

```text
centralized logs, metrics, traces, dashboards, and alerts
```

Tasks:

- Structured ECS JSON logs
- OpenTelemetry metrics and distributed tracing
- OpenSearch ingestion, storage, dashboards, and alerting
- Separate develop and production environments
- Retention, security, backup, and recovery controls

### Stage 13.1: Application Telemetry Contract

1. Define required log fields and blocked sensitive fields.
2. Enable Spring Boot ECS JSON console logging for deployed environments.
3. Keep human-readable logs as the developer-machine default.
4. Replace repetitive request logs with one structured completion event.
5. Normalize `X-Correlation-Id` at the API Gateway.
6. Return correlation and request IDs in controlled error responses.
7. Add CI tests that reject passwords, OTPs, tokens, card data, account data,
   and national IDs in logs.

Exit criteria:

- Every service emits valid structured JSON in its deployed profile.
- A request can be found by correlation ID.
- Sensitive-value logging tests pass.

### Stage 13.2: Shared Develop OpenSearch

1. Add an optional local/develop Compose profile for OpenSearch and Dashboards.
2. Add Fluent Bit for container standard-output collection.
3. Add OpenSearch Data Prepper for parsing, redaction, and routing.
4. Create version-controlled index templates and mappings.
5. Create develop rollover and retention policies.
6. Add dead-letter handling for invalid telemetry.
7. Ingest one service first, verify the pipeline, then roll through all services.

Exit criteria:

- Every shared develop service is searchable in OpenSearch Dashboards.
- Invalid or sensitive events are rejected or redacted.
- Rollover and deletion policies execute successfully.

### Stage 13.3: Traces and Metrics

1. Add OpenTelemetry Collector.
2. Add Micrometer Tracing with OpenTelemetry.
3. Export service metrics and traces through OTLP.
4. Propagate W3C trace context through the gateway and every HTTP client.
5. Fix Reactor context propagation in the API Gateway.
6. Add context propagation to scheduled jobs, async executors, and outbox
   dispatchers.
7. Add JVM, HTTP, database pool, outbound HTTP, outbox, transfer, and ledger
   metrics.

Exit criteria:

- A gateway request and its downstream calls appear in one distributed trace.
- Logs can be correlated with trace and span IDs.
- Required service and business-operation metrics are visible.

### Stage 13.4: Dashboards and Alerts

1. Create service request-rate, error-rate, and latency dashboards.
2. Create JVM, database pool, and outbound dependency dashboards.
3. Create authentication, outbox, ledger, and transfer dashboards.
4. Create OpenSearch, Data Prepper, and collector health dashboards.
5. Add alerts for HTTP failures, latency, database exhaustion, outbox backlog,
   ingestion failure, cluster health, disk watermarks, and failed snapshots.
6. Export dashboards and monitors into version control.

Exit criteria:

- Dashboards cover every production service and the observability platform.
- Alerts are verified through controlled failure injection.
- Dashboard and alert definitions can be recreated from version control.

### Stage 13.5: Production OpenSearch

1. Provision a separate production OpenSearch cluster with at least three
   cluster-manager-eligible nodes.
2. Configure internal networking and TLS for every platform connection.
3. Create least-privilege ingestion, reader, support, administrator, and
   operator roles.
4. Configure production index templates, rollover, and retention.
5. Configure an external encrypted snapshot repository.
6. Mirror pinned OpenSearch, Dashboards, Data Prepper, collector, and Fluent Bit
   images into an internal registry.
7. Run capacity, node-failure, ingestion-backpressure, snapshot-restore, and
   upgrade tests.
8. Document access approval, incident handling, restore, and upgrade runbooks.

Exit criteria:

- Develop and production share no credentials, certificates, indexes, or
  snapshots.
- Production APIs are not publicly reachable.
- One-node failure does not stop ingestion.
- A production-like snapshot restore succeeds.
- Recovery artifacts are available from the internal registry.

Engineering focus:

- Logs vs durable business audit
- Structured telemetry
- Cross-thread and cross-service context propagation
- Retention, cost, and access control
- Alert quality

Detailed design:

- [Observability and Centralized Logging](./observability.md)
- [OpenSearch Observability Platform](./opensearch-platform.md)

## Phase 14: Production Readiness

Topic:

```text
prepare for controlled production deployment
```

Tasks:

- Secrets
- CI pipeline
- Security hardening
- Backup strategy
- Disaster recovery
- Operational runbooks

Small steps:

1. Verify Phase 13 observability acceptance criteria.
2. Move secrets out of config files.
3. Add CI build.
4. Add test step in CI.
5. Add Docker image build.
6. Add dependency and container vulnerability scanning.
7. Add database backup procedures.
8. Test restore and disaster-recovery procedures.
9. Add rate limiting.
10. Add security review checklist.
11. Add deployment rollback and database migration runbooks.
12. Define service SLOs and on-call ownership.

Implemented artifacts:

- [Production Readiness](./production-readiness.md)
- [Database Backup and Restore Runbook](./runbooks/database-backup-restore.md)
- [Disaster Recovery Runbook](./runbooks/disaster-recovery.md)
- [Deployment Rollback and Migration Runbook](./runbooks/deployment-rollback-and-migrations.md)
- [Production Security Checklist](./runbooks/production-security-checklist.md)
- [Service SLOs and On-Call Ownership](./runbooks/service-slos-oncall.md)
- CI and vulnerability scanning workflow in `.github/workflows/ci.yml`
- Runtime service image build in `docker/app/Dockerfile`
- API gateway rate limiting through `REDBANK_GATEWAY_RATE_LIMIT_*`

Engineering focus:

- CI/CD
- Configuration management
- Operational safety
- Backup and recovery
- SLO and incident readiness

## Phase 15: Image and Asset Storage

Topic:

```text
backend-managed light and dark assets through MinIO
```

Tasks:

- Add MinIO to the develop Docker Compose environment
- Add persistent object-storage volume
- Add MinIO Console for backend developer uploads
- Create environment-isolated buckets
- Store stable object keys instead of complete URLs
- Add profile default images
- Resolve PFM box and vault images through box presets
- Serve PNG, SVG, and iOS PDF visual assets
- Keep public object reads for the initial implementation
- Prepare the resolver for future 15-minute presigned URLs
- Add reproducible required-asset bootstrap and backup guidance

Small steps:

1. Pin the selected MinIO container version.
2. Add the MinIO server and persistent volume to develop Docker Compose.
3. Add develop-only root credentials through environment variables.
4. Expose the MinIO S3 API and Console on local ports.
5. Create `redbank-assets-develop` through an idempotent bootstrap process.
6. Apply anonymous object-read access without anonymous list or write access.
7. Create a manifest for required profile and box-preset assets.
8. Bootstrap every required light and dark asset with content-type and cache
   metadata.
9. Add asset endpoint, bucket, URL mode, and TTL configuration.
10. Add a small S3-compatible URL resolver shared as an implementation pattern
    by services that return images.
11. Migrate profile image persistence from complete URLs to stable object keys.
12. Configure default profile light and dark keys.
13. Migrate box-preset image persistence from complete URLs to stable object
    keys.
14. Resolve a box image through its `preset_id`.
15. Resolve a related `VAULT` account image through its box preset.
16. Keep `bank_accounts` free of independent image fields.
17. Preserve the public `Image(lightUrl, darkUrl, alt)` response contract.
18. Verify PNG, SVG, and PDF assets with their intended clients.
19. Add tests for defaults, preset resolution, URL generation, content types,
    persistence, and access policy.
20. Document the later switch from public URLs to private 15-minute presigned
    URLs.
21. Define production bucket isolation, TLS, backup, restore, monitoring, and
    supported-version review before production launch.

Exit criteria:

- MinIO and its Console run in the develop environment.
- Required assets survive a container restart.
- Fresh environments receive the required bucket and default assets.
- Profiles use backend-configured default object keys.
- Box and vault images resolve through box presets.
- Database rows contain object keys, not environment-specific or signed URLs.
- Anonymous clients can read approved assets but cannot upload or delete them.
- The existing `Image` API returns working light and dark URLs for PNG, SVG,
  and PDF assets.
- No user-facing binary upload endpoint exists.
- Private 15-minute URL mode can be introduced without changing stored keys.

Engineering focus:

- Object storage and S3 concepts
- Stable object identity vs temporary delivery URL
- Environment isolation
- Cache-safe immutable naming
- Least-privilege storage access
- Backup and restore across database and object storage

Detailed design:

- [Image and Asset Storage](./image-storage.md)

## Recommended Implementation Order

Build in this exact order:

```text
1. api-gateway foundation
2. iam-service foundation
3. account-service
4. ledger-service
5. card-service
6. kyc-service
7. feature-flag-service
8. transfer-service
9. pfm-service
10. api-gateway hardening
11. events and reliability
12. testing strategy
13. observability platform
14. production readiness
15. image and asset storage
```

Reason:

```text
Gateway protects the public boundary and owns request decryption.
IAM gives identity.
Account gives ownership.
Ledger gives safe money movement.
Feature flags give operational control before risky flows.
Transfer needs IAM, account, ledger, card, KYC, and feature flags.
PFM depends on account and ledger.
Observability is implemented before production readiness is declared.
Image and asset storage is kept as the final planned phase and introduces
MinIO without changing business-service ownership boundaries.
```

## First Real Implementation Milestone

Milestone:

```text
user can register, receive limited token, create default personal profile, create default saving account, and receive default card.
```

Services involved:

```text
iam-service
account-service
card-service
ledger-service
```

Smallest useful flow:

1. Register user in IAM.
2. Return token with `ACCOUNT_REQUIRED`.
3. Call account register.
4. Create personal profile.
5. Create saving account.
6. Create ledger account.
7. Create default card.
8. Return `KYC_REQUIRED`.

This gives us a real vertical slice without implementing the entire project at once.
