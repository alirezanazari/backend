package com.redbank.ledger.infrastructure;

import com.redbank.ledger.domain.LedgerReferenceType;
import com.redbank.ledger.domain.LedgerTransactionRecord;
import com.redbank.ledger.domain.LedgerTransactionStatus;
import com.redbank.ledger.domain.LedgerTransactionType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.ledger.jooq.tables.LedgerTransactions.LEDGER_TRANSACTIONS;

@Repository
public class LedgerTransactionRepository {
    private final DSLContext dsl;

    public LedgerTransactionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(LedgerTransactionRecord transaction) {
        dsl.insertInto(LEDGER_TRANSACTIONS)
                .set(LEDGER_TRANSACTIONS.ID, transaction.id())
                .set(LEDGER_TRANSACTIONS.REFERENCE_TYPE, transaction.referenceType().name())
                .set(LEDGER_TRANSACTIONS.REFERENCE_ID, transaction.referenceId())
                .set(LEDGER_TRANSACTIONS.IDEMPOTENCY_KEY, transaction.idempotencyKey())
                .set(LEDGER_TRANSACTIONS.TRANSACTION_TYPE, transaction.transactionType().name())
                .set(LEDGER_TRANSACTIONS.STATUS, transaction.status().name())
                .set(LEDGER_TRANSACTIONS.CREATED_AT, toOffsetDateTime(transaction.createdAt()))
                .set(LEDGER_TRANSACTIONS.UPDATED_AT, toOffsetDateTime(transaction.updatedAt()))
                .execute();
    }

    public Optional<LedgerTransactionRecord> findByReference(
            LedgerReferenceType referenceType,
            String referenceId
    ) {
        return findOne(
                LEDGER_TRANSACTIONS.REFERENCE_TYPE.eq(referenceType.name())
                        .and(LEDGER_TRANSACTIONS.REFERENCE_ID.eq(referenceId))
        );
    }

    public Optional<LedgerTransactionRecord> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return findOne(LEDGER_TRANSACTIONS.IDEMPOTENCY_KEY.eq(idempotencyKey));
    }

    private Optional<LedgerTransactionRecord> findOne(Condition condition) {
        return dsl.selectFrom(LEDGER_TRANSACTIONS)
                .where(condition)
                .fetchOptional(this::map);
    }

    private LedgerTransactionRecord map(Record record) {
        return new LedgerTransactionRecord(
                record.get(LEDGER_TRANSACTIONS.ID),
                LedgerReferenceType.valueOf(record.get(LEDGER_TRANSACTIONS.REFERENCE_TYPE)),
                record.get(LEDGER_TRANSACTIONS.REFERENCE_ID),
                record.get(LEDGER_TRANSACTIONS.IDEMPOTENCY_KEY),
                LedgerTransactionType.valueOf(record.get(LEDGER_TRANSACTIONS.TRANSACTION_TYPE)),
                LedgerTransactionStatus.valueOf(record.get(LEDGER_TRANSACTIONS.STATUS)),
                record.get(LEDGER_TRANSACTIONS.CREATED_AT).toInstant(),
                record.get(LEDGER_TRANSACTIONS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
