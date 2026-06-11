package com.redbank.kyc.infrastructure;

import com.redbank.kyc.domain.ReferralCodeRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.kyc.jooq.tables.ReferralCodes.REFERRAL_CODES;

@Repository
public class ReferralCodeRepository {
    private final DSLContext dsl;

    public ReferralCodeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean createIfAbsent(ReferralCodeRecord record) {
        return dsl.insertInto(REFERRAL_CODES)
                .set(REFERRAL_CODES.ID, record.id())
                .set(REFERRAL_CODES.USER_ID, record.userId())
                .set(REFERRAL_CODES.CODE, record.code())
                .set(REFERRAL_CODES.DISPLAY_NAME, record.displayName())
                .set(REFERRAL_CODES.CREATED_AT, toOffsetDateTime(record.createdAt()))
                .onConflictDoNothing()
                .execute() == 1;
    }

    public Optional<ReferralCodeRecord> findByCode(String code) {
        return dsl.selectFrom(REFERRAL_CODES)
                .where(REFERRAL_CODES.CODE.eq(code))
                .fetchOptional(this::map);
    }

    public Optional<ReferralCodeRecord> findByUserId(UUID userId) {
        return dsl.selectFrom(REFERRAL_CODES)
                .where(REFERRAL_CODES.USER_ID.eq(userId))
                .fetchOptional(this::map);
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private ReferralCodeRecord map(Record record) {
        return new ReferralCodeRecord(
                record.get(REFERRAL_CODES.ID),
                record.get(REFERRAL_CODES.USER_ID),
                record.get(REFERRAL_CODES.CODE),
                record.get(REFERRAL_CODES.DISPLAY_NAME),
                record.get(REFERRAL_CODES.CREATED_AT).toInstant()
        );
    }
}
