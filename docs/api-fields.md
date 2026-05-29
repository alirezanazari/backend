# API Fields

This document defines endpoint-specific request and response `data` fields for the APIs listed in [API Requests](./api-requests.md).

All public business APIs still use the shared envelope from [API Contract](./api-contract.md):

```json
{
  "context": {},
  "data": {}
}
```

This document defines only the endpoint-specific `data` object.

## Contract Module Strategy

Microservices should not share database entities or domain objects.

They can share stable API contract/value models.

Recommended shared module:

```text
contract
  src/main/java/com/redbank/contracts
    envelope
    common
    identity
    account
    card
    transfer
    pfm
```

Use this module for:

- API envelope classes.
- Status code enum.
- Stable value objects.
- Shared display models.
- Shared enum names.

Do not put these in the shared module:

- JPA entities.
- Repositories.
- Service classes.
- Business use cases.
- Database migration models.
- Internal implementation details.

Service-specific request/response classes should stay inside the owning service:

```text
iam-service/src/main/java/com/redbank/iam/api/v1
account-service/src/main/java/com/redbank/account/api/v1
card-service/src/main/java/com/redbank/card/api/v1
transfer-service/src/main/java/com/redbank/transfer/api/v1
```

Rule:

```text
share stable base models, not service internals
```

This gives reuse without turning microservices into one tightly coupled application.

## Base Envelope Models

These classes belong in `contract`.

### ApiRequest<T>

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `context` | `RequestContext` | Yes | Request metadata and client context. |
| `data` | `T` | Endpoint-dependent | Endpoint request payload. |

### ApiResponse<T>

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `context` | `ResponseContext` | Yes | Response metadata and status. |
| `data` | `T` | Endpoint-dependent | Endpoint response payload, or `null` for failure. |

### RequestContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `requestId` | `String` | Yes | Client or gateway generated request ID. |
| `account` | `AccountContext` | No | Selected account/profile context. |
| `os` | `OsContext` | No | Client OS context. |
| `app` | `AppContext` | No | Client app context. |
| `device` | `DeviceContext` | No | Client device context. |
| `network` | `NetworkContext` | No | Client network context. |

### AccountContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | No | Selected 18-digit account number. Empty during registration/login. |
| `profileId` | `String` | No | Selected or active profile ID. Empty during registration/login. |

### OsContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | `String` | No | OS name such as `Android`, `iOS`, or `Web`. |
| `version` | `String` | No | OS version. |

### AppContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `version` | `String` | No | Application version. |
| `buildNumber` | `String` | No | Application build number. |
| `isRooted` | `Boolean` | No | Client-reported rooted/jailbroken signal. Treat as risk signal only. |
| `language` | `String` | No | Client language, such as `en-US` or `fa-IR`. |
| `theme` | `String` | No | Client theme, such as `light` or `dark`. |

### DeviceContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `deviceId` | `String` | No | Client device ID or server-issued device ID. |
| `model` | `String` | No | Device model. |
| `fingerprint` | `String` | No | Stable hashed fingerprint. Must not expose raw hardware identifiers. |

### NetworkContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ipAddress` | `String` | No | Client-reported IP if available. Gateway/server IP metadata is more trusted. |
| `connectionType` | `String` | No | Connection type, such as `WIFI`, `CELLULAR`, or `UNKNOWN`. |
| `operator` | `String` | No | Mobile network operator when available. |

### ResponseContext

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `requestId` | `String` | Yes | Same request ID from request/gateway. |
| `profileId` | `String` | No | Active profile used for response. |
| `status` | `Status` | Yes | Response status. |

### Status

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | `StatusCode` | Yes | Controlled response code enum. |
| `message` | `String` | Yes | Human-readable status message. |
| `httpStatus` | `Integer` | Yes | HTTP status number. |
| `details` | `List<StatusDetail>` | Yes | Empty list when there are no details. |

### StatusDetail

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `field` | `String` | No | Field path, such as `data.amount`. |
| `message` | `String` | Yes | Detail message. |
| `code` | `String` | No | Optional detail code. |

## Shared Value Models

These models are reusable across services.

### Image

Use this anywhere the API returns a user/profile/box/preset image.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `lightUrl` | `String` | No | Image URL for light theme. |
| `darkUrl` | `String` | No | Image URL for dark theme. |
| `alt` | `String` | No | Short accessibility/display text. |

### Location

Use this for user registered address and delivery address.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `countryCode` | `String` | No | ISO country code. Default can be service-defined. |
| `province` | `String` | Yes | Province. |
| `city` | `String` | Yes | City. |
| `address` | `String` | Yes | Address line. |
| `postalCode` | `String` | No | Postal code if required later. |

### Money

Use this for all financial amounts.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `amount` | `BigDecimal` | Yes | Decimal amount. Must not use floating point. |
| `currency` | `String` | Yes | Currency code, such as `USD` or `IRR`. |

### PageRequest

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `page` | `Integer` | No | Zero-based page number. |
| `size` | `Integer` | No | Page size. |

### PageResponse

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `page` | `Integer` | Yes | Current page. |
| `size` | `Integer` | Yes | Page size. |
| `totalItems` | `Long` | No | Total items when known. |
| `hasNext` | `Boolean` | Yes | Whether another page exists. |

### UserDisplay

Use only for non-sensitive display data.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `userId` | `String` | No | Internal user ID, only when safe for the API. |
| `fullName` | `String` | Yes | Display name. |
| `image` | `Image` | No | Display image. |

### ProfileSummary

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profileId` | `String` | Yes | Profile ID. |
| `type` | `ProfileType` | Yes | Profile type, such as `PERSONAL`. |
| `displayName` | `String` | Yes | Profile display name. |
| `image` | `Image` | No | Profile image. |
| `active` | `Boolean` | Yes | Whether this is active profile. |

### AccountSummary

Account number rule:

```text
accountNumber is always exactly 18 numeric digits.
```

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profileId` | `String` | Yes | Owning profile ID. |
| `accountNumber` | `String` | Yes | Auto-generated 18-digit account number. |
| `iban` | `String` | Yes | Auto-generated IBAN. |
| `type` | `AccountType` | Yes | `VAULT`, `SAVING`, `CHEQUE`. |
| `status` | `AccountStatus` | Yes | `ACTIVE`, `CLOSED`, `INACTIVE`. |
| `currency` | `String` | Yes | Account currency. |
| `balance` | `BalanceSummary` | No | Returned only when API includes balance. |

### BalanceSummary

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `availableBalance` | `Money` | Yes | Available balance. |
| `currentBalance` | `Money` | Yes | Current ledger balance. |
| `updatedAt` | `Instant` | Yes | Last balance update. |

### CardSummary

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Internal card ID. |
| `accountNumber` | `String` | Yes | Linked 18-digit account number. |
| `maskedPan` | `String` | Yes | Masked card number only. |
| `status` | `CardStatus` | Yes | Card status. |
| `expiresAt` | `YearMonth` | No | Card expiration month. |

### TransferDestinationSummary

Use this for destination lookup and recent transfer display.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `destinationType` | `DestinationType` | Yes | `ACCOUNT` or `CARD`. |
| `destinationName` | `String` | Yes | Non-sensitive destination display name. |
| `image` | `Image` | No | Destination image. |
| `accountNumber` | `String` | No | Destination 18-digit account number when account destination. |
| `iban` | `String` | No | Destination IBAN when account destination. |
| `maskedCardNumber` | `String` | No | Masked card number when card destination. |

## Shared Enums

These enum names should be shared and reused consistently.

```text
AccountCompletionStatus:
REGISTERED
DEVICE_VERIFICATION_REQUIRED
ACCOUNT_REQUIRED
ACCOUNT_RE_OPEN_REQUIRED
KYC_REQUIRED
KYC_PENDING
COMPLETED
SUSPENDED

ProfileType:
PERSONAL
BUSINESS

AccountType:
VAULT
SAVING
CHEQUE

AccountStatus:
ACTIVE
CLOSED
INACTIVE

CardStatus:
ACTIVE
INACTIVE
BLOCKED
FROZEN
EXPIRED

KycStatus:
NOT_STARTED
IN_PROGRESS
PENDING_REVIEW
APPROVED
REJECTED

TransferMethod:
CARD_TO_CARD
ACCOUNT_TO_ACCOUNT
CARD_TO_ACCOUNT
ACCOUNT_TO_CARD

TransferStatus:
PENDING
PROCESSING
SUCCESS
FAILED
CANCELLED
REVERSED

DestinationType:
ACCOUNT
CARD

LedgerDirection:
DEBIT
CREDIT
```

## Endpoint Field Contracts

All fields below are inside the endpoint `data` object.

## Gateway

### POST /api/v1/security/key-exchange

Request: [See request details](./api-requests.md#post-apiv1securitykey-exchange)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `clientPublicKey` | `String` | Yes | Client public key. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `keyExchangeId` | `String` | Yes | Temporary key exchange session ID. |
| `serverPublicKey` | `String` | Yes | Gateway public key. |
| `expiresAt` | `Instant` | Yes | Expiration timestamp. |

## IAM

### POST /api/v1/iam/register

Request: [See request details](./api-requests.md#post-apiv1iamregister)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `nationalId` | `String` | Yes | User national ID. |
| `phoneNumber` | `String` | Yes | User phone number. |
| `username` | `String` | Yes | Unique username. |
| `password` | `String` | Yes | Raw value after gateway decryption. IAM hashes it. |
| `firstName` | `String` | Yes | First name. |
| `lastName` | `String` | Yes | Last name. |
| `location` | `Location` | Yes | Registered location. |
| `jobId` | `String` | Yes | Selected job from KYC jobs list. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `userId` | `String` | Yes | Created user ID. |
| `accessToken` | `String` | Yes | Limited onboarding token. |
| `tokenType` | `String` | Yes | Usually `Bearer`. |
| `expiresAt` | `Instant` | Yes | Token expiration. |
| `accountCompletionStatus` | `AccountCompletionStatus` | Yes | Next onboarding state. |

### POST /api/v1/iam/login

Request: [See request details](./api-requests.md#post-apiv1iamlogin)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `username` | `String` | Yes | Username. |
| `password` | `String` | Yes | Raw value after gateway decryption. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accessToken` | `String` | Conditional | Present when login can continue. |
| `tokenType` | `String` | Conditional | Usually `Bearer`. |
| `expiresAt` | `Instant` | Conditional | Token expiration. |
| `accountCompletionStatus` | `AccountCompletionStatus` | Yes | User onboarding/completion state. |
| `activeProfile` | `ProfileSummary` | No | Active profile if available. |
| `deviceVerificationRequired` | `Boolean` | Yes | Whether device verify is required. |
| `challengeId` | `String` | No | OTP challenge ID when verification is required. |

### POST /api/v1/iam/device-verify

Request: [See request details](./api-requests.md#post-apiv1iamdevice-verify)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `challengeId` | `String` | Yes | OTP challenge ID. |
| `otpCode` | `String` | Yes | Six-digit OTP. In develop only, `111111` can be allowed by config. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `deviceId` | `String` | Yes | Verified device ID. |
| `deviceStatus` | `String` | Yes | Device status after verification. |
| `accessToken` | `String` | No | Token if verification completes login flow. |
| `tokenType` | `String` | No | Usually `Bearer`. |
| `expiresAt` | `Instant` | No | Token expiration. |
| `accountCompletionStatus` | `AccountCompletionStatus` | Yes | Current completion state. |

### POST /api/v1/iam/logout

Request: [See request details](./api-requests.md#post-apiv1iamlogout)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `sessionId` | `String` | No | Current login session ID if tracked. |
| `allDevices` | `Boolean` | No | Whether to logout all devices. Default `false`. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `loggedOut` | `Boolean` | Yes | Logout result. |
| `loggedOutAt` | `Instant` | Yes | Logout timestamp. |

## Account

### POST /api/v1/accounts/register

Request: [See request details](./api-requests.md#post-apiv1accountsregister)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `jobId` | `String` | Yes | Selected job ID to save on profile/account flow. |
| `profileDisplayName` | `String` | No | Display name for personal profile. |
| `profileImage` | `Image` | No | Profile image. |
| `currency` | `String` | No | Default account currency. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profile` | `ProfileSummary` | Yes | Created personal profile. |
| `account` | `AccountSummary` | Yes | Created default saving account. |
| `card` | `CardSummary` | No | Created default card if available. |
| `ledgerAccountCreated` | `Boolean` | Yes | Whether ledger account was created. |
| `accountCompletionStatus` | `AccountCompletionStatus` | Yes | Next onboarding state. |

### POST /api/v1/accounts/close

Request: [See request details](./api-requests.md#post-apiv1accountsclose)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | Yes | 18-digit account number. |
| `reasonText` | `String` | No | Optional human-readable reason. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | Yes | Closed 18-digit account number. |
| `status` | `AccountStatus` | Yes | New account status. |
| `closedAt` | `Instant` | Yes | Close timestamp. |

### POST /api/v1/accounts/list

Request: [See request details](./api-requests.md#post-apiv1accountslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `types` | `List<AccountType>` | No | Filter by account type. |
| `statuses` | `List<AccountStatus>` | No | Filter by account status. |
| `includeBalance` | `Boolean` | No | Whether to include balance summary. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accounts` | `List<AccountSummary>` | Yes | Accounts visible to user/profile. |

### POST /api/v1/accounts/balance/get

Request: [See request details](./api-requests.md#post-apiv1accountsbalanceget)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | Yes | 18-digit account number. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | Yes | 18-digit account number. |
| `iban` | `String` | Yes | IBAN. |
| `balance` | `BalanceSummary` | Yes | Ledger-backed balance. |

### POST /api/v1/profiles/list

Request: [See request details](./api-requests.md#post-apiv1profileslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | Yes | 18-digit account number used to resolve related profiles. |
| `includeInactive` | `Boolean` | No | Whether inactive profiles are included. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profiles` | `List<ProfileSummary>` | Yes | User profiles. |
| `activeProfileId` | `String` | No | Active profile ID. |

### POST /api/v1/profiles/create

Request: [See request details](./api-requests.md#post-apiv1profilescreate)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `type` | `ProfileType` | Yes | Profile type. |
| `displayName` | `String` | Yes | Display name. |
| `image` | `Image` | No | Profile image. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profile` | `ProfileSummary` | Yes | Created profile. |

### POST /api/v1/profiles/active/set

Request: [See request details](./api-requests.md#post-apiv1profilesactiveset)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `profileId` | `String` | Yes | Profile to activate. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `activeProfile` | `ProfileSummary` | Yes | New active profile. |

### POST /api/v1/accounts/owner/get

Request: [See request details](./api-requests.md#post-apiv1accountsownerget)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `destinationType` | `DestinationType` | Yes | `ACCOUNT` or `CARD`. |
| `accountNumber` | `String` | Conditional | Required 18-digit account number for account lookup when IBAN not provided. |
| `iban` | `String` | Conditional | Required for account lookup when account number not provided. |
| `cardNumber` | `String` | Conditional | Required for card lookup. Gateway/service must mask output. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `destination` | `TransferDestinationSummary` | Yes | Non-sensitive destination display info. |

## Card

### POST /api/v1/cards/list

Request: [See request details](./api-requests.md#post-apiv1cardslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `statuses` | `List<CardStatus>` | No | Filter by card status. |
| `accountNumber` | `String` | No | Filter by linked 18-digit account number. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cards` | `List<CardSummary>` | Yes | User cards. |

### POST /api/v1/cards/order

Request: [See request details](./api-requests.md#post-apiv1cardsorder)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | Yes | 18-digit account number to link the card. |
| `deliveryLocation` | `Location` | No | Delivery address if physical card delivery is needed. |
| `reason` | `String` | No | Order reason, such as new card or reorder. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `orderId` | `String` | Yes | Card order ID. |
| `card` | `CardSummary` | No | Card if already generated. |
| `orderStatus` | `String` | Yes | Order status. |

### POST /api/v1/card/freeze

Request: [See request details](./api-requests.md#post-apiv1cardfreeze)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `reason` | `String` | No | Freeze reason. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `status` | `CardStatus` | Yes | New status. |
| `updatedAt` | `Instant` | Yes | Update timestamp. |

### POST /api/v1/card/unfreeze

Request: [See request details](./api-requests.md#post-apiv1cardunfreeze)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `status` | `CardStatus` | Yes | New status. |
| `updatedAt` | `Instant` | Yes | Update timestamp. |

### POST /api/v1/card/block

Request: [See request details](./api-requests.md#post-apiv1cardblock)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `reason` | `String` | Yes | Block reason. |
| `reorderRequested` | `Boolean` | No | Whether user wants reorder after blocking. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `status` | `CardStatus` | Yes | New status. |
| `reorderRequired` | `Boolean` | Yes | Whether reorder should continue. |
| `updatedAt` | `Instant` | Yes | Update timestamp. |

### POST /api/v1/card/unblock

Request: [See request details](./api-requests.md#post-apiv1cardunblock)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `reason` | `String` | Yes | Admin/system unblock reason. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `status` | `CardStatus` | Yes | New status. |
| `updatedAt` | `Instant` | Yes | Update timestamp. |

### POST /api/v1/card/expire

Request: [See request details](./api-requests.md#post-apiv1cardexpire)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `reason` | `String` | No | Expiration reason. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `cardId` | `String` | Yes | Card ID. |
| `status` | `CardStatus` | Yes | New status. |
| `updatedAt` | `Instant` | Yes | Update timestamp. |

## KYC

### POST /api/v1/kyc/jobs/list

Request: [See request details](./api-requests.md#post-apiv1kycjobslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `language` | `String` | No | Requested language. Can default from context app language. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `jobs` | `List<JobSummary>` | Yes | Active jobs. |

JobSummary:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `jobId` | `String` | Yes | Job ID. |
| `code` | `String` | Yes | Job code. |
| `title` | `String` | Yes | Display title. |

### POST /api/v1/kyc/referral/set

Request: [See request details](./api-requests.md#post-apiv1kycreferralset)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `referralCode` | `String` | Yes | Referral code. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `referralId` | `String` | Yes | Saved referral ID. |
| `referrerName` | `String` | No | Non-sensitive referrer display name. |
| `status` | `String` | Yes | Referral status. |

### POST /api/v1/kyc/status/get

Request: [See request details](./api-requests.md#post-apiv1kycstatusget)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `userId` | `String` | No | Admin/internal use only. Normal users use token identity. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `kycCaseId` | `String` | No | KYC case ID. |
| `status` | `KycStatus` | Yes | KYC status. |
| `accountCompletionStatus` | `AccountCompletionStatus` | Yes | Completion state derived from KYC. |
| `completedAt` | `Instant` | No | Completion timestamp. |
| `rejectionReason` | `String` | No | Rejection reason when rejected. |

### POST /api/v1/kyc/complete

Request: [See request details](./api-requests.md#post-apiv1kyccomplete)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `userId` | `String` | Yes | User whose KYC is completed. |
| `status` | `KycStatus` | Yes | Usually `APPROVED` or `REJECTED`. |
| `reason` | `String` | No | Admin/system reason. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `kycCaseId` | `String` | Yes | KYC case ID. |
| `status` | `KycStatus` | Yes | New KYC status. |
| `referralRewardRequested` | `Boolean` | Yes | Whether referral reward flow was requested. |

## Feature Flag

### POST /api/v1/feature-flags/list

Request: [See request details](./api-requests.md#post-apiv1feature-flagslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `keys` | `List<String>` | No | Optional list of keys. |
| `includeDisabled` | `Boolean` | No | Whether disabled flags are included. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `flags` | `List<FeatureFlagSummary>` | Yes | Feature flags. |

FeatureFlagSummary:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `key` | `String` | Yes | Flag key. |
| `enabled` | `Boolean` | Yes | Whether enabled. |
| `description` | `String` | No | Description. |

### POST /api/v1/feature-flags/update

Request: [See request details](./api-requests.md#post-apiv1feature-flagsupdate)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `key` | `String` | Yes | Flag key. |
| `enabled` | `Boolean` | Yes | New value. |
| `description` | `String` | No | Optional description update. |
| `reason` | `String` | Yes | Audit reason. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `flag` | `FeatureFlagSummary` | Yes | Updated flag. |
| `updatedAt` | `Instant` | Yes | Update timestamp. |

## PFM

### POST /api/v1/pfm/transactions/list

Request: [See request details](./api-requests.md#post-apiv1pfmtransactionslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accountNumber` | `String` | No | Filter by 18-digit account number. |
| `boxId` | `String` | No | Filter by box. |
| `fromDate` | `LocalDate` | No | Start date. |
| `toDate` | `LocalDate` | No | End date. |
| `page` | `PageRequest` | No | Pagination. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `transactions` | `List<PfmTransactionSummary>` | Yes | Transaction view items. |
| `page` | `PageResponse` | Yes | Pagination. |

### POST /api/v1/pfm/boxes/list

Request: [See request details](./api-requests.md#post-apiv1pfmboxeslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `statuses` | `List<String>` | No | Filter by box status. |
| `includeBalance` | `Boolean` | No | Whether to include ledger balance. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `boxes` | `List<BoxSummary>` | Yes | User boxes. |

### POST /api/v1/pfm/boxes/create

Request: [See request details](./api-requests.md#post-apiv1pfmboxescreate)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | `String` | Yes | Box title. |
| `presetId` | `String` | No | Optional preset ID. |
| `image` | `Image` | No | Custom image. |
| `currency` | `String` | No | Vault currency. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `box` | `BoxSummary` | Yes | Created box. |
| `account` | `AccountSummary` | Yes | Created vault account. |

### POST /api/v1/pfm/box-presets/list

Request: [See request details](./api-requests.md#post-apiv1pfmbox-presetslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `language` | `String` | No | Requested language. Can default from context app language. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `presets` | `List<BoxPresetSummary>` | Yes | Active presets. |

### POST /api/v1/pfm/boxes/edit

Request: [See request details](./api-requests.md#post-apiv1pfmboxesedit)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `boxId` | `String` | Yes | Box ID. |
| `title` | `String` | No | New title. |
| `presetId` | `String` | No | New preset. |
| `image` | `Image` | No | New image. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `box` | `BoxSummary` | Yes | Updated box. |

PFM shared response models:

```text
BoxSummary:
boxId, accountNumber, title, image, status, balance

BoxPresetSummary:
presetId, title, image, sortOrder

PfmTransactionSummary:
transactionId, accountNumber, title, amount, direction, occurredAt
```

## Transfer

### POST /api/v1/transfers/create

Request: [See request details](./api-requests.md#post-apiv1transferscreate)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `idempotencyKey` | `String` | Yes | Prevents duplicate transfer. Can also be sent as header later. |
| `method` | `TransferMethod` | Yes | Transfer method. |
| `sourceType` | `DestinationType` | Yes | Source type: `ACCOUNT` or `CARD`. |
| `sourceAccountNumber` | `String` | Conditional | Required 18-digit source account number when source type is `ACCOUNT`. |
| `sourceCardNumber` | `String` | Conditional | Required source card number when source type is `CARD`. |
| `destinationType` | `DestinationType` | Yes | Destination type. |
| `destinationAccountNumber` | `String` | Conditional | Required 18-digit destination account number when IBAN not used. |
| `destinationIban` | `String` | Conditional | Required for account destination when account number not used. |
| `destinationCardNumber` | `String` | Conditional | Required for card destination. |
| `amount` | `Money` | Yes | Transfer amount. |
| `description` | `String` | No | User description. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `transferId` | `String` | Yes | Transfer ID. |
| `status` | `TransferStatus` | Yes | Transfer status. |
| `ledgerTransactionId` | `String` | No | Ledger transaction ID when posted. |
| `destination` | `TransferDestinationSummary` | Yes | Non-sensitive destination summary. |
| `amount` | `Money` | Yes | Transfer amount. |
| `createdAt` | `Instant` | Yes | Creation timestamp. |

### POST /api/v1/transfers/methods/list

Request: [See request details](./api-requests.md#post-apiv1transfersmethodslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `sourceType` | `DestinationType` | No | Optional source filter. |
| `destinationType` | `DestinationType` | No | Optional destination filter. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `methods` | `List<TransferMethodSummary>` | Yes | Available methods. |

TransferMethodSummary:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `method` | `TransferMethod` | Yes | Method enum. |
| `enabled` | `Boolean` | Yes | Whether available. |
| `title` | `String` | Yes | Display title. |

### POST /api/v1/transfers/recent/list

Request: [See request details](./api-requests.md#post-apiv1transfersrecentlist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `limit` | `Integer` | No | Maximum recent items. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `items` | `List<RecentTransferDestination>` | Yes | Recent destination display items. |

RecentTransferDestination:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `destinationName` | `String` | Yes | Destination display name. |
| `destinationAccountOrCardNumber` | `String` | Yes | Destination account number or masked card number. |

## Internal Ledger

### POST /internal/v1/ledger/accounts/create

Request: [See request details](./api-requests.md#post-internalv1ledgeraccountscreate)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `externalAccountNumber` | `String` | Yes | Account-service 18-digit account number. |
| `ownerUserId` | `String` | Yes | Owner user ID. |
| `currency` | `String` | Yes | Currency. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ledgerAccountId` | `String` | Yes | Ledger account ID. |
| `status` | `String` | Yes | Ledger account status. |

### POST /internal/v1/ledger/transactions/internal-transfer

Request: [See request details](./api-requests.md#post-internalv1ledgertransactionsinternal-transfer)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `referenceId` | `String` | Yes | Transfer reference ID. |
| `sourceExternalAccountNumber` | `String` | Yes | Source 18-digit account number from account-service. |
| `destinationExternalAccountNumber` | `String` | Yes | Destination 18-digit account number from account-service. |
| `amount` | `Money` | Yes | Transfer amount. |
| `idempotencyKey` | `String` | Yes | Idempotency key. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ledgerTransactionId` | `String` | Yes | Ledger transaction ID. |
| `status` | `String` | Yes | Ledger transaction status. |

### POST /internal/v1/ledger/transactions/referral-reward

Request: [See request details](./api-requests.md#post-internalv1ledgertransactionsreferral-reward)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `referralRewardId` | `String` | Yes | Referral reward reference. |
| `referrerExternalAccountNumber` | `String` | Yes | Referrer 18-digit account number from account-service. |
| `amount` | `Money` | Yes | Reward amount. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `ledgerTransactionId` | `String` | Yes | Ledger transaction ID. |
| `status` | `String` | Yes | Ledger transaction status. |

### POST /internal/v1/ledger/accounts/balance/get

Request: [See request details](./api-requests.md#post-internalv1ledgeraccountsbalanceget)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `externalAccountNumber` | `String` | Yes | Account-service 18-digit account number. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `externalAccountNumber` | `String` | Yes | Account-service 18-digit account number. |
| `ledgerAccountId` | `String` | Yes | Ledger account ID. |
| `balance` | `BalanceSummary` | Yes | Authoritative balance. |

### POST /internal/v1/ledger/accounts/entries/list

Request: [See request details](./api-requests.md#post-internalv1ledgeraccountsentrieslist)

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `externalAccountNumber` | `String` | Yes | Account-service 18-digit account number. |
| `fromDate` | `LocalDate` | No | Start date. |
| `toDate` | `LocalDate` | No | End date. |
| `page` | `PageRequest` | No | Pagination. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `entries` | `List<LedgerEntrySummary>` | Yes | Ledger entries. |
| `page` | `PageResponse` | Yes | Pagination. |

LedgerEntrySummary:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `entryId` | `String` | Yes | Ledger entry ID. |
| `ledgerTransactionId` | `String` | Yes | Ledger transaction ID. |
| `direction` | `LedgerDirection` | Yes | Debit or credit. |
| `amount` | `Money` | Yes | Entry amount. |
| `createdAt` | `Instant` | Yes | Entry timestamp. |

## Infrastructure

### GET /actuator/health

Request: [See request details](./api-requests.md#get-actuatorhealth)

Request:

```text
No API envelope and no data fields.
```

Response:

```text
Spring Actuator health response.
```

### GET /actuator/info

Request: [See request details](./api-requests.md#get-actuatorinfo)

Request:

```text
No API envelope and no data fields.
```

Response:

```text
Spring Actuator info response with safe metadata only.
```

## Debug-Only Endpoints

These endpoints are documented in [System Access](./system-access.md), not [API Requests](./api-requests.md), because they must not exist in production.

### POST /debug/v1/tokens/user

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `userId` | `String` | Yes | Existing develop user ID. |
| `profileId` | `String` | No | Existing develop profile ID. |
| `scopes` | `List<String>` | No | Requested scopes. |
| `ttlSeconds` | `Integer` | No | Token TTL, capped by service. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accessToken` | `String` | Yes | Debug user token. |
| `tokenType` | `String` | Yes | Usually `Bearer`. |
| `expiresAt` | `Instant` | Yes | Expiration timestamp. |

### POST /debug/v1/tokens/admin

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `adminId` | `String` | Yes | Develop admin identifier. |
| `scopes` | `List<String>` | No | Requested scopes. |
| `ttlSeconds` | `Integer` | No | Token TTL, capped by service. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `accessToken` | `String` | Yes | Debug admin token. |
| `tokenType` | `String` | Yes | Usually `Bearer`. |
| `expiresAt` | `Instant` | Yes | Expiration timestamp. |

### POST /debug/v1/tokens/service

Request:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `serviceName` | `String` | Yes | Service identity. |
| `scopes` | `List<String>` | Yes | Internal scopes. |
| `ttlSeconds` | `Integer` | No | Token TTL, capped by service. |

Response:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `serviceToken` | `String` | Yes | Debug service token. |
| `tokenType` | `String` | Yes | Usually `Bearer`. |
| `expiresAt` | `Instant` | Yes | Expiration timestamp. |
