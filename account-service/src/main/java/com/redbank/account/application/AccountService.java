package com.redbank.account.application;

import com.redbank.account.api.internal.dto.GetAccountCompletionRequest;
import com.redbank.account.api.internal.dto.GetAccountCompletionResponse;
import com.redbank.account.api.internal.dto.GetCardAccountRequest;
import com.redbank.account.api.internal.dto.GetCardAccountResponse;
import com.redbank.account.api.v1.dto.CloseAccountRequest;
import com.redbank.account.api.v1.dto.CloseAccountResponse;
import com.redbank.account.api.v1.dto.CreateProfileRequest;
import com.redbank.account.api.v1.dto.CreateProfileResponse;
import com.redbank.account.api.v1.dto.EditProfileRequest;
import com.redbank.account.api.v1.dto.EditProfileResponse;
import com.redbank.account.api.v1.dto.GetAccountBalanceRequest;
import com.redbank.account.api.v1.dto.GetAccountBalanceResponse;
import com.redbank.account.api.v1.dto.GetAccountOwnerRequest;
import com.redbank.account.api.v1.dto.GetAccountOwnerResponse;
import com.redbank.account.api.v1.dto.ListAccountsRequest;
import com.redbank.account.api.v1.dto.ListAccountsResponse;
import com.redbank.account.api.v1.dto.ListProfilesRequest;
import com.redbank.account.api.v1.dto.ListProfilesResponse;
import com.redbank.account.api.v1.dto.OpenAccountRequest;
import com.redbank.account.api.v1.dto.OpenAccountResponse;
import com.redbank.account.api.v1.dto.RegisterAccountRequest;
import com.redbank.account.api.v1.dto.RegisterAccountResponse;
import com.redbank.account.api.v1.dto.SetActiveProfileRequest;
import com.redbank.account.api.v1.dto.SetActiveProfileResponse;
import com.redbank.account.config.AccountProperties;
import com.redbank.account.domain.AccountStatusHistoryRecord;
import com.redbank.account.domain.BankAccountRecord;
import com.redbank.account.domain.ProfileRecord;
import com.redbank.account.domain.ProfileStatus;
import com.redbank.account.domain.ProfileStatusHistoryRecord;
import com.redbank.account.infrastructure.BankAccountRepository;
import com.redbank.account.infrastructure.ProfileRepository;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.common.Image;
import com.redbank.contracts.card.CardForm;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferDestinationSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private static final int ACCOUNT_GENERATION_ATTEMPTS = 20;

    private final ProfileRepository profileRepository;
    private final BankAccountRepository bankAccountRepository;
    private final JwtTokenService jwtTokenService;
    private final KycJobCatalog kycJobCatalog;
    private final AccountNumberGenerator accountNumberGenerator;
    private final IbanGenerator ibanGenerator;
    private final AccountOutboxService accountOutboxService;
    private final LedgerBalanceClient ledgerBalanceClient;
    private final AccountProperties accountProperties;
    private final Clock clock;

    public AccountService(
            ProfileRepository profileRepository,
            BankAccountRepository bankAccountRepository,
            JwtTokenService jwtTokenService,
            KycJobCatalog kycJobCatalog,
            AccountNumberGenerator accountNumberGenerator,
            IbanGenerator ibanGenerator,
            AccountOutboxService accountOutboxService,
            LedgerBalanceClient ledgerBalanceClient,
            AccountProperties accountProperties,
            Clock clock
    ) {
        this.profileRepository = profileRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.jwtTokenService = jwtTokenService;
        this.kycJobCatalog = kycJobCatalog;
        this.accountNumberGenerator = accountNumberGenerator;
        this.ibanGenerator = ibanGenerator;
        this.accountOutboxService = accountOutboxService;
        this.ledgerBalanceClient = ledgerBalanceClient;
        this.accountProperties = accountProperties;
        this.clock = clock;
    }

    @Transactional
    public RegisterAccountResponse register(String authorizationHeader, RegisterAccountRequest request) {
        VerifiedToken token = jwtTokenService.verifyAccountRegistrationToken(authorizationHeader);
        validateJobId(request.jobId());

        if (profileRepository.countByUserId(token.userId()) > 0) {
            throw new AccountConflictException("Account foundation already exists for this user");
        }

        Instant now = clock.instant();
        String currency = normalizeCurrency(request.currency());
        ProfileRecord profile = new ProfileRecord(
                UUID.randomUUID(),
                token.userId(),
                request.jobId(),
                ProfileType.PERSONAL,
                defaultIfBlank(request.profileDisplayName(), "Personal"),
                normalizeDisplayName(defaultIfBlank(request.profileDisplayName(), "Personal")),
                imageLightUrl(request.profileImage()),
                imageDarkUrl(request.profileImage()),
                imageAlt(request.profileImage()),
                ProfileStatus.ACTIVE,
                true,
                now,
                now
        );
        BankAccountRecord account = createUniqueAccount(profile, request.jobId(), AccountType.SAVING, currency, now);

        try {
            profileRepository.save(profile);
            bankAccountRepository.save(account);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException("Account number, IBAN, or profile already exists");
        }
        accountOutboxService.enqueueLedgerAccountCreation(account);
        accountOutboxService.enqueueDefaultCardCreation(account, CardForm.PHYSICAL);

        return new RegisterAccountResponse(
                toProfileSummary(profile),
                toAccountSummary(account, false),
                null,
                false,
                AccountCompletionStatus.KYC_REQUIRED
        );
    }

    @Transactional
    public CreateProfileResponse createProfile(String authorizationHeader, CreateProfileRequest request) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        int profileCount = profileRepository.countByUserId(token.userId());
        if (profileCount >= accountProperties.maxProfilesPerUser()) {
            throw new AccountLimitExceededException("User can have maximum %d profiles".formatted(accountProperties.maxProfilesPerUser()));
        }

        ProfileRecord activeProfile = profileRepository.findActiveByUserId(token.userId())
                .orElseThrow(() -> new AccountBusinessRuleException("Personal profile must be created first"));
        String displayNameNormalized = normalizeDisplayName(request.displayName());
        if (profileRepository.existsActiveByUserIdAndTypeAndDisplayNameNormalized(
                token.userId(),
                request.type(),
                displayNameNormalized
        )) {
            throw new AccountConflictException("Profile already exists");
        }

        Instant now = clock.instant();
        ProfileRecord profile = new ProfileRecord(
                UUID.randomUUID(),
                token.userId(),
                activeProfile.selectedJobId(),
                request.type(),
                request.displayName().trim(),
                displayNameNormalized,
                imageLightUrl(request.image()),
                imageDarkUrl(request.image()),
                imageAlt(request.image()),
                ProfileStatus.ACTIVE,
                false,
                now,
                now
        );
        BankAccountRecord account = createUniqueAccount(
                profile,
                activeProfile.selectedJobId(),
                AccountType.SAVING,
                accountProperties.defaultCurrency(),
                now
        );
        try {
            profileRepository.save(profile);
            bankAccountRepository.save(account);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException("Profile, account number, or IBAN already exists");
        }
        accountOutboxService.enqueueLedgerAccountCreation(account);
        accountOutboxService.enqueueDefaultCardCreation(account, CardForm.DIGITAL);
        return new CreateProfileResponse(toProfileSummary(profile));
    }

    public ListProfilesResponse listProfiles(String authorizationHeader, ListProfilesRequest request) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        BankAccountRecord account = ownedAccount(token.userId(), request.accountNumber());
        List<ProfileRecord> profiles = profileRepository.findByUserId(token.userId(), Boolean.TRUE.equals(request.includeInactive()));
        String activeProfileId = profiles.stream()
                .filter(ProfileRecord::active)
                .map(profile -> profile.id().toString())
                .findFirst()
                .orElse(null);

        if (!account.userId().equals(token.userId())) {
            throw new AccountUnauthorizedException("Unauthorized");
        }

        return new ListProfilesResponse(profiles.stream().map(this::toProfileSummary).toList(), activeProfileId);
    }

    @Transactional
    public SetActiveProfileResponse setActiveProfile(String authorizationHeader, SetActiveProfileRequest request) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = parseProfileId(request.profileId());
        ProfileRecord profile = profileRepository.findById(profileId)
                .filter(candidate -> candidate.userId().equals(token.userId()))
                .orElseThrow(() -> new AccountNotFoundException("Profile was not found"));

        if (profile.status() != ProfileStatus.ACTIVE) {
            throw new AccountBusinessRuleException("Inactive profile cannot be activated");
        }

        Instant now = clock.instant();
        profileRepository.setActive(token.userId(), profile.id(), now);
        ProfileRecord activeProfile = profileRepository.findById(profile.id())
                .orElseThrow(() -> new AccountNotFoundException("Profile was not found"));
        return new SetActiveProfileResponse(toProfileSummary(activeProfile));
    }

    @Transactional
    public EditProfileResponse editProfile(String authorizationHeader, EditProfileRequest request) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        UUID profileId = parseProfileId(request.profileId());
        ProfileRecord profile = profileRepository.findById(profileId)
                .filter(candidate -> candidate.userId().equals(token.userId()))
                .orElseThrow(() -> new AccountNotFoundException("Profile was not found"));

        if (profile.status() != ProfileStatus.ACTIVE) {
            throw new AccountBusinessRuleException("Profile is inactive");
        }

        Instant now = clock.instant();
        String displayName = request.displayName().trim();
        ProfileRecord updatedProfile = profile.withEditableFields(
                displayName,
                normalizeDisplayName(displayName),
                imageLightUrl(request.image()),
                imageDarkUrl(request.image()),
                imageAlt(request.image()),
                now
        );

        if (profileRepository.existsByUserIdAndTypeAndDisplayNameNormalizedAndIdNot(
                token.userId(),
                updatedProfile.type(),
                updatedProfile.displayNameNormalized(),
                updatedProfile.id()
        )) {
            throw new AccountConflictException("Profile already exists, use different name");
        }

        try {
            if (!profileRepository.edit(token.userId(), updatedProfile, now)) {
                throw new AccountNotFoundException("Profile was not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException("Profile already exists, use different name");
        }
        return new EditProfileResponse(toProfileSummary(updatedProfile));
    }

    public ListAccountsResponse listAccounts(
            String authorizationHeader,
            RequestContext context,
            ListAccountsRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        ProfileRecord profile = resolveProfile(token, context);
        List<BankAccountRecord> accounts = bankAccountRepository.findByUserIdAndProfileId(
                token.userId(),
                profile.id(),
                request.types(),
                request.statuses()
        );
        return new ListAccountsResponse(accounts.stream()
                .map(account -> toAccountSummary(account, Boolean.TRUE.equals(request.includeBalance())))
                .toList());
    }

    public GetAccountBalanceResponse getBalance(
            String authorizationHeader,
            RequestContext context,
            GetAccountBalanceRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        BankAccountRecord account = ownedAccount(token.userId(), request.accountNumber());
        validateProfileContext(account, resolveProfile(token, context));
        return new GetAccountBalanceResponse(
                account.accountNumber(),
                account.iban(),
                ledgerBalanceClient.getBalance(account.accountNumber(), account.currency())
        );
    }

    @Transactional
    public CloseAccountResponse closeAccount(
            String authorizationHeader,
            RequestContext context,
            CloseAccountRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        BankAccountRecord account = ownedAccount(token.userId(), request.accountNumber());
        ProfileRecord profile = resolveProfile(token, context);
        validateProfileContext(account, profile);

        if (account.status() == AccountStatus.CLOSED) {
            throw new AccountBusinessRuleException("Account is already closed");
        }
        validateProfileCanBeClosedWithAccount(profile);
        if (!ledgerBalanceClient.isZero(account.accountNumber(), account.currency())) {
            throw new AccountBusinessRuleException("Account cannot be closed while balance is not zero");
        }

        Instant closedAt = clock.instant();
        bankAccountRepository.close(account, closedAt);
        bankAccountRepository.saveStatusHistory(new AccountStatusHistoryRecord(
                UUID.randomUUID(),
                account.id(),
                account.userId(),
                account.profileId(),
                account.status(),
                AccountStatus.CLOSED,
                request.reasonText(),
                closedAt
        ));

        boolean profileClosed = closeProfileWhenNoActiveAccountsRemain(profile, request.reasonText(), closedAt);
        accountOutboxService.enqueueLedgerAccountClosure(account);
        accountOutboxService.enqueueCardAccountClosure(account);
        return new CloseAccountResponse(
                account.accountNumber(),
                AccountStatus.CLOSED,
                closedAt,
                profile.id().toString(),
                profile.type(),
                profileClosed
        );
    }

    @Transactional
    public OpenAccountResponse openAccount(String authorizationHeader, OpenAccountRequest request) {
        VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
        BankAccountRecord account = accountToOpen(token.userId(), request == null ? null : request.accountNumber());

        if (account.status() != AccountStatus.CLOSED) {
            throw new AccountBusinessRuleException("Account is not closed");
        }

        ProfileRecord profile = profileRepository.findById(account.profileId())
                .filter(candidate -> candidate.userId().equals(token.userId()))
                .orElseThrow(() -> new AccountNotFoundException("Profile was not found"));

        Instant openedAt = clock.instant();
        profileRepository.openAndSetActive(profile, openedAt);
        BankAccountRecord openedAccount = new BankAccountRecord(
                account.id(),
                account.profileId(),
                account.userId(),
                account.selectedJobId(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                AccountStatus.ACTIVE,
                account.currency(),
                null,
                account.createdAt(),
                openedAt
        );
        ProfileRecord openedProfile = new ProfileRecord(
                profile.id(),
                profile.userId(),
                profile.selectedJobId(),
                profile.type(),
                profile.displayName(),
                profile.displayNameNormalized(),
                profile.imageLightUrl(),
                profile.imageDarkUrl(),
                profile.imageAlt(),
                ProfileStatus.ACTIVE,
                true,
                profile.createdAt(),
                openedAt
        );

        bankAccountRepository.open(account, openedAt);
        bankAccountRepository.saveStatusHistory(new AccountStatusHistoryRecord(
                UUID.randomUUID(),
                account.id(),
                account.userId(),
                account.profileId(),
                account.status(),
                AccountStatus.ACTIVE,
                request == null ? null : request.reasonText(),
                openedAt
        ));

        if (profile.status() != ProfileStatus.ACTIVE) {
            profileRepository.saveStatusHistory(new ProfileStatusHistoryRecord(
                    UUID.randomUUID(),
                    profile.id(),
                    profile.userId(),
                    profile.status(),
                    ProfileStatus.ACTIVE,
                    request == null ? null : request.reasonText(),
                    openedAt
            ));
        }
        accountOutboxService.enqueueLedgerAccountReopen(openedAccount);
        accountOutboxService.enqueueCardAccountReopen(openedAccount);

        return new OpenAccountResponse(
                toProfileSummary(openedProfile),
                toAccountSummary(openedAccount, false),
                openedAt,
                AccountCompletionStatus.KYC_REQUIRED
        );
    }

    public GetAccountOwnerResponse getOwner(String authorizationHeader, GetAccountOwnerRequest request) {
        jwtTokenService.verifyUserToken(authorizationHeader);
        if (request.destinationType() == null) {
            throw validation("data.destinationType", "destinationType is required", "DESTINATION_TYPE_REQUIRED");
        }
        if (request.destinationType() == DestinationType.CARD) {
            throw new AccountNotFoundException("Card owner lookup is not available before card-service");
        }

        BankAccountRecord account;
        if (request.accountNumber() != null && !request.accountNumber().isBlank()) {
            account = bankAccountRepository.findByAccountNumber(request.accountNumber())
                    .orElseThrow(() -> new AccountNotFoundException("Destination account was not found"));
        } else if (request.iban() != null && !request.iban().isBlank()) {
            account = bankAccountRepository.findByIban(request.iban().replace(" ", "").toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new AccountNotFoundException("Destination account was not found"));
        } else {
            throw validation("data.accountNumber", "accountNumber or iban is required", "DESTINATION_REQUIRED");
        }

        ProfileRecord profile = profileRepository.findById(account.profileId())
                .orElseThrow(() -> new AccountNotFoundException("Destination profile was not found"));
        TransferDestinationSummary destination = new TransferDestinationSummary(
                DestinationType.ACCOUNT,
                profile.displayName(),
                toImage(profile),
                account.accountNumber(),
                account.iban(),
                null
        );
        return new GetAccountOwnerResponse(destination);
    }

    public GetAccountCompletionResponse getCompletion(GetAccountCompletionRequest request) {
        UUID userId = parseUserId(request.userId());
        ProfileRecord personalProfile = profileRepository.findFirstByUserIdAndType(userId, ProfileType.PERSONAL)
                .orElse(null);
        ProfileRecord activeProfile = profileRepository.findActiveByUserId(userId)
                .orElse(personalProfile);

        if (personalProfile == null) {
            if (bankAccountRepository.existsByUserIdAndStatus(userId, AccountStatus.CLOSED)) {
                return new GetAccountCompletionResponse(AccountCompletionStatus.ACCOUNT_RE_OPEN_REQUIRED, null);
            }
            return new GetAccountCompletionResponse(AccountCompletionStatus.ACCOUNT_REQUIRED, null);
        }

        boolean hasPersonalAccount = bankAccountRepository.existsActiveByUserIdAndProfileId(userId, personalProfile.id());
        AccountCompletionStatus status;
        if (hasPersonalAccount) {
            status = AccountCompletionStatus.KYC_REQUIRED;
        } else if (bankAccountRepository.existsByUserIdAndStatus(userId, AccountStatus.CLOSED)) {
            status = AccountCompletionStatus.ACCOUNT_RE_OPEN_REQUIRED;
        } else {
            status = AccountCompletionStatus.ACCOUNT_REQUIRED;
        }
        return new GetAccountCompletionResponse(status, activeProfile == null ? null : toProfileSummary(activeProfile));
    }

    public GetCardAccountResponse getCardAccount(GetCardAccountRequest request) {
        UUID userId = parseUserId(request.userId());
        UUID profileId = parseProfileId(request.profileId());
        BankAccountRecord account = bankAccountRepository.findByUserIdAndAccountNumber(userId, request.accountNumber())
                .filter(candidate -> candidate.profileId().equals(profileId))
                .orElseThrow(() -> new AccountNotFoundException("Account was not found"));
        boolean eligible = account.type() == AccountType.SAVING && account.status() == AccountStatus.ACTIVE;
        return new GetCardAccountResponse(
                account.accountNumber(),
                account.userId().toString(),
                account.profileId().toString(),
                account.type(),
                account.status(),
                account.currency(),
                eligible
        );
    }

    private BankAccountRecord createUniqueAccount(
            ProfileRecord profile,
            String jobId,
            AccountType type,
            String currency,
            Instant now
    ) {
        for (int attempt = 0; attempt < ACCOUNT_GENERATION_ATTEMPTS; attempt++) {
            String accountNumber = accountNumberGenerator.generate();
            String iban = ibanGenerator.generate(accountNumber);
            if (!bankAccountRepository.existsByAccountNumber(accountNumber) && !bankAccountRepository.existsByIban(iban)) {
                return new BankAccountRecord(
                        UUID.randomUUID(),
                        profile.id(),
                        profile.userId(),
                        jobId,
                        accountNumber,
                        iban,
                        type,
                        AccountStatus.ACTIVE,
                        currency,
                        null,
                        now,
                        now
                );
            }
        }
        throw new AccountConflictException("Unable to generate a unique account number");
    }

    private ProfileRecord resolveProfile(VerifiedToken token, RequestContext context) {
        String requestedProfileId = context.account() == null ? null : context.account().profileId();
        if (requestedProfileId != null && !requestedProfileId.isBlank()) {
            UUID profileId = parseProfileId(requestedProfileId);
            return profileRepository.findById(profileId)
                    .filter(profile -> profile.userId().equals(token.userId()))
                    .orElseThrow(() -> new AccountNotFoundException("Profile was not found"));
        }
        if (token.profileId() != null) {
            return profileRepository.findById(token.profileId())
                    .filter(profile -> profile.userId().equals(token.userId()))
                    .orElseThrow(() -> new AccountNotFoundException("Profile was not found"));
        }
        return profileRepository.findActiveByUserId(token.userId())
                .orElseThrow(() -> new AccountBusinessRuleException("Active profile is required"));
    }

    private void validateProfileContext(BankAccountRecord account, ProfileRecord profile) {
        if (!account.profileId().equals(profile.id())) {
            throw new AccountBusinessRuleException("Account does not belong to the active profile");
        }
    }

    private void validateProfileCanBeClosedWithAccount(ProfileRecord profile) {
        if (profile.type() != ProfileType.PERSONAL) {
            return;
        }
        if (bankAccountRepository.countActiveByProfileId(profile.id()) > 1) {
            return;
        }
        if (profileRepository.countActiveByUserIdAndType(profile.userId(), ProfileType.BUSINESS) > 0) {
            throw new AccountBusinessRuleException("Personal profile cannot be closed while business profiles exist");
        }
    }

    private boolean closeProfileWhenNoActiveAccountsRemain(ProfileRecord profile, String reasonText, Instant closedAt) {
        if (profile.status() == ProfileStatus.INACTIVE || bankAccountRepository.existsActiveByProfileId(profile.id())) {
            return false;
        }

        profileRepository.close(profile, closedAt);
        profileRepository.saveStatusHistory(new ProfileStatusHistoryRecord(
                UUID.randomUUID(),
                profile.id(),
                profile.userId(),
                profile.status(),
                ProfileStatus.INACTIVE,
                reasonText,
                closedAt
        ));
        return true;
    }

    private BankAccountRecord ownedAccount(UUID userId, String accountNumber) {
        return bankAccountRepository.findByUserIdAndAccountNumber(userId, accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Account was not found"));
    }

    private BankAccountRecord accountToOpen(UUID userId, String accountNumber) {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return ownedAccount(userId, accountNumber);
        }
        return bankAccountRepository.findLatestClosedPersonalAccountByUserId(userId)
                .orElseThrow(() -> new AccountNotFoundException("Closed account was not found"));
    }

    private void validateJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw validation("data.jobId", "jobId is required", "JOB_ID_REQUIRED");
        }
        if (!kycJobCatalog.exists(jobId)) {
            throw validation("data.jobId", "jobId does not exist in the configured KYC job list", "JOB_ID_UNKNOWN");
        }
    }

    private AccountValidationException validation(String field, String message, String code) {
        return new AccountValidationException(List.of(new StatusDetail(field, message, code)));
    }

    private UUID parseProfileId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation("data.profileId", "profileId must be a UUID", "PROFILE_ID_UUID");
        }
    }

    private UUID parseUserId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation("data.userId", "userId must be a UUID", "USER_ID_UUID");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return accountProperties.defaultCurrency();
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    private AccountSummary toAccountSummary(BankAccountRecord account, boolean includeBalance) {
        BalanceSummary balance = includeBalance
                ? ledgerBalanceClient.getBalance(account.accountNumber(), account.currency())
                : null;
        return new AccountSummary(
                account.profileId().toString(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                account.status(),
                account.currency(),
                balance
        );
    }

    private ProfileSummary toProfileSummary(ProfileRecord profile) {
        return new ProfileSummary(
                profile.id().toString(),
                profile.type(),
                profile.displayName(),
                toImage(profile),
                profile.active()
        );
    }

    private Image toImage(ProfileRecord profile) {
        if (profile.imageLightUrl() == null && profile.imageDarkUrl() == null && profile.imageAlt() == null) {
            return null;
        }
        return new Image(profile.imageLightUrl(), profile.imageDarkUrl(), profile.imageAlt());
    }

    private String imageLightUrl(Image image) {
        return image == null ? null : image.lightUrl();
    }

    private String imageDarkUrl(Image image) {
        return image == null ? null : image.darkUrl();
    }

    private String imageAlt(Image image) {
        return image == null ? null : image.alt();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String normalizeDisplayName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
