package com.redbank.transfer.infrastructure;

import com.redbank.transfer.domain.IdempotencyRecord;
import com.redbank.transfer.domain.IdempotencyStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.transfer.jooq.tables.IdempotencyKeys.IDEMPOTENCY_KEYS;

@Repository
public class IdempotencyRepository {
    private final DSLContext dsl;

    public IdempotencyRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public IdempotencyRecord claim(
            UUID userId,
            String key,
            String requestHash,
            Instant expiresAt,
            Instant now
    ) {
        IdempotencyRecord candidate = new IdempotencyRecord(
                UUID.randomUUID(),
                userId,
                key,
                requestHash,
                null,
                null,
                IdempotencyStatus.PROCESSING,
                expiresAt,
                now,
                now
        );
        int inserted = dsl.insertInto(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.ID, candidate.id())
                .set(IDEMPOTENCY_KEYS.USER_ID, candidate.userId())
                .set(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY, candidate.key())
                .set(IDEMPOTENCY_KEYS.REQUEST_HASH, candidate.requestHash())
                .set(IDEMPOTENCY_KEYS.TRANSFER_ID, candidate.transferId())
                .set(IDEMPOTENCY_KEYS.RESPONSE_SNAPSHOT, candidate.responseSnapshot())
                .set(IDEMPOTENCY_KEYS.STATUS, candidate.status().name())
                .set(IDEMPOTENCY_KEYS.EXPIRES_AT, toOffsetDateTime(candidate.expiresAt()))
                .set(IDEMPOTENCY_KEYS.CREATED_AT, toOffsetDateTime(candidate.createdAt()))
                .set(IDEMPOTENCY_KEYS.UPDATED_AT, toOffsetDateTime(candidate.updatedAt()))
                .onConflict(IDEMPOTENCY_KEYS.USER_ID, IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY)
                .doNothing()
                .execute();
        return inserted == 1 ? candidate : find(userId, key)
                .orElseThrow(() -> new IllegalStateException("Idempotency claim disappeared"));
    }

    public void attachTransfer(UUID idempotencyId, UUID transferId, Instant updatedAt) {
        dsl.update(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.TRANSFER_ID, transferId)
                .set(IDEMPOTENCY_KEYS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(IDEMPOTENCY_KEYS.ID.eq(idempotencyId))
                .execute();
    }

    public void complete(UUID idempotencyId, String responseSnapshot, Instant updatedAt) {
        dsl.update(IDEMPOTENCY_KEYS)
                .set(IDEMPOTENCY_KEYS.STATUS, IdempotencyStatus.COMPLETED.name())
                .set(IDEMPOTENCY_KEYS.RESPONSE_SNAPSHOT, responseSnapshot)
                .set(IDEMPOTENCY_KEYS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(IDEMPOTENCY_KEYS.ID.eq(idempotencyId))
                .execute();
    }

    private Optional<IdempotencyRecord> find(UUID userId, String key) {
        return dsl.selectFrom(IDEMPOTENCY_KEYS)
                .where(IDEMPOTENCY_KEYS.USER_ID.eq(userId))
                .and(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY.eq(key))
                .fetchOptional(this::map);
    }

    private IdempotencyRecord map(Record record) {
        return new IdempotencyRecord(
                record.get(IDEMPOTENCY_KEYS.ID),
                record.get(IDEMPOTENCY_KEYS.USER_ID),
                record.get(IDEMPOTENCY_KEYS.IDEMPOTENCY_KEY),
                record.get(IDEMPOTENCY_KEYS.REQUEST_HASH),
                record.get(IDEMPOTENCY_KEYS.TRANSFER_ID),
                record.get(IDEMPOTENCY_KEYS.RESPONSE_SNAPSHOT),
                IdempotencyStatus.valueOf(record.get(IDEMPOTENCY_KEYS.STATUS)),
                record.get(IDEMPOTENCY_KEYS.EXPIRES_AT).toInstant(),
                record.get(IDEMPOTENCY_KEYS.CREATED_AT).toInstant(),
                record.get(IDEMPOTENCY_KEYS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
