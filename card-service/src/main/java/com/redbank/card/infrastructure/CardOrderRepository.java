package com.redbank.card.infrastructure;

import com.redbank.card.domain.CardOrderRecord;
import com.redbank.card.domain.CardOrderStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.card.jooq.tables.CardOrders.CARD_ORDERS;

@Repository
public class CardOrderRepository {
    private final DSLContext dsl;

    public CardOrderRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(CardOrderRecord order) {
        dsl.insertInto(CARD_ORDERS)
                .set(CARD_ORDERS.ID, order.id())
                .set(CARD_ORDERS.USER_ID, order.userId())
                .set(CARD_ORDERS.PROFILE_ID, order.profileId())
                .set(CARD_ORDERS.ACCOUNT_NUMBER, order.accountNumber())
                .set(CARD_ORDERS.DELIVERY_COUNTRY_CODE, order.deliveryCountryCode())
                .set(CARD_ORDERS.DELIVERY_PROVINCE, order.deliveryProvince())
                .set(CARD_ORDERS.DELIVERY_CITY, order.deliveryCity())
                .set(CARD_ORDERS.DELIVERY_ADDRESS, order.deliveryAddress())
                .set(CARD_ORDERS.DELIVERY_POSTAL_CODE, order.deliveryPostalCode())
                .set(CARD_ORDERS.REASON, order.reason())
                .set(CARD_ORDERS.STATUS, order.status().name())
                .set(CARD_ORDERS.CARD_ID, order.cardId())
                .set(CARD_ORDERS.CREATED_AT, toOffsetDateTime(order.createdAt()))
                .set(CARD_ORDERS.UPDATED_AT, toOffsetDateTime(order.updatedAt()))
                .execute();
    }

    public Optional<CardOrderRecord> findOpenByUserIdAndAccountNumber(UUID userId, String accountNumber) {
        // TODO(phase-5-learning): query PENDING orders for this user/account and return the newest one.
        throw new UnsupportedOperationException("TODO: implement open card order database query");
    }

    public Optional<CardOrderRecord> findByCardId(UUID cardId) {
        return dsl.selectFrom(CARD_ORDERS)
                .where(CARD_ORDERS.CARD_ID.eq(cardId))
                .orderBy(CARD_ORDERS.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private CardOrderRecord map(Record record) {
        return new CardOrderRecord(
                record.get(CARD_ORDERS.ID),
                record.get(CARD_ORDERS.USER_ID),
                record.get(CARD_ORDERS.PROFILE_ID),
                record.get(CARD_ORDERS.ACCOUNT_NUMBER),
                record.get(CARD_ORDERS.DELIVERY_COUNTRY_CODE),
                record.get(CARD_ORDERS.DELIVERY_PROVINCE),
                record.get(CARD_ORDERS.DELIVERY_CITY),
                record.get(CARD_ORDERS.DELIVERY_ADDRESS),
                record.get(CARD_ORDERS.DELIVERY_POSTAL_CODE),
                record.get(CARD_ORDERS.REASON),
                CardOrderStatus.valueOf(record.get(CARD_ORDERS.STATUS)),
                record.get(CARD_ORDERS.CARD_ID),
                record.get(CARD_ORDERS.CREATED_AT).toInstant(),
                record.get(CARD_ORDERS.UPDATED_AT).toInstant()
        );
    }
}
