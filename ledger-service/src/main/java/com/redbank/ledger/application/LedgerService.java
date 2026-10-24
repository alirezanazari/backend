package com.redbank.ledger.application;

import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.common.PageRequest;
import com.redbank.contracts.common.PageResponse;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.ledger.LedgerDirection;
import com.redbank.ledger.api.internal.dto.CloseLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.CloseLedgerAccountResponse;
import com.redbank.ledger.api.internal.dto.CreateLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.CreateLedgerAccountResponse;
import com.redbank.ledger.api.internal.dto.GetLedgerBalanceRequest;
import com.redbank.ledger.api.internal.dto.GetLedgerBalanceResponse;
import com.redbank.ledger.api.internal.dto.InternalTransferRequest;
import com.redbank.ledger.api.internal.dto.LedgerEntrySummary;
import com.redbank.ledger.api.internal.dto.LedgerTransactionResponse;
import com.redbank.ledger.api.internal.dto.ListLedgerEntriesRequest;
import com.redbank.ledger.api.internal.dto.ListLedgerEntriesResponse;
import com.redbank.ledger.api.internal.dto.ReferralRewardRequest;
import com.redbank.ledger.api.internal.dto.ReopenLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.ReopenLedgerAccountResponse;
import com.redbank.ledger.domain.LedgerAccountRecord;
import com.redbank.ledger.domain.LedgerAccountStatus;
import com.redbank.ledger.domain.LedgerBalanceRecord;
import com.redbank.ledger.domain.LedgerEntryRecord;
import com.redbank.ledger.domain.LedgerReferenceType;
import com.redbank.ledger.domain.LedgerTransactionRecord;
import com.redbank.ledger.domain.LedgerTransactionStatus;
import com.redbank.ledger.domain.LedgerTransactionType;
import com.redbank.ledger.infrastructure.LedgerAccountRepository;
import com.redbank.ledger.infrastructure.LedgerBalanceRepository;
import com.redbank.ledger.infrastructure.LedgerEntryRepository;
import com.redbank.ledger.infrastructure.LedgerTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.redbank.ledger.api.internal.dto.AccountFundingRequest;

@Service
public class LedgerService {
    private static final String SYSTEM_REFERRAL_REWARD_ACCOUNT_NUMBER = "000000000000000000";
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_OPTIMISTIC_LOCK_ATTEMPTS = 3;
    private static final String SYSTEM_ACCOUNT_FUNDING_ACCOUNT_NUMBER = "000000000000000001";

    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerBalanceRepository ledgerBalanceRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerPostingValidator ledgerPostingValidator;
    private final LedgerOutboxService ledgerOutboxService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;


    public LedgerService(
            LedgerAccountRepository ledgerAccountRepository,
            LedgerBalanceRepository ledgerBalanceRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository,
            LedgerOutboxService ledgerOutboxService,
            TransactionTemplate transactionTemplate,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerBalanceRepository = ledgerBalanceRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerOutboxService = ledgerOutboxService;
        this.ledgerPostingValidator = new LedgerPostingValidator();
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CreateLedgerAccountResponse createAccount(CreateLedgerAccountRequest request) {
        UUID ownerUserId = parseUuid(request.ownerUserId(), "data.ownerUserId", "OWNER_USER_ID_UUID");
        String currency = normalizeCurrency(request.currency());

        return ledgerAccountRepository.findByExternalAccountNumber(request.externalAccountNumber())
                .map(existing -> existingAccountResponse(existing, ownerUserId, currency))
                .orElseGet(() -> createNewAccount(request.externalAccountNumber(), ownerUserId, currency));
    }

    @Transactional
    public CloseLedgerAccountResponse closeAccount(CloseLedgerAccountRequest request) {
        LedgerAccountRecord account = ledgerAccount(request.externalAccountNumber());
        if (account.status() == LedgerAccountStatus.CLOSED) {
            recordLedgerAccount("close", "already_closed");
            return toCloseAccountResponse(account);
        }

        LedgerBalanceRecord balance = balance(account.id());
        if (balance.availableBalance().signum() != 0 || balance.currentBalance().signum() != 0) {
            throw new LedgerBusinessRuleException("Ledger balances must be zero to close");
        }

        boolean closed = ledgerAccountRepository.updateStatus(
                account.id(),
                LedgerAccountStatus.ACTIVE,
                LedgerAccountStatus.CLOSED,
                clock.instant()
        );
        if (!closed) {
            LedgerAccountRecord current = ledgerAccount(request.externalAccountNumber());
            if (current.status() != LedgerAccountStatus.CLOSED) {
                throw new LedgerConflictException("Ledger account status changed while closing");
            }
            return toCloseAccountResponse(current);
        }
        ledgerOutboxService.enqueueAccountClosed(updatedAccount(account, LedgerAccountStatus.CLOSED));
        recordLedgerAccount("close", "closed");
        return new CloseLedgerAccountResponse(account.id().toString(), LedgerAccountStatus.CLOSED.name());
    }

    @Transactional
    public ReopenLedgerAccountResponse reopenAccount(ReopenLedgerAccountRequest request) {
        LedgerAccountRecord account = ledgerAccount(request.externalAccountNumber());
        if (account.status() == LedgerAccountStatus.ACTIVE) {
            recordLedgerAccount("reopen", "already_active");
            return toReopenAccountResponse(account);
        }

        boolean reopened = ledgerAccountRepository.updateStatus(
                account.id(),
                LedgerAccountStatus.CLOSED,
                LedgerAccountStatus.ACTIVE,
                clock.instant()
        );
        if (!reopened) {
            LedgerAccountRecord current = ledgerAccount(request.externalAccountNumber());
            if (current.status() != LedgerAccountStatus.ACTIVE) {
                throw new LedgerConflictException("Ledger account status changed while reopening");
            }
            return toReopenAccountResponse(current);
        }
        ledgerOutboxService.enqueueAccountReopened(updatedAccount(account, LedgerAccountStatus.ACTIVE));
        recordLedgerAccount("reopen", "active");
        return new ReopenLedgerAccountResponse(account.id().toString(), LedgerAccountStatus.ACTIVE.name());
    }

    public LedgerTransactionResponse postInternalTransfer(InternalTransferRequest request) {
        String referenceId = normalizeRequired(request.referenceId(), "data.referenceId", "REFERENCE_ID_REQUIRED");
        String idempotencyKey = normalizeRequired(request.idempotencyKey(), "data.idempotencyKey", "IDEMPOTENCY_KEY_REQUIRED");
        BigDecimal amount = normalizeAmount(request.amount(), "data.amount");

        if (request.sourceExternalAccountNumber().equals(request.destinationExternalAccountNumber())) {
            throw new LedgerBusinessRuleException("Source and destination accounts must be different");
        }

        return executePostingWithRetry(
                LedgerReferenceType.TRANSFER,
                referenceId,
                idempotencyKey,
                () -> {
                    LedgerTransactionResponse duplicate = duplicateTransferResponse(referenceId, idempotencyKey);
                    if (duplicate != null) {
                        return duplicate;
                    }

                    LedgerAccountRecord source = activeAccount(request.sourceExternalAccountNumber());
                    LedgerAccountRecord destination = activeAccount(request.destinationExternalAccountNumber());
                    validateSameCurrency(source, request.amount().currency(), "Source account currency does not match transfer amount");
                    validateSameCurrency(destination, request.amount().currency(), "Destination account currency does not match transfer amount");

                    Instant now = clock.instant();
                    LedgerTransactionRecord transaction = new LedgerTransactionRecord(
                            UUID.randomUUID(),
                            LedgerReferenceType.TRANSFER,
                            referenceId,
                            idempotencyKey,
                            LedgerTransactionType.INTERNAL_TRANSFER,
                            LedgerTransactionStatus.POSTED,
                            now,
                            now
                    );
                    List<LedgerEntryRecord> entries = List.of(
                            entry(transaction.id(), source.id(), LedgerDirection.DEBIT, amount, source.currency(), now),
                            entry(transaction.id(), destination.id(), LedgerDirection.CREDIT, amount, destination.currency(), now)
                    );

                    return postBalancedTransaction(transaction, entries);
                }
        );
    }

    public LedgerTransactionResponse postReferralReward(ReferralRewardRequest request) {
        String rewardId = normalizeRequired(request.referralRewardId(), "data.referralRewardId", "REFERRAL_REWARD_ID_REQUIRED");
        BigDecimal amount = normalizeAmount(request.amount(), "data.amount");

        return executePostingWithRetry(
                LedgerReferenceType.REFERRAL_REWARD,
                rewardId,
                null,
                () -> {
                    LedgerTransactionResponse duplicate = ledgerTransactionRepository
                            .findByReference(LedgerReferenceType.REFERRAL_REWARD, rewardId)
                            .map(this::toTransactionResponse)
                            .orElse(null);
                    if (duplicate != null) {
                        return duplicate;
                    }

                    LedgerAccountRecord rewardPool = activeAccount(SYSTEM_REFERRAL_REWARD_ACCOUNT_NUMBER);
                    LedgerAccountRecord referrer = activeAccount(request.referrerExternalAccountNumber());
                    validateSameCurrency(rewardPool, request.amount().currency(), "Reward pool currency does not match reward amount");
                    validateSameCurrency(referrer, request.amount().currency(), "Referrer account currency does not match reward amount");

                    Instant now = clock.instant();
                    LedgerTransactionRecord transaction = new LedgerTransactionRecord(
                            UUID.randomUUID(),
                            LedgerReferenceType.REFERRAL_REWARD,
                            rewardId,
                            null,
                            LedgerTransactionType.REFERRAL_REWARD,
                            LedgerTransactionStatus.POSTED,
                            now,
                            now
                    );
                    List<LedgerEntryRecord> entries = List.of(
                            entry(transaction.id(), rewardPool.id(), LedgerDirection.DEBIT, amount, rewardPool.currency(), now),
                            entry(transaction.id(), referrer.id(), LedgerDirection.CREDIT, amount, referrer.currency(), now)
                    );

                    return postBalancedTransaction(transaction, entries);
                }
        );
    }
    public LedgerTransactionResponse postAccountFunding(AccountFundingRequest request) {
        String fundingId = normalizeRequired(
                request.fundingId(),
                "data.fundingId",
                "FUNDING_ID_REQUIRED"
        );

        BigDecimal amount = normalizeAmount(request.amount(), "data.amount");

        return executePostingWithRetry(
                LedgerReferenceType.ACCOUNT_FUNDING,
                fundingId,
                null,
                () -> {
                    LedgerTransactionResponse duplicate = ledgerTransactionRepository
                            .findByReference(LedgerReferenceType.ACCOUNT_FUNDING, fundingId)
                            .map(this::toTransactionResponse)
                            .orElse(null);

                    if (duplicate != null) {
                        return duplicate;
                    }

                    LedgerAccountRecord fundingSource =
                            activeAccount(SYSTEM_ACCOUNT_FUNDING_ACCOUNT_NUMBER);

                    LedgerAccountRecord target =
                            activeAccount(request.targetExternalAccountNumber());

                    validateSameCurrency(
                            fundingSource,
                            request.amount().currency(),
                            "Funding account currency does not match funding amount"
                    );

                    validateSameCurrency(
                            target,
                            request.amount().currency(),
                            "Target account currency does not match funding amount"
                    );

                    Instant now = clock.instant();

                    LedgerTransactionRecord transaction = new LedgerTransactionRecord(
                            UUID.randomUUID(),
                            LedgerReferenceType.ACCOUNT_FUNDING,
                            fundingId,
                            null,
                            LedgerTransactionType.ACCOUNT_FUNDING,
                            LedgerTransactionStatus.POSTED,
                            now,
                            now
                    );

                    List<LedgerEntryRecord> entries = List.of(
                            entry(
                                    transaction.id(),
                                    fundingSource.id(),
                                    LedgerDirection.DEBIT,
                                    amount,
                                    fundingSource.currency(),
                                    now
                            ),
                            entry(
                                    transaction.id(),
                                    target.id(),
                                    LedgerDirection.CREDIT,
                                    amount,
                                    target.currency(),
                                    now
                            )
                    );

                    return postBalancedTransaction(transaction, entries);
                }
        );
    }

    public GetLedgerBalanceResponse getBalance(GetLedgerBalanceRequest request) {
        LedgerAccountRecord account = activeAccount(request.externalAccountNumber());
        return new GetLedgerBalanceResponse(
                account.externalAccountNumber(),
                account.id().toString(),
                toBalanceSummary(balance(account.id()))
        );
    }

    public ListLedgerEntriesResponse listEntries(ListLedgerEntriesRequest request) {
        LedgerAccountRecord account = activeAccount(request.externalAccountNumber());
        int page = page(request.page());
        int size = size(request.page());
        Instant fromInclusive = startOfDay(request.fromDate());
        Instant toExclusive = endExclusive(request.toDate());
        if (fromInclusive != null && toExclusive != null && !fromInclusive.isBefore(toExclusive)) {
            throw validation("data.fromDate", "fromDate must be before or equal to toDate", "DATE_RANGE_INVALID");
        }

        List<LedgerEntryRecord> entries = ledgerEntryRepository.findByAccountId(
                account.id(),
                fromInclusive,
                toExclusive,
                page,
                size
        );
        long totalItems = ledgerEntryRepository.countByAccountId(account.id(), fromInclusive, toExclusive);
        PageResponse pageResponse = new PageResponse(page, size, totalItems, ((long) (page + 1) * size) < totalItems);
        return new ListLedgerEntriesResponse(entries.stream().map(this::toEntrySummary).toList(), pageResponse);
    }

    private CreateLedgerAccountResponse createNewAccount(String externalAccountNumber, UUID ownerUserId, String currency) {
        Instant now = clock.instant();
        LedgerAccountRecord account = new LedgerAccountRecord(
                UUID.randomUUID(),
                externalAccountNumber,
                ownerUserId,
                currency,
                LedgerAccountStatus.ACTIVE,
                false,
                now,
                now
        );
        try {
            ledgerAccountRepository.save(account);
            ledgerBalanceRepository.saveInitialBalance(account.id(), account.currency(), now);
        } catch (DataIntegrityViolationException exception) {
            LedgerAccountRecord existing = ledgerAccountRepository.findByExternalAccountNumber(externalAccountNumber)
                    .orElseThrow(() -> new LedgerConflictException("Ledger account already exists"));
            return existingAccountResponse(existing, ownerUserId, currency);
        }
        ledgerOutboxService.enqueueAccountCreated(account);
        recordLedgerAccount("create", "created");
        return toCreateAccountResponse(account);
    }

    private CreateLedgerAccountResponse existingAccountResponse(
            LedgerAccountRecord existing,
            UUID ownerUserId,
            String currency
    ) {
        if (!existing.ownerUserId().equals(ownerUserId) || !existing.currency().equals(currency)) {
            throw new LedgerConflictException("Ledger account exists with different owner or currency");
        }
        return toCreateAccountResponse(existing);
    }

    private LedgerTransactionResponse duplicateTransferResponse(String referenceId, String idempotencyKey) {
        LedgerTransactionRecord byReference = ledgerTransactionRepository
                .findByReference(LedgerReferenceType.TRANSFER, referenceId)
                .orElse(null);
        if (byReference != null) {
            return toTransactionResponse(byReference);
        }

        LedgerTransactionRecord byIdempotencyKey = ledgerTransactionRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (byIdempotencyKey == null) {
            return null;
        }
        if (byIdempotencyKey.referenceType() != LedgerReferenceType.TRANSFER
                || !byIdempotencyKey.referenceId().equals(referenceId)) {
            throw new LedgerConflictException("Idempotency key was already used for another ledger transaction");
        }
        return toTransactionResponse(byIdempotencyKey);
    }

    private LedgerTransactionResponse postBalancedTransaction(
            LedgerTransactionRecord transaction,
            List<LedgerEntryRecord> entries
    ) {
        validateBalanced(entries);
        ledgerTransactionRepository.save(transaction);

        for (LedgerEntryRecord entry : entries) {
            applyEntry(entry);
        }
        ledgerEntryRepository.saveAll(entries);
        ledgerOutboxService.enqueueTransactionPosted(transaction);
        recordLedgerTransaction(transaction.referenceType(), transaction.transactionType(), "posted");
        return toTransactionResponse(transaction);
    }

    private void validateBalanced(List<LedgerEntryRecord> entries) {
        ledgerPostingValidator.validate(entries);
    }

    private void applyEntry(LedgerEntryRecord entry) {
        LedgerAccountRecord account = ledgerAccountRepository.findById(entry.ledgerAccountId())
                .orElseThrow(() -> new LedgerNotFoundException("Ledger account was not found"));
        LedgerBalanceRecord previous = balance(account.id());
        BigDecimal newAvailableBalance = apply(previous.availableBalance(), entry.direction(), entry.amount());
        BigDecimal newCurrentBalance = apply(previous.currentBalance(), entry.direction(), entry.amount());

        if (!account.allowNegativeBalance()
                && (newAvailableBalance.signum() < 0 || newCurrentBalance.signum() < 0)) {
            throw new LedgerInsufficientFundsException("Insufficient funds");
        }

        boolean updated = ledgerBalanceRepository.update(
                account.id(),
                newAvailableBalance,
                newCurrentBalance,
                previous.version(),
                entry.createdAt()
        );
        if (!updated) {
            throw new LedgerOptimisticLockException();
        }
    }

    private LedgerTransactionResponse executePostingWithRetry(
            LedgerReferenceType referenceType,
            String referenceId,
            String idempotencyKey,
            LedgerPostingOperation operation
    ) {
        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> operation.post());
            } catch (LedgerOptimisticLockException exception) {
                recordLedgerPostingRetry(referenceType, "optimistic_lock");
                if (attempt == MAX_OPTIMISTIC_LOCK_ATTEMPTS) {
                    throw new LedgerBusinessRuleException("Ledger balance changed while posting transaction; retry limit reached");
                }
            } catch (DataIntegrityViolationException exception) {
                recordLedgerPostingRetry(referenceType, "integrity_violation");
                return transactionTemplate.execute(status -> duplicateResponseAfterIntegrityViolation(
                        referenceType,
                        referenceId,
                        idempotencyKey
                ));
            }
        }
        throw new LedgerBusinessRuleException("Ledger transaction could not be posted");
    }

    private void recordLedgerAccount(String operation, String outcome) {
        meterRegistry.counter(
                "redbank.ledger.accounts",
                "operation",
                operation,
                "outcome",
                outcome
        ).increment();
    }

    private void recordLedgerTransaction(
            LedgerReferenceType referenceType,
            LedgerTransactionType transactionType,
            String outcome
    ) {
        meterRegistry.counter(
                "redbank.ledger.transactions",
                "reference.type",
                referenceType.name().toLowerCase(Locale.ROOT),
                "transaction.type",
                transactionType.name().toLowerCase(Locale.ROOT),
                "outcome",
                outcome
        ).increment();
    }

    private void recordLedgerPostingRetry(LedgerReferenceType referenceType, String reason) {
        meterRegistry.counter(
                "redbank.ledger.posting.retries",
                "reference.type",
                referenceType.name().toLowerCase(Locale.ROOT),
                "reason",
                reason
        ).increment();
    }

    private LedgerTransactionResponse duplicateResponseAfterIntegrityViolation(
            LedgerReferenceType referenceType,
            String referenceId,
            String idempotencyKey
    ) {
        LedgerTransactionRecord byReference = ledgerTransactionRepository.findByReference(referenceType, referenceId)
                .orElse(null);
        if (byReference != null) {
            return toTransactionResponse(byReference);
        }
        LedgerTransactionRecord byIdempotencyKey = ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);
        if (byIdempotencyKey != null) {
            throw new LedgerConflictException("Idempotency key was already used for another ledger transaction");
        }
        throw new LedgerConflictException("Duplicate ledger transaction");
    }

    private BigDecimal apply(BigDecimal balance, LedgerDirection direction, BigDecimal amount) {
        return direction == LedgerDirection.DEBIT ? balance.subtract(amount) : balance.add(amount);
    }

    private LedgerEntryRecord entry(
            UUID transactionId,
            UUID accountId,
            LedgerDirection direction,
            BigDecimal amount,
            String currency,
            Instant createdAt
    ) {
        return new LedgerEntryRecord(UUID.randomUUID(), transactionId, accountId, direction, amount, currency, createdAt);
    }

    private LedgerAccountRecord activeAccount(String externalAccountNumber) {
        LedgerAccountRecord account = ledgerAccount(externalAccountNumber);
        if (account.status() != LedgerAccountStatus.ACTIVE) {
            throw new LedgerBusinessRuleException("Ledger account is not active");
        }
        return account;
    }

    private LedgerAccountRecord ledgerAccount(String externalAccountNumber) {
        return ledgerAccountRepository.findByExternalAccountNumber(externalAccountNumber)
                .orElseThrow(() -> new LedgerNotFoundException("Ledger account was not found"));
    }

    private CloseLedgerAccountResponse toCloseAccountResponse(LedgerAccountRecord account) {
        return new CloseLedgerAccountResponse(account.id().toString(), LedgerAccountStatus.CLOSED.name());
    }

    private ReopenLedgerAccountResponse toReopenAccountResponse(LedgerAccountRecord account) {
        return new ReopenLedgerAccountResponse(account.id().toString(), LedgerAccountStatus.ACTIVE.name());
    }

    private LedgerBalanceRecord balance(UUID ledgerAccountId) {
        return ledgerBalanceRepository.findByAccountId(ledgerAccountId)
                .orElseThrow(() -> new LedgerNotFoundException("Ledger balance was not found"));
    }

    private void validateSameCurrency(LedgerAccountRecord account, String currency, String message) {
        if (!account.currency().equals(normalizeCurrency(currency))) {
            throw new LedgerBusinessRuleException(message);
        }
    }

    private BigDecimal normalizeAmount(Money money, String field) {
        if (money == null || money.amount() == null) {
            throw validation(field, "amount is required", "AMOUNT_REQUIRED");
        }
        if (money.currency() == null || money.currency().isBlank()) {
            throw validation(field + ".currency", "currency is required", "CURRENCY_REQUIRED");
        }
        try {
            BigDecimal amount = money.amount().setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() <= 0) {
                throw validation(field + ".amount", "amount must be greater than zero", "AMOUNT_POSITIVE_REQUIRED");
            }
            return amount;
        } catch (ArithmeticException exception) {
            throw validation(field + ".amount", "amount supports maximum 2 decimal places", "AMOUNT_SCALE_INVALID");
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw validation("data.currency", "currency is required", "CURRENCY_REQUIRED");
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String field, String code) {
        if (value == null || value.isBlank()) {
            throw validation(field, field.substring(field.lastIndexOf('.') + 1) + " is required", code);
        }
        return value.trim();
    }

    private UUID parseUuid(String value, String field, String code) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation(field, field.substring(field.lastIndexOf('.') + 1) + " must be a UUID", code);
        }
    }

    private LedgerValidationException validation(String field, String message, String code) {
        return new LedgerValidationException(List.of(new StatusDetail(field, message, code)));
    }

    private int page(PageRequest pageRequest) {
        if (pageRequest == null || pageRequest.page() == null) {
            return DEFAULT_PAGE;
        }
        return Math.max(pageRequest.page(), 0);
    }

    private int size(PageRequest pageRequest) {
        if (pageRequest == null || pageRequest.size() == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(pageRequest.size(), 1), MAX_PAGE_SIZE);
    }

    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant endExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private CreateLedgerAccountResponse toCreateAccountResponse(LedgerAccountRecord account) {
        return new CreateLedgerAccountResponse(account.id().toString(), account.status().name());
    }

    private LedgerAccountRecord updatedAccount(LedgerAccountRecord account, LedgerAccountStatus status) {
        return new LedgerAccountRecord(
                account.id(),
                account.externalAccountNumber(),
                account.ownerUserId(),
                account.currency(),
                status,
                account.allowNegativeBalance(),
                account.createdAt(),
                clock.instant()
        );
    }

    private LedgerTransactionResponse toTransactionResponse(LedgerTransactionRecord transaction) {
        return new LedgerTransactionResponse(transaction.id().toString(), transaction.status().name());
    }

    private BalanceSummary toBalanceSummary(LedgerBalanceRecord balance) {
        Money available = new Money(balance.availableBalance(), balance.currency());
        Money current = new Money(balance.currentBalance(), balance.currency());
        return new BalanceSummary(available, current, balance.updatedAt());
    }

    private LedgerEntrySummary toEntrySummary(LedgerEntryRecord entry) {
        return new LedgerEntrySummary(
                entry.id().toString(),
                entry.ledgerTransactionId().toString(),
                entry.direction(),
                new Money(entry.amount(), entry.currency()),
                entry.createdAt()
        );
    }

    private interface LedgerPostingOperation {
        LedgerTransactionResponse post();
    }

    private static class LedgerOptimisticLockException extends RuntimeException {
    }
}
