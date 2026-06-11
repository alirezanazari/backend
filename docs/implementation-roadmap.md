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
13. Implement `POST /api/v1/pfm/transactions/list`.
14. Read transaction information from ledger or a read model.
15. Add tests for box-account relation.

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

Engineering focus:

- Test pyramid
- Why database tests matter
- Why financial systems need edge-case tests

## Phase 13: Production Readiness

Topic:

```text
prepare for real deployment thinking
```

Tasks:

- Centralized logging
- Metrics
- Tracing
- Secrets
- CI pipeline
- Security hardening
- Backup strategy

Small steps:

1. Add structured JSON logs.
2. Add metrics with Actuator.
3. Add distributed trace IDs.
4. Move secrets out of config files.
5. Add CI build.
6. Add test step in CI.
7. Add Docker image build.
8. Add database backup notes.
9. Add rate limiting.
10. Add security review checklist.

Engineering focus:

- Observability
- CI/CD
- Configuration management
- Operational safety

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
