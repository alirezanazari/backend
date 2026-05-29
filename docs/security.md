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

Do not use simple random string concatenation as a secret key. It may look secure but usually is not.

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
