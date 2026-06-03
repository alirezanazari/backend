package com.redbank.ledger.infrastructure;

import com.redbank.contracts.ledger.LedgerDirection;
import com.redbank.ledger.domain.LedgerEntryRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerEntryRepository {
    private final JdbcTemplate jdbcTemplate;

    public LedgerEntryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(List<LedgerEntryRecord> entries) {
        for (LedgerEntryRecord entry : entries) {
            jdbcTemplate.update("""
                            INSERT INTO ledger_entries (
                                id, ledger_transaction_id, ledger_account_id, direction, amount, currency, created_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    entry.id(),
                    entry.ledgerTransactionId(),
                    entry.ledgerAccountId(),
                    entry.direction().name(),
                    entry.amount(),
                    entry.currency(),
                    Timestamp.from(entry.createdAt())
            );
        }
    }

    public List<LedgerEntryRecord> findByAccountId(
            UUID ledgerAccountId,
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size
    ) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(selectBase());
        appendFilters(sql, args, ledgerAccountId, fromInclusive, toExclusive);
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add(page * size);

        return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
    }

    public long countByAccountId(UUID ledgerAccountId, Instant fromInclusive, Instant toExclusive) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ledger_entries");
        appendFilters(sql, args, ledgerAccountId, fromInclusive, toExclusive);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private String selectBase() {
        return """
                SELECT id, ledger_transaction_id, ledger_account_id, direction, amount, currency, created_at
                FROM ledger_entries
                """;
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> args,
            UUID ledgerAccountId,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        sql.append(" WHERE ledger_account_id = ?");
        args.add(ledgerAccountId);

        if (fromInclusive != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.from(fromInclusive));
        }
        if (toExclusive != null) {
            sql.append(" AND created_at < ?");
            args.add(Timestamp.from(toExclusive));
        }
    }

    private LedgerEntryRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LedgerEntryRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("ledger_transaction_id", UUID.class),
                resultSet.getObject("ledger_account_id", UUID.class),
                LedgerDirection.valueOf(resultSet.getString("direction")),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
