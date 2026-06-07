package com.redbank.card.infrastructure;

import com.redbank.card.domain.CardOrderRecord;
import java.sql.Timestamp;
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
}
