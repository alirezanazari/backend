package com.redbank.account.infrastructure;

import com.redbank.account.domain.AccountStatusHistoryRecord;
import com.redbank.account.domain.BankAccountRecord;
import com.redbank.account.domain.ProfileStatus;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.redbank.account.jooq.tables.AccountStatusHistory.ACCOUNT_STATUS_HISTORY;
import static com.redbank.account.jooq.tables.BankAccounts.BANK_ACCOUNTS;
import static com.redbank.account.jooq.tables.Profiles.PROFILES;

@Repository
public class BankAccountRepository {
    private final DSLContext dsl;

    public BankAccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(BankAccountRecord account) {
        dsl.insertInto(BANK_ACCOUNTS)
                .set(BANK_ACCOUNTS.ID, account.id())
                .set(BANK_ACCOUNTS.PROFILE_ID, account.profileId())
                .set(BANK_ACCOUNTS.USER_ID, account.userId())
                .set(BANK_ACCOUNTS.SELECTED_JOB_ID, account.selectedJobId())
                .set(BANK_ACCOUNTS.ACCOUNT_NUMBER, account.accountNumber())
                .set(BANK_ACCOUNTS.IBAN, account.iban())
                .set(BANK_ACCOUNTS.TYPE, account.type().name())
                .set(BANK_ACCOUNTS.STATUS, account.status().name())
                .set(BANK_ACCOUNTS.CURRENCY, account.currency())
                .set(BANK_ACCOUNTS.CLOSED_AT, toOffsetDateTime(account.closedAt()))
                .set(BANK_ACCOUNTS.CREATED_AT, toOffsetDateTime(account.createdAt()))
                .set(BANK_ACCOUNTS.UPDATED_AT, toOffsetDateTime(account.updatedAt()))
                .execute();
    }

    public boolean existsByAccountNumber(String accountNumber) {
        return dsl.fetchExists(BANK_ACCOUNTS, BANK_ACCOUNTS.ACCOUNT_NUMBER.eq(accountNumber));
    }

    public boolean existsByIban(String iban) {
        return dsl.fetchExists(BANK_ACCOUNTS, BANK_ACCOUNTS.IBAN.eq(iban));
    }

    public Optional<BankAccountRecord> findByAccountNumber(String accountNumber) {
        return findOne(BANK_ACCOUNTS.ACCOUNT_NUMBER.eq(accountNumber));
    }

    public Optional<BankAccountRecord> findByIban(String iban) {
        return findOne(BANK_ACCOUNTS.IBAN.eq(iban));
    }

    public Optional<BankAccountRecord> findByUserIdAndAccountNumber(UUID userId, String accountNumber) {
        return findOne(BANK_ACCOUNTS.USER_ID.eq(userId)
                .and(BANK_ACCOUNTS.ACCOUNT_NUMBER.eq(accountNumber)));
    }

    public Optional<BankAccountRecord> findActiveReferralRewardAccount(UUID userId, String currency) {
        return dsl.select(BANK_ACCOUNTS.fields())
                .from(BANK_ACCOUNTS)
                .join(PROFILES).on(PROFILES.ID.eq(BANK_ACCOUNTS.PROFILE_ID))
                .where(BANK_ACCOUNTS.USER_ID.eq(userId))
                .and(BANK_ACCOUNTS.STATUS.eq(AccountStatus.ACTIVE.name()))
                .and(BANK_ACCOUNTS.CURRENCY.eq(currency))
                .and(PROFILES.STATUS.eq(ProfileStatus.ACTIVE.name()))
                .orderBy(
                        PROFILES.IS_ACTIVE.desc(),
                        DSL.when(PROFILES.TYPE.eq(ProfileType.PERSONAL.name()), 0).otherwise(1),
                        BANK_ACCOUNTS.CREATED_AT.asc()
                )
                .limit(1)
                .fetchOptional(this::map);
    }

    public Optional<BankAccountRecord> findLatestClosedPersonalAccountByUserId(UUID userId) {
        return dsl.select(BANK_ACCOUNTS.fields())
                .from(BANK_ACCOUNTS)
                .join(PROFILES).on(PROFILES.ID.eq(BANK_ACCOUNTS.PROFILE_ID))
                .where(BANK_ACCOUNTS.USER_ID.eq(userId))
                .and(BANK_ACCOUNTS.STATUS.eq(AccountStatus.CLOSED.name()))
                .and(PROFILES.TYPE.eq(ProfileType.PERSONAL.name()))
                .orderBy(BANK_ACCOUNTS.UPDATED_AT.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    public boolean existsActiveByUserIdAndProfileId(UUID userId, UUID profileId) {
        return dsl.fetchExists(
                BANK_ACCOUNTS,
                BANK_ACCOUNTS.USER_ID.eq(userId)
                        .and(BANK_ACCOUNTS.PROFILE_ID.eq(profileId))
                        .and(BANK_ACCOUNTS.STATUS.eq(AccountStatus.ACTIVE.name()))
        );
    }

    public boolean existsByUserIdAndStatus(UUID userId, AccountStatus status) {
        return dsl.fetchExists(
                BANK_ACCOUNTS,
                BANK_ACCOUNTS.USER_ID.eq(userId).and(BANK_ACCOUNTS.STATUS.eq(status.name()))
        );
    }

    public int countActiveByProfileId(UUID profileId) {
        return dsl.fetchCount(
                BANK_ACCOUNTS,
                BANK_ACCOUNTS.PROFILE_ID.eq(profileId)
                        .and(BANK_ACCOUNTS.STATUS.eq(AccountStatus.ACTIVE.name()))
        );
    }

    public boolean existsActiveByProfileId(UUID profileId) {
        return countActiveByProfileId(profileId) > 0;
    }

    public List<BankAccountRecord> findByUserIdAndProfileId(
            UUID userId,
            UUID profileId,
            List<AccountType> types,
            List<AccountStatus> statuses
    ) {
        Condition condition = BANK_ACCOUNTS.USER_ID.eq(userId)
                .and(BANK_ACCOUNTS.PROFILE_ID.eq(profileId));
        if (types != null && !types.isEmpty()) {
            condition = condition.and(BANK_ACCOUNTS.TYPE.in(types.stream().map(Enum::name).toList()));
        }
        if (statuses != null && !statuses.isEmpty()) {
            condition = condition.and(BANK_ACCOUNTS.STATUS.in(statuses.stream().map(Enum::name).toList()));
        }
        return dsl.selectFrom(BANK_ACCOUNTS)
                .where(condition)
                .orderBy(BANK_ACCOUNTS.CREATED_AT.asc())
                .fetch(this::map);
    }

    public void close(BankAccountRecord account, Instant closedAt) {
        OffsetDateTime timestamp = toOffsetDateTime(closedAt);
        dsl.update(BANK_ACCOUNTS)
                .set(BANK_ACCOUNTS.STATUS, AccountStatus.CLOSED.name())
                .set(BANK_ACCOUNTS.CLOSED_AT, timestamp)
                .set(BANK_ACCOUNTS.UPDATED_AT, timestamp)
                .where(BANK_ACCOUNTS.ID.eq(account.id()))
                .execute();
    }

    public void open(BankAccountRecord account, Instant openedAt) {
        dsl.update(BANK_ACCOUNTS)
                .set(BANK_ACCOUNTS.STATUS, AccountStatus.ACTIVE.name())
                .setNull(BANK_ACCOUNTS.CLOSED_AT)
                .set(BANK_ACCOUNTS.UPDATED_AT, toOffsetDateTime(openedAt))
                .where(BANK_ACCOUNTS.ID.eq(account.id()))
                .execute();
    }

    public void saveStatusHistory(AccountStatusHistoryRecord history) {
        dsl.insertInto(ACCOUNT_STATUS_HISTORY)
                .set(ACCOUNT_STATUS_HISTORY.ID, history.id())
                .set(ACCOUNT_STATUS_HISTORY.ACCOUNT_ID, history.accountId())
                .set(ACCOUNT_STATUS_HISTORY.USER_ID, history.userId())
                .set(ACCOUNT_STATUS_HISTORY.PROFILE_ID, history.profileId())
                .set(ACCOUNT_STATUS_HISTORY.PREVIOUS_STATUS, history.previousStatus().name())
                .set(ACCOUNT_STATUS_HISTORY.NEW_STATUS, history.newStatus().name())
                .set(ACCOUNT_STATUS_HISTORY.REASON_TEXT, history.reasonText())
                .set(ACCOUNT_STATUS_HISTORY.CREATED_AT, toOffsetDateTime(history.createdAt()))
                .execute();
    }

    private Optional<BankAccountRecord> findOne(Condition condition) {
        return dsl.selectFrom(BANK_ACCOUNTS)
                .where(condition)
                .fetchOptional(this::map);
    }

    private BankAccountRecord map(Record record) {
        return new BankAccountRecord(
                record.get(BANK_ACCOUNTS.ID),
                record.get(BANK_ACCOUNTS.PROFILE_ID),
                record.get(BANK_ACCOUNTS.USER_ID),
                record.get(BANK_ACCOUNTS.SELECTED_JOB_ID),
                record.get(BANK_ACCOUNTS.ACCOUNT_NUMBER),
                record.get(BANK_ACCOUNTS.IBAN),
                AccountType.valueOf(record.get(BANK_ACCOUNTS.TYPE)),
                AccountStatus.valueOf(record.get(BANK_ACCOUNTS.STATUS)),
                record.get(BANK_ACCOUNTS.CURRENCY),
                toInstant(record.get(BANK_ACCOUNTS.CLOSED_AT)),
                record.get(BANK_ACCOUNTS.CREATED_AT).toInstant(),
                record.get(BANK_ACCOUNTS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
