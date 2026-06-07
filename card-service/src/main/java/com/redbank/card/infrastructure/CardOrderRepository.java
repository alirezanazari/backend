package com.redbank.card.infrastructure;

import com.redbank.card.domain.CardOrderRecord;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CardOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public CardOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(CardOrderRecord order) {
        jdbcTemplate.update("""
                        INSERT INTO card_orders (
                            id, user_id, profile_id, account_number, delivery_country_code,
                            delivery_province, delivery_city, delivery_address, delivery_postal_code,
                            reason, status, card_id, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                order.id(),
                order.userId(),
                order.profileId(),
                order.accountNumber(),
                order.deliveryCountryCode(),
                order.deliveryProvince(),
                order.deliveryCity(),
                order.deliveryAddress(),
                order.deliveryPostalCode(),
                order.reason(),
                order.status().name(),
                order.cardId(),
                Timestamp.from(order.createdAt()),
                Timestamp.from(order.updatedAt())
        );
    }

    public Optional<CardOrderRecord> findOpenByUserIdAndAccountNumber(UUID userId, String accountNumber) {
        // TODO(phase-5-learning): query PENDING orders for this user/account and return the newest one.
        throw new UnsupportedOperationException("TODO: implement open card order database query");
    }

    public Optional<CardOrderRecord> findByCardId(UUID cardId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, profile_id, account_number, delivery_country_code,
                               delivery_province, delivery_city, delivery_address, delivery_postal_code,
                               reason, status, card_id, created_at, updated_at
                        FROM card_orders
                        WHERE card_id = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                this::map,
                cardId
        ).stream().findFirst();
    }

    private CardOrderRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CardOrderRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("profile_id", UUID.class),
                resultSet.getString("account_number"),
                resultSet.getString("delivery_country_code"),
                resultSet.getString("delivery_province"),
                resultSet.getString("delivery_city"),
                resultSet.getString("delivery_address"),
                resultSet.getString("delivery_postal_code"),
                resultSet.getString("reason"),
                com.redbank.card.domain.CardOrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("card_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
