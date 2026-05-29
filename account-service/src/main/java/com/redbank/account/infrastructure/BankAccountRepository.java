package com.redbank.account.infrastructure;

import com.redbank.account.domain.AccountStatusHistoryRecord;
import com.redbank.account.domain.BankAccountRecord;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BankAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public BankAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(BankAccountRecord account) {
        jdbcTemplate.update("""
                        INSERT INTO bank_accounts (
                            id, profile_id, user_id, selected_job_id, account_number, iban, type,
                            status, currency, closed_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                account.id(),
                account.profileId(),
                account.userId(),
                account.selectedJobId(),
                account.accountNumber(),
                account.iban(),
                account.type().name(),
                account.status().name(),
                account.currency(),
                timestamp(account.closedAt()),
                Timestamp.from(account.createdAt()),
                Timestamp.from(account.updatedAt())
        );
    }

    public boolean existsByAccountNumber(String accountNumber) {
        return exists("account_number", accountNumber);
    }

    public boolean existsByIban(String iban) {
        return exists("iban", iban);
    }

    public Optional<BankAccountRecord> findByAccountNumber(String accountNumber) {
        return queryOne("WHERE account_number = ?", accountNumber);
    }

    public Optional<BankAccountRecord> findByIban(String iban) {
        return queryOne("WHERE iban = ?", iban);
    }

    public Optional<BankAccountRecord> findByUserIdAndAccountNumber(UUID userId, String accountNumber) {
        return queryOne("WHERE user_id = ? AND account_number = ?", userId, accountNumber);
    }

    public Optional<BankAccountRecord> findLatestClosedPersonalAccountByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT ba.id, ba.profile_id, ba.user_id, ba.selected_job_id, ba.account_number, ba.iban, ba.type,
                               ba.status, ba.currency, ba.closed_at, ba.created_at, ba.updated_at
                        FROM bank_accounts ba
                        JOIN profiles p ON p.id = ba.profile_id
                        WHERE ba.user_id = ?
                          AND ba.status = 'CLOSED'
                          AND p.type = 'PERSONAL'
                        ORDER BY ba.updated_at DESC
                        LIMIT 1
                        """,
                this::map,
                userId
        ).stream().findFirst();
    }

    public boolean existsActiveByUserIdAndProfileId(UUID userId, UUID profileId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM bank_accounts
                        WHERE user_id = ?
                          AND profile_id = ?
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                userId,
                profileId
        );
        return count != null && count > 0;
    }

    public boolean existsByUserIdAndStatus(UUID userId, AccountStatus status) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM bank_accounts
                        WHERE user_id = ?
                          AND status = ?
                        """,
                Integer.class,
                userId,
                status.name()
        );
        return count != null && count > 0;
    }

    public int countActiveByProfileId(UUID profileId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM bank_accounts
                        WHERE profile_id = ?
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                profileId
        );
        return count == null ? 0 : count;
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
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(profileId);

        StringBuilder sql = new StringBuilder(selectBase());
        sql.append(" WHERE user_id = ? AND profile_id = ?");
        appendInClause(sql, args, "type", types == null ? List.of() : types.stream().map(Enum::name).toList());
        appendInClause(sql, args, "status", statuses == null ? List.of() : statuses.stream().map(Enum::name).toList());
        sql.append(" ORDER BY created_at ASC");

        return jdbcTemplate.query(sql.toString(), this::map, args.toArray());
    }

    public void close(BankAccountRecord account, Instant closedAt) {
        jdbcTemplate.update("""
                        UPDATE bank_accounts
                        SET status = ?, closed_at = ?, updated_at = ?
                        WHERE id = ?
                        """,
                AccountStatus.CLOSED.name(),
                Timestamp.from(closedAt),
                Timestamp.from(closedAt),
                account.id()
        );
    }

    public void open(BankAccountRecord account, Instant openedAt) {
        jdbcTemplate.update("""
                        UPDATE bank_accounts
                        SET status = ?, closed_at = NULL, updated_at = ?
                        WHERE id = ?
                        """,
                AccountStatus.ACTIVE.name(),
                Timestamp.from(openedAt),
                account.id()
        );
    }

    public void saveStatusHistory(AccountStatusHistoryRecord history) {
        jdbcTemplate.update("""
                        INSERT INTO account_status_history (
                            id, account_id, user_id, profile_id, previous_status, new_status, reason_text, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                history.id(),
                history.accountId(),
                history.userId(),
                history.profileId(),
                history.previousStatus().name(),
                history.newStatus().name(),
                history.reasonText(),
                Timestamp.from(history.createdAt())
        );
    }

    private Optional<BankAccountRecord> queryOne(String predicate, Object... args) {
        return jdbcTemplate.query(selectBase() + " " + predicate, this::map, args)
                .stream()
                .findFirst();
    }

    private String selectBase() {
        return """
                SELECT id, profile_id, user_id, selected_job_id, account_number, iban, type,
                       status, currency, closed_at, created_at, updated_at
                FROM bank_accounts
                """;
    }

    private void appendInClause(StringBuilder sql, List<Object> args, String column, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (");
        sql.append("?,".repeat(values.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");
        args.addAll(values);
    }

    private boolean exists(String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bank_accounts WHERE " + column + " = ?",
                Integer.class,
                value
        );
        return count != null && count > 0;
    }

    private BankAccountRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BankAccountRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("selected_job_id"),
                resultSet.getString("account_number"),
                resultSet.getString("iban"),
                AccountType.valueOf(resultSet.getString("type")),
                AccountStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("currency"),
                toNullableInstant(resultSet, "closed_at"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant toNullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
