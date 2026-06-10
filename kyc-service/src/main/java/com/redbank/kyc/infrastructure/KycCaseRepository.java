package com.redbank.kyc.infrastructure;

import com.redbank.contracts.kyc.KycStatus;
import com.redbank.kyc.domain.KycCaseHistoryRecord;
import com.redbank.kyc.domain.KycCaseRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.kyc.jooq.tables.KycCaseHistory.KYC_CASE_HISTORY;
import static com.redbank.kyc.jooq.tables.KycCases.KYC_CASES;

@Repository
public class KycCaseRepository {
    private final DSLContext dsl;

    public KycCaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<KycCaseRecord> findByUserId(UUID userId) {
        return dsl.selectFrom(KYC_CASES)
                .where(KYC_CASES.USER_ID.eq(userId))
                .fetchOptional(this::map);
    }

    public void save(KycCaseRecord kycCase) {
        dsl.insertInto(KYC_CASES)
                .set(KYC_CASES.ID, kycCase.id())
                .set(KYC_CASES.USER_ID, kycCase.userId())
                .set(KYC_CASES.STATUS, kycCase.status().name())
                .set(KYC_CASES.REJECTION_REASON, kycCase.rejectionReason())
                .set(KYC_CASES.COMPLETED_AT, toOffsetDateTime(kycCase.completedAt()))
                .set(KYC_CASES.CREATED_AT, toOffsetDateTime(kycCase.createdAt()))
                .set(KYC_CASES.UPDATED_AT, toOffsetDateTime(kycCase.updatedAt()))
                .execute();
    }

    public boolean updateStatus(
            UUID caseId,
            KycStatus expectedStatus,
            KycStatus nextStatus,
            String rejectionReason,
            Instant completedAt,
            Instant updatedAt
    ) {
        int updated = dsl.update(KYC_CASES)
                .set(KYC_CASES.STATUS, nextStatus.name())
                .set(KYC_CASES.REJECTION_REASON, rejectionReason)
                .set(KYC_CASES.COMPLETED_AT, toOffsetDateTime(completedAt))
                .set(KYC_CASES.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(KYC_CASES.ID.eq(caseId))
                .and(KYC_CASES.STATUS.eq(expectedStatus.name()))
                .execute();
        return updated == 1;
    }

    public void saveHistory(KycCaseHistoryRecord history) {
        dsl.insertInto(KYC_CASE_HISTORY)
                .set(KYC_CASE_HISTORY.ID, history.id())
                .set(KYC_CASE_HISTORY.KYC_CASE_ID, history.kycCaseId())
                .set(
                        KYC_CASE_HISTORY.PREVIOUS_STATUS,
                        history.previousStatus() == null ? null : history.previousStatus().name()
                )
                .set(KYC_CASE_HISTORY.NEW_STATUS, history.newStatus().name())
                .set(KYC_CASE_HISTORY.REASON, history.reason())
                .set(KYC_CASE_HISTORY.ACTOR_ID, history.actorId())
                .set(KYC_CASE_HISTORY.CREATED_AT, toOffsetDateTime(history.createdAt()))
                .execute();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private KycCaseRecord map(Record record) {
        return new KycCaseRecord(
                record.get(KYC_CASES.ID),
                record.get(KYC_CASES.USER_ID),
                KycStatus.valueOf(record.get(KYC_CASES.STATUS)),
                record.get(KYC_CASES.REJECTION_REASON),
                toInstant(record.get(KYC_CASES.COMPLETED_AT)),
                record.get(KYC_CASES.CREATED_AT).toInstant(),
                record.get(KYC_CASES.UPDATED_AT).toInstant()
        );
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
