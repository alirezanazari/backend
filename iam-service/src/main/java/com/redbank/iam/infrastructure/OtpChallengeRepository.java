package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.OtpChallengeRecord;
import com.redbank.iam.domain.OtpChallengeStatus;
import com.redbank.iam.domain.OtpPurpose;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.iam.jooq.tables.OtpChallenges.OTP_CHALLENGES;

@Repository
public class OtpChallengeRepository {
    private final DSLContext dsl;

    public OtpChallengeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(OtpChallengeRecord challenge) {
        dsl.insertInto(OTP_CHALLENGES)
                .set(OTP_CHALLENGES.ID, challenge.id())
                .set(OTP_CHALLENGES.USER_ID, challenge.userId())
                .set(OTP_CHALLENGES.DEVICE_ID, challenge.deviceId())
                .set(OTP_CHALLENGES.OTP_HASH, challenge.otpHash())
                .set(OTP_CHALLENGES.PURPOSE, challenge.purpose().name())
                .set(OTP_CHALLENGES.EXPIRES_AT, toOffsetDateTime(challenge.expiresAt()))
                .set(OTP_CHALLENGES.ATTEMPT_COUNT, challenge.attemptCount())
                .set(OTP_CHALLENGES.MAX_ATTEMPTS, challenge.maxAttempts())
                .set(OTP_CHALLENGES.STATUS, challenge.status().name())
                .set(OTP_CHALLENGES.CREATED_AT, toOffsetDateTime(challenge.createdAt()))
                .set(OTP_CHALLENGES.UPDATED_AT, toOffsetDateTime(challenge.updatedAt()))
                .execute();
    }

    public Optional<OtpChallengeRecord> findById(UUID id) {
        return dsl.selectFrom(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(id))
                .fetchOptional(this::map);
    }

    public Optional<OtpChallengeRecord> findPendingByUserDevicePurpose(
            UUID userId,
            UUID deviceId,
            OtpPurpose purpose,
            Instant now
    ) {
        return dsl.selectFrom(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.USER_ID.eq(userId))
                .and(OTP_CHALLENGES.DEVICE_ID.eq(deviceId))
                .and(OTP_CHALLENGES.PURPOSE.eq(purpose.name()))
                .and(OTP_CHALLENGES.STATUS.eq(OtpChallengeStatus.PENDING.name()))
                .and(OTP_CHALLENGES.EXPIRES_AT.gt(toOffsetDateTime(now)))
                .orderBy(OTP_CHALLENGES.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::map);
    }

    public void expirePendingByUserDevicePurpose(UUID userId, UUID deviceId, OtpPurpose purpose, Instant now) {
        dsl.update(OTP_CHALLENGES)
                .set(OTP_CHALLENGES.STATUS, OtpChallengeStatus.EXPIRED.name())
                .set(OTP_CHALLENGES.UPDATED_AT, toOffsetDateTime(now))
                .where(OTP_CHALLENGES.USER_ID.eq(userId))
                .and(OTP_CHALLENGES.DEVICE_ID.eq(deviceId))
                .and(OTP_CHALLENGES.PURPOSE.eq(purpose.name()))
                .and(OTP_CHALLENGES.STATUS.eq(OtpChallengeStatus.PENDING.name()))
                .and(OTP_CHALLENGES.EXPIRES_AT.le(toOffsetDateTime(now)))
                .execute();
    }

    public void incrementAttemptCount(UUID id, Instant updatedAt) {
        dsl.update(OTP_CHALLENGES)
                .set(OTP_CHALLENGES.ATTEMPT_COUNT, OTP_CHALLENGES.ATTEMPT_COUNT.add(1))
                .set(OTP_CHALLENGES.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(OTP_CHALLENGES.ID.eq(id))
                .execute();
    }

    public void markStatus(UUID id, OtpChallengeStatus status, Instant updatedAt) {
        dsl.update(OTP_CHALLENGES)
                .set(OTP_CHALLENGES.STATUS, status.name())
                .set(OTP_CHALLENGES.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(OTP_CHALLENGES.ID.eq(id))
                .execute();
    }

    private OtpChallengeRecord map(Record record) {
        return new OtpChallengeRecord(
                record.get(OTP_CHALLENGES.ID),
                record.get(OTP_CHALLENGES.USER_ID),
                record.get(OTP_CHALLENGES.DEVICE_ID),
                record.get(OTP_CHALLENGES.OTP_HASH),
                OtpPurpose.valueOf(record.get(OTP_CHALLENGES.PURPOSE)),
                record.get(OTP_CHALLENGES.EXPIRES_AT).toInstant(),
                record.get(OTP_CHALLENGES.ATTEMPT_COUNT),
                record.get(OTP_CHALLENGES.MAX_ATTEMPTS),
                OtpChallengeStatus.valueOf(record.get(OTP_CHALLENGES.STATUS)),
                record.get(OTP_CHALLENGES.CREATED_AT).toInstant(),
                record.get(OTP_CHALLENGES.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
