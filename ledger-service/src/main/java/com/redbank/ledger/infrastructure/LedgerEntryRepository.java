package com.redbank.ledger.infrastructure;

import com.redbank.contracts.ledger.LedgerDirection;
import com.redbank.ledger.domain.LedgerEntryRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.redbank.ledger.jooq.tables.LedgerEntries.LEDGER_ENTRIES;

@Repository
public class LedgerEntryRepository {
    private final DSLContext dsl;

    public LedgerEntryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void saveAll(List<LedgerEntryRecord> entries) {
        for (LedgerEntryRecord entry : entries) {
            dsl.insertInto(LEDGER_ENTRIES)
                    .set(LEDGER_ENTRIES.ID, entry.id())
                    .set(LEDGER_ENTRIES.LEDGER_TRANSACTION_ID, entry.ledgerTransactionId())
                    .set(LEDGER_ENTRIES.LEDGER_ACCOUNT_ID, entry.ledgerAccountId())
                    .set(LEDGER_ENTRIES.DIRECTION, entry.direction().name())
                    .set(LEDGER_ENTRIES.AMOUNT, entry.amount())
                    .set(LEDGER_ENTRIES.CURRENCY, entry.currency())
                    .set(LEDGER_ENTRIES.CREATED_AT, toOffsetDateTime(entry.createdAt()))
                    .execute();
        }
    }

    public List<LedgerEntryRecord> findByAccountId(
            UUID ledgerAccountId,
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size
    ) {
        return dsl.selectFrom(LEDGER_ENTRIES)
                .where(filters(ledgerAccountId, fromInclusive, toExclusive))
                .orderBy(LEDGER_ENTRIES.CREATED_AT.desc(), LEDGER_ENTRIES.ID.desc())
                .limit(size)
                .offset(page * size)
                .fetch(this::map);
    }

    public long countByAccountId(UUID ledgerAccountId, Instant fromInclusive, Instant toExclusive) {
        Long count = dsl.select(DSL.count().cast(Long.class))
                .from(LEDGER_ENTRIES)
                .where(filters(ledgerAccountId, fromInclusive, toExclusive))
                .fetchOne(0, Long.class);
        return count == null ? 0L : count;
    }

    private Condition filters(UUID ledgerAccountId, Instant fromInclusive, Instant toExclusive) {
        Condition condition = LEDGER_ENTRIES.LEDGER_ACCOUNT_ID.eq(ledgerAccountId);
        if (fromInclusive != null) {
            condition = condition.and(LEDGER_ENTRIES.CREATED_AT.ge(toOffsetDateTime(fromInclusive)));
        }
        if (toExclusive != null) {
            condition = condition.and(LEDGER_ENTRIES.CREATED_AT.lt(toOffsetDateTime(toExclusive)));
        }
        return condition;
    }

    private LedgerEntryRecord map(Record record) {
        return new LedgerEntryRecord(
                record.get(LEDGER_ENTRIES.ID),
                record.get(LEDGER_ENTRIES.LEDGER_TRANSACTION_ID),
                record.get(LEDGER_ENTRIES.LEDGER_ACCOUNT_ID),
                LedgerDirection.valueOf(record.get(LEDGER_ENTRIES.DIRECTION)),
                record.get(LEDGER_ENTRIES.AMOUNT),
                record.get(LEDGER_ENTRIES.CURRENCY),
                record.get(LEDGER_ENTRIES.CREATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
