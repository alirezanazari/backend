package com.redbank.kyc.infrastructure;

import com.redbank.kyc.domain.ReferralRecord;
import com.redbank.kyc.domain.ReferralStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.kyc.jooq.tables.Referrals.REFERRALS;

@Repository
public class ReferralRepository {
    private final DSLContext dsl;

    public ReferralRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<ReferralRecord> findByReferredUserId(UUID referredUserId) {
        return dsl.selectFrom(REFERRALS)
                .where(REFERRALS.REFERRED_USER_ID.eq(referredUserId))
                .fetchOptional(this::map);
    }

    public void save(ReferralRecord referral) {
        dsl.insertInto(REFERRALS)
                .set(REFERRALS.ID, referral.id())
                .set(REFERRALS.REFERRED_USER_ID, referral.referredUserId())
                .set(REFERRALS.REFERRER_USER_ID, referral.referrerUserId())
                .set(REFERRALS.REFERRAL_CODE, referral.referralCode())
                .set(REFERRALS.REFERRER_NAME, referral.referrerName())
                .set(REFERRALS.STATUS, referral.status().name())
                .set(REFERRALS.CREATED_AT, toOffsetDateTime(referral.createdAt()))
                .set(REFERRALS.UPDATED_AT, toOffsetDateTime(referral.updatedAt()))
                .execute();
    }

    public boolean updateStatus(
            UUID referralId,
            ReferralStatus expectedStatus,
            ReferralStatus nextStatus,
            Instant updatedAt
    ) {
        int updated = dsl.update(REFERRALS)
                .set(REFERRALS.STATUS, nextStatus.name())
                .set(REFERRALS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(REFERRALS.ID.eq(referralId))
                .and(REFERRALS.STATUS.eq(expectedStatus.name()))
                .execute();
        return updated == 1;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private ReferralRecord map(Record record) {
        return new ReferralRecord(
                record.get(REFERRALS.ID),
                record.get(REFERRALS.REFERRED_USER_ID),
                record.get(REFERRALS.REFERRER_USER_ID),
                record.get(REFERRALS.REFERRAL_CODE),
                record.get(REFERRALS.REFERRER_NAME),
                ReferralStatus.valueOf(record.get(REFERRALS.STATUS)),
                record.get(REFERRALS.CREATED_AT).toInstant(),
                record.get(REFERRALS.UPDATED_AT).toInstant()
        );
    }
}
