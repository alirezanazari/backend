package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.OutboxEventRecord;
import com.redbank.iam.domain.OutboxEventStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.redbank.iam.jooq.tables.OutboxEvents.OUTBOX_EVENTS;

@Repository
public class OutboxEventRepository {
    private final DSLContext dsl;

    public OutboxEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(OutboxEventRecord event) {
        dsl.insertInto(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.ID, event.id())
                .set(OUTBOX_EVENTS.AGGREGATE_TYPE, event.aggregateType())
                .set(OUTBOX_EVENTS.AGGREGATE_ID, event.aggregateId())
                .set(OUTBOX_EVENTS.EVENT_TYPE, event.eventType())
                .set(OUTBOX_EVENTS.PAYLOAD, event.payload())
                .set(OUTBOX_EVENTS.STATUS, event.status().name())
                .set(OUTBOX_EVENTS.CREATED_AT, toOffsetDateTime(event.createdAt()))
                .set(OUTBOX_EVENTS.UPDATED_AT, toOffsetDateTime(event.updatedAt()))
                .set(OUTBOX_EVENTS.RETRY_COUNT, event.retryCount())
                .execute();
    }

    public List<OutboxEventRecord> findPending(int limit, int maxRetryCount) {
        return dsl.selectFrom(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.STATUS.eq(OutboxEventStatus.PENDING.name()))
                .and(OUTBOX_EVENTS.RETRY_COUNT.lt(maxRetryCount))
                .orderBy(OUTBOX_EVENTS.SEQUENCE_NUMBER.asc())
                .limit(limit)
                .fetch(this::map);
    }

    public void markPublished(UUID id, Instant publishedAt) {
        OffsetDateTime timestamp = toOffsetDateTime(publishedAt);
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.STATUS, OutboxEventStatus.PUBLISHED.name())
                .set(OUTBOX_EVENTS.PUBLISHED_AT, timestamp)
                .set(OUTBOX_EVENTS.UPDATED_AT, timestamp)
                .where(OUTBOX_EVENTS.ID.eq(id))
                .execute();
    }

    public void markAttemptFailed(UUID id, Instant updatedAt, int maxRetryCount) {
        dsl.update(OUTBOX_EVENTS)
                .set(OUTBOX_EVENTS.RETRY_COUNT, OUTBOX_EVENTS.RETRY_COUNT.add(1))
                .set(
                        OUTBOX_EVENTS.STATUS,
                        DSL.when(
                                OUTBOX_EVENTS.RETRY_COUNT.add(1).ge(maxRetryCount),
                                OutboxEventStatus.DEAD_LETTER.name()
                        ).otherwise(OutboxEventStatus.PENDING.name())
                )
                .set(OUTBOX_EVENTS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(OUTBOX_EVENTS.ID.eq(id))
                .execute();
    }

    private OutboxEventRecord map(Record record) {
        return new OutboxEventRecord(
                record.get(OUTBOX_EVENTS.ID),
                record.get(OUTBOX_EVENTS.AGGREGATE_TYPE),
                record.get(OUTBOX_EVENTS.AGGREGATE_ID),
                record.get(OUTBOX_EVENTS.EVENT_TYPE),
                record.get(OUTBOX_EVENTS.PAYLOAD),
                OutboxEventStatus.valueOf(record.get(OUTBOX_EVENTS.STATUS)),
                record.get(OUTBOX_EVENTS.CREATED_AT).toInstant(),
                record.get(OUTBOX_EVENTS.UPDATED_AT).toInstant(),
                toInstant(record.get(OUTBOX_EVENTS.PUBLISHED_AT)),
                record.get(OUTBOX_EVENTS.RETRY_COUNT)
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
