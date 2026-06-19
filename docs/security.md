# Security Design

## Security Principles

This is a banking-style backend, so security must be designed from the first day.

Important rules:

- Use HTTPS/TLS in real environments.
- Never store plaintext passwords.
- Never store raw OTP values.
- Never store raw card numbers.
- Do not invent cryptography.
- Use short-lived access tokens.
- Do not issue refresh tokens.
- Return `401 Unauthorized` for missing, invalid, or expired tokens.
- Require idempotency for financial write operations.
- Audit sensitive actions.
- Validate request `context` against JWT, service data, and server-derived metadata.

For token access levels, admin/internal APIs, debug token generation, and mock OTP rules, see [System Access](./system-access.md).

For `develop` and `prod` configuration, environment variables, deployment, and profile switching, see [Environments](./environments.md).

For production readiness gates, backup/restore, deployment rollback, SLOs, and
release security review, see [Production Readiness](./production-readiness.md).

## Password Handling

Recommended:

```text
client sends password over HTTPS
server hashes password using Argon2id or BCrypt
server stores only password hash
```

Do not store encrypted passwords. Encryption is reversible; password hashing is intentionally one-way.

Preferred password hash:

```text
Argon2id
```

Good fallback:

```text
BCrypt
```

## Gateway Encryption and Key Exchange

App-level request encryption, if enabled, is owned by `api-gateway`.

Microservices must not perform request encryption or decryption. Downstream services receive raw decrypted values from the gateway over trusted internal service communication.

Important architecture advice:

```text
field-level encryption can be added, but it must not replace HTTPS.
```

Recommended design if app-level encryption is required:

```text
POST /api/v1/security/key-exchange
```

Request:

```json
{
  "clientPublicKey": "..."
}
```

Response:

```json
{
  "keyExchangeId": "...",
  "serverPublicKey": "...",
  "expiresAt": "..."
}
```

Concept:

```text
client and api-gateway perform public-key exchange
both derive the same temporary session key
client encrypts sensitive fields with the gateway-approved crypto configuration
api-gateway decrypts only in memory
api-gateway forwards raw values to internal services
microservices never decrypt request payloads
session expires quickly
```

Implemented transport:

```text
key agreement: X25519
client/server public-key encoding: Base64 of X.509 SubjectPublicKeyInfo bytes
key derivation: HKDF-SHA-256
HKDF salt: UTF-8 keyExchangeId
HKDF info: red-bank-gateway-app-encryption-v1
payload encryption: AES-256-GCM
nonce: 12 random bytes, never reused with the same session key
additional authenticated data: UTF-8 keyExchangeId
```

When a client encrypts a public request, it sends:

```text
X-Key-Exchange-Id: value returned by key exchange
X-Encryption-Nonce: Base64 12-byte AES-GCM nonce
Content-Type: text/plain
body: Base64 AES-GCM ciphertext of the complete normal JSON request envelope
```

The gateway:

1. Rejects unknown or expired sessions.
2. Authenticates and decrypts the payload in memory.
3. Removes the encryption transport headers.
4. Replaces the request body with the raw JSON envelope.
5. Forwards `Content-Type: application/json` to the owning microservice.

Unencrypted requests remain supported when app-level encryption is enabled so
non-sensitive endpoints and controlled migrations can continue to work. HTTPS
is mandatory in production in both modes.

Do not use simple random string concatenation as a secret key. It may look secure but usually is not.

## Gateway Rate Limiting

`api-gateway` enforces rate limits for gateway API routes before
authentication. Unauthenticated/public requests are keyed by route and client
address. Bearer-token requests are keyed by token hash so authenticated users do
not share a bucket only because they use the same network.

Production rate-limit thresholds are configured through
`REDBANK_GATEWAY_RATE_LIMIT_*` variables. Multi-instance deployments should add
a shared ingress or infrastructure limiter when strict global limits are
required.

## Login

Login input:

```text
username
password
device context
```

If app-level encryption is enabled, the gateway decrypts encrypted fields before forwarding the request to `iam-service`.

Login output:

```text
access token
account completion status
active profile
device verification status
```

If device is not verified, return a limited token and require OTP verification.

## JWT Strategy

Recommended:

```text
access token: short lived, for API requests
no refresh token
expired token requires login again
```

Use asymmetric signing:

```text
ES256 or RS256
```

The current service and gateway implementation accepts HS256 through
`REDBANK_JWT_SIGNING_SECRET` for compatibility. Treat this as a transitional
implementation detail; the production target remains asymmetric signing so
services can verify tokens without sharing signing capability.

Reason:

```text
IAM signs token with private key
other services validate token with public key
services do not need IAM database access
```

If the token is missing, invalid, expired, malformed, or signed by an unknown key, services must return:

```text
401 Unauthorized
```

The client must send the user through login again after token expiration.

Suggested access token claims:

```text
sub: user id
profile_id: active profile id
device_id: verified device id
scope: allowed actions
roles: user/admin/system
jti: token id
iat: issued at
exp: expiration time
```

Do not put sensitive data in JWT:

```text
password
national id
full phone number
card number
secret keys
```

## Account Completion Status

Suggested enum:

```text
REGISTERED
DEVICE_VERIFICATION_REQUIRED
ACCOUNT_REQUIRED
ACCOUNT_RE_OPEN_REQUIRED
KYC_REQUIRED
KYC_PENDING
COMPLETED
SUSPENDED
```

This status tells the client what screen to show next.

## Device Verification

Use 6-digit OTP:

```text
123456
```

Security rules:

- OTP expires quickly.
- OTP is stored hashed.
- Attempt count is limited.
- Device info is recorded.
- Verified devices can receive normal access.
- Unverified devices receive limited access.

Device info examples:

```text
device fingerprint
device name
operating system
app version
ip address
user agent
```

## Authorization

Authentication answers:

```text
who are you?
```

Authorization answers:

```text
are you allowed to do this?
```

Each service must check ownership. Example:

```text
user can only close accounts that belong to their active profile
user can only freeze their own card
user can only transfer from their own active account/card
```

## Context Trust

Every public business request contains `context`, but client-provided context is not automatically trusted.

Trusted values:

```text
JWT subject and claims after signature validation
request ID generated or normalized by API Gateway
server-derived IP address
verified device record from iam-service
ownership data from account-service
```

Untrusted or partially trusted client signals:

```text
isRooted
language
theme
client-reported IP address
device model
OS version
app version
```

Use client-provided device, app, OS, and network values for audit, risk checks, compatibility, analytics, and debugging. Do not use them alone for authorization or financial permission.

## Service-to-Service Security

Later production options:

- Private network between services
- API Gateway for public traffic
- Service tokens for internal calls
- mTLS for service-to-service communication

For early implementation:

```text
validate JWT in every public service
use internal endpoints only between services
protect internal endpoints with service token later
```

## Audit Requirements

Audit these actions:

- Login success and failure
- Device verification
- Password changes
- Account creation and close
- Card order, freeze, block, expire
- KYC completion
- Referral reward
- Feature flag updates
- Transfer creation and final status
- Ledger transaction posting

Audit logs should include:

```text
who
what
when
from where
correlation id
before and after status when useful
```

Operational logs are not the authoritative business audit store. Sensitive
business audit records remain in service-owned append-only tables or events. See
[Observability and Centralized Logging](./observability.md) and
[OpenSearch Observability Platform](./opensearch-platform.md).
