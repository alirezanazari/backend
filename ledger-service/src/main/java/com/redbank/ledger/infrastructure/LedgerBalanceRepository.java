package com.redbank.ledger.infrastructure;

import com.redbank.ledger.domain.LedgerBalanceRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.ledger.jooq.tables.LedgerBalances.LEDGER_BALANCES;

@Repository
public class LedgerBalanceRepository {
    private final DSLContext dsl;

    public LedgerBalanceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void saveInitialBalance(UUID ledgerAccountId, String currency, Instant updatedAt) {
        dsl.insertInto(LEDGER_BALANCES)
                .set(LEDGER_BALANCES.LEDGER_ACCOUNT_ID, ledgerAccountId)
                .set(LEDGER_BALANCES.AVAILABLE_BALANCE, BigDecimal.ZERO)
                .set(LEDGER_BALANCES.CURRENT_BALANCE, BigDecimal.ZERO)
                .set(LEDGER_BALANCES.CURRENCY, currency)
                .set(LEDGER_BALANCES.UPDATED_AT, toOffsetDateTime(updatedAt))
                .set(LEDGER_BALANCES.VERSION, 0L)
                .execute();
    }

    public Optional<LedgerBalanceRecord> findByAccountId(UUID ledgerAccountId) {
        return dsl.selectFrom(LEDGER_BALANCES)
                .where(LEDGER_BALANCES.LEDGER_ACCOUNT_ID.eq(ledgerAccountId))
                .fetchOptional(this::map);
    }

    public boolean update(
            UUID ledgerAccountId,
            BigDecimal availableBalance,
            BigDecimal currentBalance,
            long expectedVersion,
            Instant updatedAt
    ) {
        return dsl.update(LEDGER_BALANCES)
                .set(LEDGER_BALANCES.AVAILABLE_BALANCE, availableBalance)
                .set(LEDGER_BALANCES.CURRENT_BALANCE, currentBalance)
                .set(LEDGER_BALANCES.UPDATED_AT, toOffsetDateTime(updatedAt))
                .set(LEDGER_BALANCES.VERSION, LEDGER_BALANCES.VERSION.add(1L))
                .where(LEDGER_BALANCES.LEDGER_ACCOUNT_ID.eq(ledgerAccountId))
                .and(LEDGER_BALANCES.VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    private LedgerBalanceRecord map(Record record) {
        return new LedgerBalanceRecord(
                record.get(LEDGER_BALANCES.LEDGER_ACCOUNT_ID),
                record.get(LEDGER_BALANCES.AVAILABLE_BALANCE),
                record.get(LEDGER_BALANCES.CURRENT_BALANCE),
                record.get(LEDGER_BALANCES.CURRENCY),
                record.get(LEDGER_BALANCES.UPDATED_AT).toInstant(),
                record.get(LEDGER_BALANCES.VERSION)
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
