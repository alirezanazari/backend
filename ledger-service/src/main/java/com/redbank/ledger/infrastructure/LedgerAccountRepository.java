package com.redbank.ledger.infrastructure;

import com.redbank.ledger.domain.LedgerAccountRecord;
import com.redbank.ledger.domain.LedgerAccountStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.ledger.jooq.tables.LedgerAccounts.LEDGER_ACCOUNTS;

@Repository
public class LedgerAccountRepository {
    private final DSLContext dsl;

    public LedgerAccountRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(LedgerAccountRecord account) {
        dsl.insertInto(LEDGER_ACCOUNTS)
                .set(LEDGER_ACCOUNTS.ID, account.id())
                .set(LEDGER_ACCOUNTS.EXTERNAL_ACCOUNT_NUMBER, account.externalAccountNumber())
                .set(LEDGER_ACCOUNTS.OWNER_USER_ID, account.ownerUserId())
                .set(LEDGER_ACCOUNTS.CURRENCY, account.currency())
                .set(LEDGER_ACCOUNTS.STATUS, account.status().name())
                .set(LEDGER_ACCOUNTS.ALLOW_NEGATIVE_BALANCE, account.allowNegativeBalance())
                .set(LEDGER_ACCOUNTS.CREATED_AT, toOffsetDateTime(account.createdAt()))
                .set(LEDGER_ACCOUNTS.UPDATED_AT, toOffsetDateTime(account.updatedAt()))
                .execute();
    }

    public boolean updateStatus(
            UUID accountId,
            LedgerAccountStatus expectedStatus,
            LedgerAccountStatus targetStatus,
            Instant updatedAt
    ) {
        return dsl.update(LEDGER_ACCOUNTS)
                .set(LEDGER_ACCOUNTS.STATUS, targetStatus.name())
                .set(LEDGER_ACCOUNTS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(LEDGER_ACCOUNTS.ID.eq(accountId))
                .and(LEDGER_ACCOUNTS.STATUS.eq(expectedStatus.name()))
                .execute() == 1;
    }

    public Optional<LedgerAccountRecord> findByExternalAccountNumber(String externalAccountNumber) {
        return findOne(LEDGER_ACCOUNTS.EXTERNAL_ACCOUNT_NUMBER.eq(externalAccountNumber));
    }

    public Optional<LedgerAccountRecord> findById(UUID id) {
        return findOne(LEDGER_ACCOUNTS.ID.eq(id));
    }

    private Optional<LedgerAccountRecord> findOne(Condition condition) {
        return dsl.selectFrom(LEDGER_ACCOUNTS)
                .where(condition)
                .fetchOptional(this::map);
    }

    private LedgerAccountRecord map(Record record) {
        return new LedgerAccountRecord(
                record.get(LEDGER_ACCOUNTS.ID),
                record.get(LEDGER_ACCOUNTS.EXTERNAL_ACCOUNT_NUMBER),
                record.get(LEDGER_ACCOUNTS.OWNER_USER_ID),
                record.get(LEDGER_ACCOUNTS.CURRENCY),
                LedgerAccountStatus.valueOf(record.get(LEDGER_ACCOUNTS.STATUS)),
                record.get(LEDGER_ACCOUNTS.ALLOW_NEGATIVE_BALANCE),
                record.get(LEDGER_ACCOUNTS.CREATED_AT).toInstant(),
                record.get(LEDGER_ACCOUNTS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
