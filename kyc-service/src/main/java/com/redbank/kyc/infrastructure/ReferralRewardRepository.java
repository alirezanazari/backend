package com.redbank.kyc.infrastructure;

import com.redbank.kyc.domain.ReferralRewardRecord;
import com.redbank.kyc.domain.ReferralRewardStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.kyc.jooq.tables.ReferralRewards.REFERRAL_REWARDS;

@Repository
public class ReferralRewardRepository {
    private final DSLContext dsl;

    public ReferralRewardRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<ReferralRewardRecord> findByReferralId(UUID referralId) {
        return dsl.selectFrom(REFERRAL_REWARDS)
                .where(REFERRAL_REWARDS.REFERRAL_ID.eq(referralId))
                .fetchOptional(this::map);
    }

    public void save(ReferralRewardRecord reward) {
        dsl.insertInto(REFERRAL_REWARDS)
                .set(REFERRAL_REWARDS.ID, reward.id())
                .set(REFERRAL_REWARDS.REFERRAL_ID, reward.referralId())
                .set(REFERRAL_REWARDS.REFERRER_ACCOUNT_NUMBER, reward.referrerAccountNumber())
                .set(REFERRAL_REWARDS.AMOUNT, reward.amount())
                .set(REFERRAL_REWARDS.CURRENCY, reward.currency())
                .set(REFERRAL_REWARDS.STATUS, reward.status().name())
                .set(REFERRAL_REWARDS.LEDGER_TRANSACTION_ID, reward.ledgerTransactionId())
                .set(REFERRAL_REWARDS.REQUESTED_AT, toOffsetDateTime(reward.requestedAt()))
                .set(REFERRAL_REWARDS.CREATED_AT, toOffsetDateTime(reward.createdAt()))
                .set(REFERRAL_REWARDS.UPDATED_AT, toOffsetDateTime(reward.updatedAt()))
                .execute();
    }

    public boolean markResult(
            UUID rewardId,
            ReferralRewardStatus expectedStatus,
            ReferralRewardStatus nextStatus,
            String accountNumber,
            String ledgerTransactionId,
            Instant requestedAt
    ) {
        int updated = dsl.update(REFERRAL_REWARDS)
                .set(REFERRAL_REWARDS.REFERRER_ACCOUNT_NUMBER, accountNumber)
                .set(REFERRAL_REWARDS.STATUS, nextStatus.name())
                .set(REFERRAL_REWARDS.LEDGER_TRANSACTION_ID, ledgerTransactionId)
                .set(REFERRAL_REWARDS.REQUESTED_AT, toOffsetDateTime(requestedAt))
                .set(REFERRAL_REWARDS.UPDATED_AT, toOffsetDateTime(requestedAt))
                .where(REFERRAL_REWARDS.ID.eq(rewardId))
                .and(REFERRAL_REWARDS.STATUS.eq(expectedStatus.name()))
                .execute();
        return updated == 1;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private ReferralRewardRecord map(Record record) {
        return new ReferralRewardRecord(
                record.get(REFERRAL_REWARDS.ID),
                record.get(REFERRAL_REWARDS.REFERRAL_ID),
                record.get(REFERRAL_REWARDS.REFERRER_ACCOUNT_NUMBER),
                record.get(REFERRAL_REWARDS.AMOUNT),
                record.get(REFERRAL_REWARDS.CURRENCY),
                ReferralRewardStatus.valueOf(record.get(REFERRAL_REWARDS.STATUS)),
                record.get(REFERRAL_REWARDS.LEDGER_TRANSACTION_ID),
                toInstant(record.get(REFERRAL_REWARDS.REQUESTED_AT)),
                record.get(REFERRAL_REWARDS.CREATED_AT).toInstant(),
                record.get(REFERRAL_REWARDS.UPDATED_AT).toInstant()
        );
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
