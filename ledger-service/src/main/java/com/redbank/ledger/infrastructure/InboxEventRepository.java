package com.redbank.ledger.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.redbank.ledger.jooq.tables.InboxEvents.INBOX_EVENTS;

@Repository
public class InboxEventRepository {
    private final DSLContext dsl;

    public InboxEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean claim(UUID eventId, String eventType, String consumerName, Instant receivedAt) {
        return dsl.insertInto(INBOX_EVENTS)
                .set(INBOX_EVENTS.ID, UUID.randomUUID())
                .set(INBOX_EVENTS.EVENT_ID, eventId)
                .set(INBOX_EVENTS.EVENT_TYPE, eventType)
                .set(INBOX_EVENTS.CONSUMER_NAME, consumerName)
                .set(INBOX_EVENTS.STATUS, "PROCESSING")
                .set(INBOX_EVENTS.RECEIVED_AT, toOffsetDateTime(receivedAt))
                .onConflict(INBOX_EVENTS.EVENT_ID, INBOX_EVENTS.CONSUMER_NAME)
                .doNothing()
                .execute() == 1;
    }

    public void markProcessed(UUID eventId, String consumerName, String resultPayload, Instant processedAt) {
        dsl.update(INBOX_EVENTS)
                .set(INBOX_EVENTS.STATUS, "PROCESSED")
                .set(INBOX_EVENTS.RESULT_PAYLOAD, resultPayload)
                .set(INBOX_EVENTS.PROCESSED_AT, toOffsetDateTime(processedAt))
                .where(INBOX_EVENTS.EVENT_ID.eq(eventId))
                .and(INBOX_EVENTS.CONSUMER_NAME.eq(consumerName))
                .execute();
    }

    public Optional<String> findProcessedResult(UUID eventId, String eventType, String consumerName) {
        return dsl.select(INBOX_EVENTS.RESULT_PAYLOAD)
                .from(INBOX_EVENTS)
                .where(INBOX_EVENTS.EVENT_ID.eq(eventId))
                .and(INBOX_EVENTS.EVENT_TYPE.eq(eventType))
                .and(INBOX_EVENTS.CONSUMER_NAME.eq(consumerName))
                .and(INBOX_EVENTS.STATUS.eq("PROCESSED"))
                .fetchOptional(INBOX_EVENTS.RESULT_PAYLOAD);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
