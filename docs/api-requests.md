# API Requests

This document lists the API requests currently planned for `red-bank`.

It defines endpoint purpose, ownership, access, and behavior.

For request and response `data` fields of every API, see [API Fields](./api-fields.md).

For the global envelope, status format, and context rules, see [API Contract](./api-contract.md).

For token, admin, internal, and debug access rules, see [System Access](./system-access.md).

## Requests Summary

| Area | Request | Auth | Summary |
| --- | --- | --- | --- |
| Gateway | `POST /api/v1/security/key-exchange` | Public | Create temporary app-level encryption session with gateway. |
| IAM | `POST /api/v1/iam/register` | Public | Register user identity and initial credentials. |
| IAM | `POST /api/v1/iam/login` | Public | Authenticate user with username and password. |
| IAM | `POST /api/v1/iam/device-verify` | Conditional | Verify device using 6-digit OTP. |
| IAM | `POST /api/v1/iam/logout` | Required | End current client login session. |
| Account | `POST /api/v1/account/register` | Required | Create personal profile and default saving account. |
| Account | `POST /api/v1/account/close` | Required | Close an owned account when allowed. |
| Account | `POST /api/v1/account/open` | Required | Reopen a closed personal account/profile when login requires reopening. |
| Account | `POST /api/v1/account/list` | Required | List accounts for current user/profile. |
| Account | `POST /api/v1/account/balance/get` | Required | Get authoritative account balance through ledger. |
| Account | `POST /api/v1/profile/list` | Required | List profiles related to an owned account number. |
| Account | `POST /api/v1/profile/create` | Required | Create an additional user profile. |
| Account | `POST /api/v1/profile/edit` | Required | Edit profile display name and image. |
| Account | `POST /api/v1/profile/active/set` | Required | Set active profile for current user. |
| Account | `POST /api/v1/account/owner/get` | Required | Lookup non-sensitive transfer destination display info. |
| Account | `POST /internal/v1/account/referral/reward-account/get` | Internal | Find an active account eligible for a referral reward. |
| Card | `POST /api/v1/card/list` | Required | List cards for current user/profile. |
| Card | `POST /api/v1/card/order` | Required | Order a new card. |
| Card | `POST /api/v1/card/freeze` | Required | Temporarily freeze user's card. |
| Card | `POST /api/v1/card/unfreeze` | Required | Unfreeze user's frozen card. |
| Card | `POST /api/v1/card/block` | Required | Block user's card, including for reorder flow. |
| Card | `POST /api/v1/card/unblock` | Admin/Internal | Unblock card by authorized operator/system. |
| Card | `POST /api/v1/card/expire` | Admin/Internal | Mark card as expired. |
| KYC | `POST /api/v1/kyc/jobs/list` | Public | List active jobs for registration. |
| KYC | `POST /api/v1/kyc/referral/set` | Required/Limited | Save referral during registration flow. |
| KYC | `POST /api/v1/kyc/status/get` | Required | Get current user's KYC status. |
| KYC | `POST /api/v1/kyc/complete` | Admin/Internal | Complete or approve KYC case. |
| Feature Flag | `POST /api/v1/feature-flags/list` | Admin/Internal | List runtime feature flags. |
| Feature Flag | `POST /api/v1/feature-flags/update` | Admin | Update feature flag value. |
| PFM | `POST /api/v1/pfm/transactions/list` | Required | List personal finance transactions. |
| PFM | `POST /api/v1/pfm/boxes/list` | Required | List user's boxes. |
| PFM | `POST /api/v1/pfm/boxes/create` | Required | Create a new vault-backed box. |
| PFM | `POST /api/v1/pfm/box-presets/list` | Required | List box creation presets. |
| PFM | `POST /api/v1/pfm/boxes/edit` | Required | Edit box display metadata. |
| Transfer | `POST /api/v1/transfers/create` | Required | Create a money transfer. |
| Transfer | `POST /api/v1/transfers/methods/list` | Required | List available transfer methods. |
| Transfer | `POST /api/v1/transfers/recent/list` | Required | List successful recent transfers. |
| Ledger | `POST /internal/v1/ledger/account/create` | Internal | Create ledger account. |
| Ledger | `POST /internal/v1/ledger/account/close` | Internal | Close a zero-balance ledger account. |
| Ledger | `POST /internal/v1/ledger/account/reopen` | Internal | Reopen a closed ledger account. |
| Ledger | `POST /internal/v1/ledger/transactions/internal-transfer` | Internal | Post double-entry internal transfer. |
| Ledger | `POST /internal/v1/ledger/transactions/referral-reward` | Internal | Post referral reward ledger movement. |
| Ledger | `POST /internal/v1/ledger/account/balance/get` | Internal | Get authoritative ledger balance. |
| Ledger | `POST /internal/v1/ledger/account/entries/list` | Internal | List ledger entries for account. |
| Infrastructure | `GET /actuator/health` | Environment-dependent | Health check. |
| Infrastructure | `GET /actuator/info` | Environment-dependent | Safe service build/runtime information. |

## Global Rules

All public business APIs use:

```text
POST
```

Reason:

```text
every request must carry the standard context body
```

Each public API request uses:

```json
{
  "context": {},
  "data": {}
}
```

Each public API response uses:

```json
{
  "context": {
    "status": {}
  },
  "data": {}
}
```

There is no separate `error` object. Failures are represented through `context.status`.

## Authentication Rules

Public APIs are reached through `api-gateway`.

Token rules:

- Missing token returns `401 Unauthorized`.
- Invalid token returns `401 Unauthorized`.
- Expired token returns `401 Unauthorized`.
- There is no refresh token.
- When token expires, user must login again.

Public unauthenticated APIs:

- `POST /api/v1/security/key-exchange`
- `POST /api/v1/kyc/jobs/list`
- `POST /api/v1/iam/register`
- `POST /api/v1/iam/login`

Conditionally authenticated APIs:

- `POST /api/v1/iam/device-verify`

Authenticated APIs:

- All account APIs
- All card APIs
- All KYC APIs after registration/login except public job list
- All PFM APIs
- All transfer APIs
- All feature flag APIs except future internal/admin exceptions

## Gateway Requests

### POST /api/v1/security/key-exchange

Fields: [See fields](./api-fields.md#post-apiv1securitykey-exchange)

Owner:

```text
api-gateway
```

Purpose:

```text
create a temporary app-level encryption session between client and gateway
```

Authentication:

```text
public
```

Important rules:

- Only gateway owns app-level key exchange.
- Gateway decrypts encrypted request fields in memory.
- Microservices receive raw values.
- Microservices must not implement request encryption or decryption.
- HTTPS/TLS is still required; app-level encryption does not replace TLS.
- Session must expire quickly.
- Simple random string concatenation must not be used as cryptography.

## IAM Requests

### POST /api/v1/iam/register

Fields: [See fields](./api-fields.md#post-apiv1iamregister)

Owner:

```text
iam-service
```

Purpose:

```text
register user identity and create initial login credentials
```

Authentication:

```text
public
```

Important rules:

- User registers with national ID, phone number, username, password, name, and location.
- Registration includes `jobId` selected from `POST /api/v1/kyc/jobs/list`.
- Username must be unique.
- Phone number must be unique.
- National ID must be unique.
- Password must be hashed, never stored as plaintext or reversible encrypted text.
- Registration returns a limited access token so onboarding can continue.
- Initial account completion status should guide the client to the next step.
- Registration does not create bank account data directly inside IAM.
- IAM does not own job catalog data; `jobId` is carried into account/profile creation.

Expected next step:

```text
POST /api/v1/account/register
```

### POST /api/v1/iam/login

Fields: [See fields](./api-fields.md#post-apiv1iamlogin)

Owner:

```text
iam-service
```

Purpose:

```text
authenticate user by username and password
```

Authentication:

```text
public
```

Important rules:

- Login uses username and password.
- If app-level encryption is enabled, gateway decrypts password before IAM receives it.
- IAM validates password against password hash.
- IAM checks device state.
- If device is not verified, response should require device verification.
- If login succeeds and device is allowed, IAM returns an access token.
- There is no refresh token.
- Expired access token requires login again.

Returned business concepts:

- Account completion status
- Active profile
- Access token
- Device verification state

### POST /api/v1/iam/device-verify

Fields: [See fields](./api-fields.md#post-apiv1iamdevice-verify)

Owner:

```text
iam-service
```

Purpose:

```text
verify a device using 6-digit OTP
```

Authentication:

```text
limited token or verified login challenge context
```

Important rules:

- OTP is 6 digits.
- Raw OTP must never be stored.
- OTP must be stored as a hash.
- OTP must expire.
- Attempt count must be limited.
- Successful verification registers or activates the device for the user.
- High-risk operations should require a verified device.

### POST /api/v1/iam/logout

Fields: [See fields](./api-fields.md#post-apiv1iamlogout)

Owner:

```text
iam-service
```

Purpose:

```text
end current login session from client perspective
```

Authentication:

```text
required
```

Important rules:

- Since JWT access tokens are stateless, logout mainly ends the current client session.
- Server-side login session audit should be updated if login sessions are stored.
- Token revocation before expiration is not guaranteed unless token deny-list is added later.

## Account Requests

### POST /api/v1/account/register

Fields: [See fields](./api-fields.md#post-apiv1accountregister)

Owner:

```text
account-service
```

Purpose:

```text
create user account foundation after IAM registration
```

Authentication:

```text
required
```

Important rules:

- Create default personal profile.
- Create default saving account.
- Save `jobId` selected during registration on the user's profile/account record.
- One user can have up to 5 profiles.
- First profile is automatically personal.
- Default saving account is the main account for now.
- Account number is auto-generated.
- Account number must be exactly 18 digits.
- IBAN is auto-generated using banking IBAN format rules.
- Account number and IBAN must be unique.
- Account metadata belongs to account-service.
- Financial balance belongs to ledger-service.
- This flow should trigger or call default card creation.
- This flow should trigger or call ledger account creation.

Expected related actions:

- Create personal profile.
- Create saving account.
- Create ledger account.
- Create default card.

### POST /api/v1/account/close

Fields: [See fields](./api-fields.md#post-apiv1accountclose)

Owner:

```text
account-service
```

Purpose:

```text
close an account
```

Authentication:

```text
required
```

Important rules:

- User can close only accounts they own.
- Account ownership must be validated against active profile.
- Account cannot be closed if ledger balance is not zero.
- Account cannot be closed if there are pending transfers or holds.
- Closed account must not be used as transfer source.
- Status history should be stored.

### POST /api/v1/account/open

Fields: [See fields](./api-fields.md#post-apiv1accountopen)

Owner:

```text
account-service
```

Purpose:

```text
reopen a closed account
```

Authentication:

```text
required
```

Important rules:

- User can reopen only accounts they own.
- If `accountNumber` is omitted, account-service reopens the latest closed personal account.
- Reopening activates the owning profile and the account.
- Status history should be stored for the account and, when applicable, the profile.
- After reopen, login completion should move from `ACCOUNT_RE_OPEN_REQUIRED` to the next completion state.

### POST /api/v1/account/list

Fields: [See fields](./api-fields.md#post-apiv1accountlist)

Owner:

```text
account-service
```

Purpose:

```text
list accounts for current user/profile
```

Authentication:

```text
required
```

Important rules:

- Supports account types: `VAULT`, `SAVING`, `CHEQUE`.
- Supports account statuses: `ACTIVE`, `CLOSED`, `INACTIVE`.
- Each account has an internal database ID, an 18-digit `accountNumber`, and an IBAN.
- Account balance, if returned, must come from ledger or ledger-synchronized read model.
- User must not see accounts owned by another user/profile.

### POST /api/v1/account/balance/get

Fields: [See fields](./api-fields.md#post-apiv1accountbalanceget)

Owner:

```text
account-service
```

Purpose:

```text
get account balance for current user/profile
```

Authentication:

```text
required
```

Important rules:

- User can get balance only for their own account.
- Account ownership must be validated against active profile.
- Balance must come from ledger-service or a ledger-synchronized read model.
- Account-service does not calculate financial balance by editing account rows.
- Closed or inactive accounts may have restricted balance visibility based on business rules.

### POST /api/v1/profile/list

Fields: [See fields](./api-fields.md#post-apiv1profilelist)

Owner:

```text
account-service
```

Purpose:

```text
list profiles related to an owned account number
```

Authentication:

```text
required
```

Important rules:

- Request must include `accountNumber`.
- Account number must belong to the authenticated user.
- First profile is personal.
- User can have up to 5 profiles.
- Only one active profile should be selected at a time.
- Profile ownership must be validated against JWT user identity.

### POST /api/v1/profile/create

Fields: [See fields](./api-fields.md#post-apiv1profilecreate)

Owner:

```text
account-service
```

Purpose:

```text
create an additional user profile
```

Authentication:

```text
required
```

Important rules:

- User can have up to 5 profiles.
- First profile is created automatically during account registration.
- Additional profiles must belong to the authenticated user.
- Profile creation should not create unrelated accounts unless business rules require it.
- Profile creation must be audited.

### POST /api/v1/profile/edit

Fields: [See fields](./api-fields.md#post-apiv1profileedit)

Owner:

```text
account-service
```

Purpose:

```text
edit profile display name and image
```

Authentication:

```text
required
```

Important rules:

- User can edit only their own profile.
- Only active profiles can be edited.
- Only `displayName` and `image` are editable.
- `displayName` is trimmed before storage.
- `image: null` clears the stored profile image.
- Normalized display name must remain unique for the same user and profile type.

### POST /api/v1/profile/active/set

Fields: [See fields](./api-fields.md#post-apiv1profileactiveset)

Owner:

```text
account-service
```

Purpose:

```text
set active profile for current user
```

Authentication:

```text
required
```

Important rules:

- User can activate only their own profile.
- Only one profile should be active at a time.
- Active profile affects account list, card list, PFM, and transfer context.
- Services must still validate profile ownership; client context alone is not trusted.

### POST /api/v1/account/owner/get

Fields: [See fields](./api-fields.md#post-apiv1accountownerget)

Owner:

```text
account-service
```

Purpose:

```text
lookup destination owner display information for transfer
```

Authentication:

```text
required; public client-facing transfer lookup
```

Important rules:

- Used when user enters destination account/card information during transfer.
- This endpoint returns only non-sensitive destination display data.
- Allowed destination display data: destination name, destination image, destination account number or IBAN, and masked destination card number when card lookup is used.
- Do not expose national ID, phone number, address, full profile data, balances, or sensitive card data.
- Lookup should be rate limited to reduce account/card enumeration risk.
- Lookup should be audited.
- Transfer service must still validate destination again during transfer creation.

## Card Requests

### POST /api/v1/card/list

Fields: [See fields](./api-fields.md#post-apiv1cardlist)

Owner:

```text
card-service
```

Purpose:

```text
list cards for current user/profile
```

Authentication:

```text
required
```

Important rules:

- Supports statuses: `ACTIVE`, `INACTIVE`, `BLOCKED`, `FROZEN`, `EXPIRED`.
- Cards are linked to saving account for now.
- Card-account relation must be explicit because it can change later.
- Do not expose raw card number.
- Use masked PAN and internal card token.

### POST /api/v1/card/order

Fields: [See fields](./api-fields.md#post-apiv1cardorder)

Owner:

```text
card-service
```

Purpose:

```text
order a new card
```

Authentication:

```text
required
```

Important rules:

- Card ordering can be controlled by feature flag.
- User must have eligible active account.
- Duplicate active card order should be prevented when business rules require it.
- Card order should create auditable status history.

### POST /api/v1/card/freeze

Fields: [See fields](./api-fields.md#post-apiv1cardfreeze)

Owner:

```text
card-service
```

Purpose:

```text
temporarily freeze a card by user action
```

Authentication:

```text
required
```

Important rules:

- Freeze is temporary and user-controlled.
- User can freeze only their own card.
- Frozen card should not be usable for transfers or card operations.
- Status history should be stored.

### POST /api/v1/card/unfreeze

Fields: [See fields](./api-fields.md#post-apiv1cardunfreeze)

Owner:

```text
card-service
```

Purpose:

```text
unfreeze a user-frozen card
```

Authentication:

```text
required
```

Important rules:

- User can unfreeze only their own card.
- Blocked or expired cards cannot be unfrozen by normal user action.
- Status transition must be validated.

### POST /api/v1/card/block

Fields: [See fields](./api-fields.md#post-apiv1cardblock)

Owner:

```text
card-service
```

Purpose:

```text
block a card
```

Authentication:

```text
required
```

Important rules:

- Block is stronger than freeze.
- Blocked card cannot be used.
- User can block their own card to reorder a new one.
- Admin/system can also block cards for risk, fraud, or operational reasons.
- Block should usually be treated as irreversible for normal user flow.
- Reordering after user block should go through `POST /api/v1/card/order`.
- Status history and audit record are required.

### POST /api/v1/card/unblock

Fields: [See fields](./api-fields.md#post-apiv1cardunblock)

Owner:

```text
card-service
```

Purpose:

```text
unblock a card by system or admin action
```

Authentication:

```text
admin/internal required
```

Important rules:

- Unblock requires admin/system permission.
- Expired cards cannot be unblocked into active state.
- Status transition must be validated.
- Status history and audit record are required.

### POST /api/v1/card/expire

Fields: [See fields](./api-fields.md#post-apiv1cardexpire)

Owner:

```text
card-service
```

Purpose:

```text
mark card as expired
```

Authentication:

```text
required; internal/admin/system use preferred
```

Important rules:

- Expired card must not be used.
- This is usually a system/admin action, not a normal customer action.
- Status history should be stored.

## KYC Requests

### POST /api/v1/kyc/jobs/list

Fields: [See fields](./api-fields.md#post-apiv1kycjobslist)

Owner:

```text
kyc-service
```

Purpose:

```text
list available jobs for KYC profile data
```

Authentication:

```text
public
```

Important rules:

- Jobs should be seeded and managed as reference data.
- Only active jobs should be shown to normal users.
- User selects a job from this list during registration.
- Selected `jobId` must be saved during user account/profile creation.
- `jobId` must be validated against active jobs.

### POST /api/v1/kyc/referral/set

Fields: [See fields](./api-fields.md#post-apiv1kycreferralset)

Owner:

```text
kyc-service
```

Purpose:

```text
save referral during registration flow
```

Authentication:

```text
required or onboarding-limited token
```

Important rules:

- Referral can be set only during registration flow.
- Referral cannot be changed later.
- Reward is not paid immediately.
- Reward is paid after referred user completes KYC.
- Reward is paid only once.
- Referrer must have an active account before reward transfer.

### POST /api/v1/kyc/status/get

Fields: [See fields](./api-fields.md#post-apiv1kycstatusget)

Owner:

```text
kyc-service
```

Purpose:

```text
get KYC status for current user
```

Authentication:

```text
required
```

Important rules:

- KYC status controls onboarding flow.
- Suggested statuses: `NOT_STARTED`, `IN_PROGRESS`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`.
- Account completion status should be updated or derived from KYC state.

### POST /api/v1/kyc/complete

Fields: [See fields](./api-fields.md#post-apiv1kyccomplete)

Owner:

```text
kyc-service
```

Purpose:

```text
complete or approve KYC case
```

Authentication:

```text
internal/admin/system use preferred
```

Important rules:

- This should not be a normal public customer action in production.
- When KYC becomes approved, referral reward eligibility must be checked.
- Referral reward should be requested through transfer/ledger flow.
- KYC completion should be auditable.

## Feature Flag Requests

### POST /api/v1/feature-flags/list

Fields: [See fields](./api-fields.md#post-apiv1feature-flagslist)

Owner:

```text
feature-flag-service
```

Purpose:

```text
list feature flags
```

Authentication:

```text
required; admin/internal access preferred
```

Important rules:

- Used by services or admin panel to see runtime switches.
- Normal users should not receive sensitive operational flags.
- Example flags:
  - `transfer.card-to-card.enabled`
  - `transfer.account-to-account.enabled`
  - `transfer.card-to-account.enabled`
  - `transfer.account-to-card.enabled`
  - `card.order.enabled`
  - `referral.reward.enabled`

### POST /api/v1/feature-flags/update

Fields: [See fields](./api-fields.md#post-apiv1feature-flagsupdate)

Owner:

```text
feature-flag-service
```

Purpose:

```text
update feature flag value
```

Authentication:

```text
admin required
```

Important rules:

- Every update must be audited.
- Track who changed the flag.
- Track old value and new value.
- Feature changes can affect transfer, card, and referral flows.

## PFM Requests

### POST /api/v1/pfm/transactions/list

Fields: [See fields](./api-fields.md#post-apiv1pfmtransactionslist)

Owner:

```text
pfm-service
```

Purpose:

```text
list personal finance transactions
```

Authentication:

```text
required
```

Important rules:

- Transaction data should come from ledger or a ledger-synchronized read model.
- User must see only their own profile/account transactions.
- This API is a read experience, not the financial source of truth.

### POST /api/v1/pfm/boxes/list

Fields: [See fields](./api-fields.md#post-apiv1pfmboxeslist)

Owner:

```text
pfm-service
```

Purpose:

```text
list user's boxes
```

Authentication:

```text
required
```

Important rules:

- Box is a PFM concept backed by account type `VAULT`.
- Box metadata belongs to PFM.
- Vault account metadata belongs to account-service.
- Balance belongs to ledger-service.

### POST /api/v1/pfm/boxes/create

Fields: [See fields](./api-fields.md#post-apiv1pfmboxescreate)

Owner:

```text
pfm-service
```

Purpose:

```text
create a new box
```

Authentication:

```text
required
```

Important rules:

- Creating box should create or link a `VAULT` account.
- Ledger account must be created for the vault.
- PFM stores title, image, preset, and display metadata.
- Financial balance is not owned by PFM.

### POST /api/v1/pfm/box-presets/list

Fields: [See fields](./api-fields.md#post-apiv1pfmbox-presetslist)

Owner:

```text
pfm-service
```

Purpose:

```text
list box creation presets
```

Authentication:

```text
required
```

Important rules:

- Presets contain title and image.
- Only active presets should be visible.
- Presets are reference/display data.

### POST /api/v1/pfm/boxes/edit

Fields: [See fields](./api-fields.md#post-apiv1pfmboxesedit)

Owner:

```text
pfm-service
```

Purpose:

```text
edit box display metadata
```

Authentication:

```text
required
```

Important rules:

- User can edit only their own box.
- Edit should affect PFM metadata only.
- Financial account state must not be changed by metadata edit.

## Transfer Requests

### POST /api/v1/transfers/create

Fields: [See fields](./api-fields.md#post-apiv1transferscreate)

Owner:

```text
transfer-service
```

Purpose:

```text
create a money transfer
```

Authentication:

```text
required
```

Important rules:

- Must require idempotency key.
- Same idempotency key and same request returns same result.
- Same idempotency key and different request returns conflict.
- Transfer method must be enabled by feature flag.
- Source ownership must be validated.
- Destination must be validated.
- Destination lookup can use `POST /api/v1/account/owner/get` before transfer creation.
- Internal transfer is supported first.
- External-compatible design is required for later.
- Real debit and credit happen in ledger-service.
- Transfer service orchestrates and stores transfer status.
- Successful transfers appear in recent list.
- Failed or pending transfers do not appear in recent list.

Transfer methods:

```text
CARD_TO_CARD
ACCOUNT_TO_ACCOUNT
CARD_TO_ACCOUNT
ACCOUNT_TO_CARD
```

Transfer statuses:

```text
PENDING
PROCESSING
SUCCESS
FAILED
CANCELLED
REVERSED
```

### POST /api/v1/transfers/methods/list

Fields: [See fields](./api-fields.md#post-apiv1transfersmethodslist)

Owner:

```text
transfer-service
```

Purpose:

```text
list available transfer methods
```

Authentication:

```text
required
```

Important rules:

- Returned methods should respect feature flags.
- Methods may depend on card/account availability.
- Disabled methods should not be usable by create-transfer.

### POST /api/v1/transfers/recent/list

Fields: [See fields](./api-fields.md#post-apiv1transfersrecentlist)

Owner:

```text
transfer-service
```

Purpose:

```text
list successful recent transfer history
```

Authentication:

```text
required
```

Important rules:

- Return only successful transfers.
- User must see only their own transfer history.
- Response should include only destination name and destination card/account number.
- Do not return destination phone number, national ID, address, balance, or sensitive card data.
- This is a transfer history view, not the ledger source of truth.

## Internal Account Requests

These APIs are internal service APIs. They should not be directly exposed to mobile/web clients.

### POST /internal/v1/account/referral/reward-account/get

Fields: [See fields](./api-fields.md#post-internalv1accountreferralreward-accountget)

Owner:

```text
account-service
```

Purpose:

```text
find an active referrer account in the requested reward currency
```

Authentication:

```text
internal service authentication required
```

Important rules:

- The account and its profile must both be active.
- The account currency must match the requested reward currency.
- The active profile is preferred, then the personal profile, then the oldest eligible account.
- A missing eligible account returns `NOT_FOUND`; KYC treats this as a referral business-rule failure.

## Internal Ledger Requests

These APIs are internal service APIs. They should not be directly exposed to mobile/web clients.

### POST /internal/v1/ledger/account/create

Fields: [See fields](./api-fields.md#post-internalv1ledgeraccountcreate)

Owner:

```text
ledger-service
```

Purpose:

```text
create ledger account for a bank account or vault
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Called after account creation.
- Ledger account maps to external account number from account-service.
- Must be idempotent by external account reference.

### POST /internal/v1/ledger/account/close

Fields: [See fields](./api-fields.md#post-internalv1ledgeraccountclose)

Owner:

```text
ledger-service
```

Purpose:

```text
close a ledger account after its bank account is closed
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Available and current balances must both be zero.
- Repeated close requests return the existing closed state.
- Account-service delivers this request through its transactional outbox.

### POST /internal/v1/ledger/account/reopen

Fields: [See fields](./api-fields.md#post-internalv1ledgeraccountreopen)

Owner:

```text
ledger-service
```

Purpose:

```text
reactivate a closed ledger account after its bank account is reopened
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Repeated reopen requests return the existing active state.
- Lifecycle requests are processed in account outbox order.

### POST /internal/v1/ledger/transactions/internal-transfer

Fields: [See fields](./api-fields.md#post-internalv1ledgertransactionsinternal-transfer)

Owner:

```text
ledger-service
```

Purpose:

```text
post double-entry internal transfer
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Debit and credit entries must balance.
- Balance update must be transactional.
- Insufficient funds must prevent debit.
- Duplicate reference must not create duplicate ledger movement.

### POST /internal/v1/ledger/transactions/referral-reward

Fields: [See fields](./api-fields.md#post-internalv1ledgertransactionsreferral-reward)

Owner:

```text
ledger-service
```

Purpose:

```text
post referral reward ledger movement
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Reward is paid only after referred user completes KYC.
- Reward is paid only once.
- Referrer must have active account.
- Must be idempotent by referral reward reference.

### POST /internal/v1/ledger/account/balance/get

Fields: [See fields](./api-fields.md#post-internalv1ledgeraccountbalanceget)

Owner:

```text
ledger-service
```

Purpose:

```text
get authoritative ledger balance
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Ledger is the source of truth.
- Account/PFM/transfer services may use this for validation or display.
- Public clients should not call this endpoint directly.

### POST /internal/v1/ledger/account/entries/list

Fields: [See fields](./api-fields.md#post-internalv1ledgeraccountentrieslist)

Owner:

```text
ledger-service
```

Purpose:

```text
list ledger entries for an account
```

Authentication:

```text
internal service authentication required
```

Important rules:

- Used for audit, reconciliation, and transaction views.
- Access must be restricted.
- This data is sensitive financial history.

## Infrastructure Requests

### GET /actuator/health

Fields: [See fields](./api-fields.md#get-actuatorhealth)

Owner:

```text
every service
```

Purpose:

```text
health check
```

Authentication:

```text
environment-dependent
```

Important rules:

- Used by local development, orchestration, and monitoring.
- Should not expose sensitive details publicly.

### GET /actuator/info

Fields: [See fields](./api-fields.md#get-actuatorinfo)

Owner:

```text
every service
```

Purpose:

```text
service build/runtime information
```

Authentication:

```text
environment-dependent
```

Important rules:

- Should expose only safe operational metadata.
- Do not leak secrets, internal URLs, database credentials, or private config.
