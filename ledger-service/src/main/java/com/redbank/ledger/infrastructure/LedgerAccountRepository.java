package com.redbank.ledger.infrastructure;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.ledger.domain.LedgerAccountRecord;
import com.redbank.ledger.domain.LedgerAccountStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public LedgerAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(LedgerAccountRecord account) {
        jdbcTemplate.update("""
                        INSERT INTO ledger_accounts (
                            id, external_account_number, owner_user_id, currency, status,
                            allow_negative_balance, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                account.id(),
                account.externalAccountNumber(),
                account.ownerUserId(),
                account.currency(),
                account.status().name(),
                account.allowNegativeBalance(),
                Timestamp.from(account.createdAt()),
                Timestamp.from(account.updatedAt())
        );
    }

    public void close(LedgerAccountRecord account, Instant closedAt) {
        jdbcTemplate.update("""
                        UPDATE ledger_accounts
                        SET status = ?, updated_at = ?
                        WHERE id = ?
                        """,
                LedgerAccountStatus.CLOSED.name(),
                Timestamp.from(closedAt),
                account.id()
        );
    }

    public Optional<LedgerAccountRecord> findByExternalAccountNumber(String externalAccountNumber) {
        return queryOne("WHERE external_account_number = ?", externalAccountNumber);
    }

    public Optional<LedgerAccountRecord> findById(UUID id) {
        return queryOne("WHERE id = ?", id);
    }

    private Optional<LedgerAccountRecord> queryOne(String predicate, Object... args) {
        return jdbcTemplate.query(selectBase() + " " + predicate, this::map, args)
                .stream()
                .findFirst();
    }

    private String selectBase() {
        return """
                SELECT id, external_account_number, owner_user_id, currency, status,
                       allow_negative_balance, created_at, updated_at
                FROM ledger_accounts
                """;
    }

    private LedgerAccountRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LedgerAccountRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("external_account_number"),
                resultSet.getObject("owner_user_id", UUID.class),
                resultSet.getString("currency"),
                LedgerAccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("allow_negative_balance"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}
