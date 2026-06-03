package com.redbank.ledger.infrastructure;

import com.redbank.ledger.domain.LedgerReferenceType;
import com.redbank.ledger.domain.LedgerTransactionRecord;
import com.redbank.ledger.domain.LedgerTransactionStatus;
import com.redbank.ledger.domain.LedgerTransactionType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerTransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    public LedgerTransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(LedgerTransactionRecord transaction) {
        jdbcTemplate.update("""
                        INSERT INTO ledger_transactions (
                            id, reference_type, reference_id, idempotency_key, transaction_type,
                            status, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                transaction.id(),
                transaction.referenceType().name(),
                transaction.referenceId(),
                transaction.idempotencyKey(),
                transaction.transactionType().name(),
                transaction.status().name(),
                Timestamp.from(transaction.createdAt()),
                Timestamp.from(transaction.updatedAt())
        );
    }

    public Optional<LedgerTransactionRecord> findByReference(LedgerReferenceType referenceType, String referenceId) {
        return queryOne("WHERE reference_type = ? AND reference_id = ?", referenceType.name(), referenceId);
    }

    public Optional<LedgerTransactionRecord> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return queryOne("WHERE idempotency_key = ?", idempotencyKey);
    }

    private Optional<LedgerTransactionRecord> queryOne(String predicate, Object... args) {
        return jdbcTemplate.query(selectBase() + " " + predicate, this::map, args)
                .stream()
                .findFirst();
    }

    private String selectBase() {
        return """
                SELECT id, reference_type, reference_id, idempotency_key, transaction_type, status, created_at, updated_at
                FROM ledger_transactions
                """;
    }

    private LedgerTransactionRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LedgerTransactionRecord(
                resultSet.getObject("id", UUID.class),
                LedgerReferenceType.valueOf(resultSet.getString("reference_type")),
                resultSet.getString("reference_id"),
                resultSet.getString("idempotency_key"),
                LedgerTransactionType.valueOf(resultSet.getString("transaction_type")),
                LedgerTransactionStatus.valueOf(resultSet.getString("status")),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}
