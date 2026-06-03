package com.redbank.ledger.infrastructure;

import com.redbank.ledger.domain.LedgerBalanceRecord;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerBalanceRepository {
    private final JdbcTemplate jdbcTemplate;

    public LedgerBalanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveInitialBalance(UUID ledgerAccountId, String currency, Instant updatedAt) {
        jdbcTemplate.update("""
                        INSERT INTO ledger_balances (
                            ledger_account_id, available_balance, current_balance, currency, updated_at, version
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                ledgerAccountId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                currency,
                Timestamp.from(updatedAt),
                0L
        );
    }

    public Optional<LedgerBalanceRecord> findByAccountId(UUID ledgerAccountId) {
        return jdbcTemplate.query("""
                        SELECT ledger_account_id, available_balance, current_balance, currency, updated_at, version
                        FROM ledger_balances
                        WHERE ledger_account_id = ?
                        """,
                this::map,
                ledgerAccountId
        ).stream().findFirst();
    }

    public boolean update(
            UUID ledgerAccountId,
            BigDecimal availableBalance,
            BigDecimal currentBalance,
            long expectedVersion,
            Instant updatedAt
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE ledger_balances
                        SET available_balance = ?, current_balance = ?, updated_at = ?, version = version + 1
                        WHERE ledger_account_id = ?
                          AND version = ?
                        """,
                availableBalance,
                currentBalance,
                Timestamp.from(updatedAt),
                ledgerAccountId,
                expectedVersion
        );
        return updated == 1;
    }

    private LedgerBalanceRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LedgerBalanceRecord(
                resultSet.getObject("ledger_account_id", UUID.class),
                resultSet.getBigDecimal("available_balance"),
                resultSet.getBigDecimal("current_balance"),
                resultSet.getString("currency"),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version")
        );
    }
}
