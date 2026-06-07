package com.redbank.card.infrastructure;

import com.redbank.card.domain.CardRecord;
import com.redbank.card.domain.CardStatusHistoryRecord;
import com.redbank.contracts.card.CardStatus;
import com.redbank.contracts.card.CardForm;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CardRepository {
    private final JdbcTemplate jdbcTemplate;

    public CardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(CardRecord card) {
        jdbcTemplate.update("""
                        INSERT INTO cards (
                            id, user_id, profile_id, account_number, masked_pan, card_token,
                            form, status, expires_at, issuance_reference, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                card.id(),
                card.userId(),
                card.profileId(),
                card.accountNumber(),
                card.maskedPan(),
                card.cardToken(),
                card.form().name(),
                card.status().name(),
                Date.valueOf(card.expiresAt().atDay(1)),
                card.issuanceReference(),
                Timestamp.from(card.createdAt()),
                Timestamp.from(card.updatedAt())
        );
    }

    public Optional<CardRecord> findByIssuanceReference(String issuanceReference) {
        return queryOne("WHERE issuance_reference = ?", issuanceReference);
    }

    public Optional<CardRecord> findById(UUID cardId) {
        return queryOne("WHERE id = ?", cardId);
    }

    public Optional<CardRecord> findByIdAndUserId(UUID cardId, UUID userId) {
        return queryOne("WHERE id = ? AND user_id = ?", cardId, userId);
    }

    public Optional<CardRecord> findDigitalByUserIdAndProfileIdAndAccountNumber(
            UUID userId,
            UUID profileId,
            String accountNumber
    ) {
        return queryOne("""
                WHERE user_id = ?
                  AND profile_id = ?
                  AND account_number = ?
                  AND form = 'DIGITAL'
                  AND status = 'ACTIVE'
                ORDER BY created_at ASC
                LIMIT 1
                """,
                userId,
                profileId,
                accountNumber
        );
    }

    public List<CardRecord> findByAccountNumber(String accountNumber) {
        return jdbcTemplate.query(
                selectBase() + " WHERE account_number = ? ORDER BY created_at ASC",
                this::map,
                accountNumber
        );
    }

    public List<CardRecord> findByUserIdAndProfileId(
            UUID userId,
            UUID profileId,
            List<CardStatus> statuses,
            String accountNumber
    ) {
        // TODO(phase-5-learning): build the filtered SQL query and map the matching card rows.
        throw new UnsupportedOperationException("TODO: implement filtered card database query");
    }

    public boolean updateStatus(UUID cardId, CardStatus expectedStatus, CardStatus newStatus, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE cards
                        SET status = ?, updated_at = ?
                        WHERE id = ? AND status = ?
                        """,
                newStatus.name(),
                Timestamp.from(updatedAt),
                cardId,
                expectedStatus.name()
        );
        return updated == 1;
    }

    public boolean updateForm(UUID cardId, CardForm expectedForm, CardForm newForm, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                        UPDATE cards
                        SET form = ?, updated_at = ?
                        WHERE id = ? AND form = ?
                        """,
                newForm.name(),
                Timestamp.from(updatedAt),
                cardId,
                expectedForm.name()
        );
        return updated == 1;
    }

    public void saveStatusHistory(CardStatusHistoryRecord history) {
        jdbcTemplate.update("""
                        INSERT INTO card_status_history (
                            id, card_id, user_id, profile_id, previous_status, new_status,
                            reason, actor_type, actor_id, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                history.id(),
                history.cardId(),
                history.userId(),
                history.profileId(),
                history.previousStatus() == null ? null : history.previousStatus().name(),
                history.newStatus().name(),
                history.reason(),
                history.actorType().name(),
                history.actorId(),
                Timestamp.from(history.createdAt())
        );
    }

    private Optional<CardRecord> queryOne(String predicate, Object... args) {
        return jdbcTemplate.query(selectBase() + " " + predicate, this::map, args)
                .stream()
                .findFirst();
    }

    private String selectBase() {
        return """
                SELECT id, user_id, profile_id, account_number, masked_pan, card_token,
                       form, status, expires_at, issuance_reference, created_at, updated_at
                FROM cards
                """;
    }

    private CardRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CardRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("account_number"),
                resultSet.getString("masked_pan"),
                resultSet.getString("card_token"),
                CardForm.valueOf(resultSet.getString("form")),
                CardStatus.valueOf(resultSet.getString("status")),
                YearMonth.from(resultSet.getDate("expires_at").toLocalDate()),
                resultSet.getString("issuance_reference"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
