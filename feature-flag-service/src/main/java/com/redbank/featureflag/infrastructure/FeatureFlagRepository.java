package com.redbank.featureflag.infrastructure;

import com.redbank.featureflag.domain.FeatureFlagAuditRecord;
import com.redbank.featureflag.domain.FeatureFlagRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.redbank.featureflag.jooq.tables.FeatureFlagAuditLogs.FEATURE_FLAG_AUDIT_LOGS;
import static com.redbank.featureflag.jooq.tables.FeatureFlags.FEATURE_FLAGS;

@Repository
public class FeatureFlagRepository {
    private final DSLContext dsl;

    public FeatureFlagRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<FeatureFlagRecord> find(List<String> keys, boolean includeDisabled) {
        Condition condition = DSL.noCondition();
        if (keys != null && !keys.isEmpty()) {
            condition = condition.and(FEATURE_FLAGS.FLAG_KEY.in(keys));
        }
        if (!includeDisabled) {
            condition = condition.and(FEATURE_FLAGS.ENABLED.isTrue());
        }
        return dsl.selectFrom(FEATURE_FLAGS)
                .where(condition)
                .orderBy(FEATURE_FLAGS.FLAG_KEY.asc())
                .fetch(this::map);
    }

    public List<FeatureFlagRecord> findForEvaluation(List<String> keys, boolean clientOnly) {
        Condition condition = DSL.noCondition();
        if (keys != null && !keys.isEmpty()) {
            condition = condition.and(FEATURE_FLAGS.FLAG_KEY.in(keys));
        }
        if (clientOnly) {
            condition = condition.and(FEATURE_FLAGS.CLIENT_VISIBLE.isTrue());
        }
        return dsl.selectFrom(FEATURE_FLAGS)
                .where(condition)
                .orderBy(FEATURE_FLAGS.FLAG_KEY.asc())
                .fetch(this::map);
    }

    public Optional<FeatureFlagRecord> findByKey(String key) {
        return dsl.selectFrom(FEATURE_FLAGS)
                .where(FEATURE_FLAGS.FLAG_KEY.eq(key))
                .fetchOptional(this::map);
    }

    public boolean update(
            FeatureFlagRecord current,
            boolean enabled,
            String description,
            boolean clientVisible,
            UUID updatedBy,
            Instant updatedAt
    ) {
        int updated = dsl.update(FEATURE_FLAGS)
                .set(FEATURE_FLAGS.ENABLED, enabled)
                .set(FEATURE_FLAGS.DESCRIPTION, description)
                .set(FEATURE_FLAGS.CLIENT_VISIBLE, clientVisible)
                .set(FEATURE_FLAGS.UPDATED_BY, updatedBy)
                .set(FEATURE_FLAGS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(FEATURE_FLAGS.ID.eq(current.id()))
                .and(FEATURE_FLAGS.UPDATED_AT.eq(toOffsetDateTime(current.updatedAt())))
                .execute();
        return updated == 1;
    }

    public boolean touch(FeatureFlagRecord current, UUID updatedBy, Instant updatedAt) {
        int updated = dsl.update(FEATURE_FLAGS)
                .set(FEATURE_FLAGS.UPDATED_BY, updatedBy)
                .set(FEATURE_FLAGS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(FEATURE_FLAGS.ID.eq(current.id()))
                .and(FEATURE_FLAGS.UPDATED_AT.eq(toOffsetDateTime(current.updatedAt())))
                .execute();
        return updated == 1;
    }

    public void saveAudit(FeatureFlagAuditRecord audit) {
        dsl.insertInto(FEATURE_FLAG_AUDIT_LOGS)
                .set(FEATURE_FLAG_AUDIT_LOGS.ID, audit.id())
                .set(FEATURE_FLAG_AUDIT_LOGS.FEATURE_FLAG_ID, audit.featureFlagId())
                .set(FEATURE_FLAG_AUDIT_LOGS.FLAG_KEY, audit.key())
                .set(FEATURE_FLAG_AUDIT_LOGS.OLD_ENABLED, audit.oldEnabled())
                .set(FEATURE_FLAG_AUDIT_LOGS.NEW_ENABLED, audit.newEnabled())
                .set(FEATURE_FLAG_AUDIT_LOGS.OLD_DESCRIPTION, audit.oldDescription())
                .set(FEATURE_FLAG_AUDIT_LOGS.NEW_DESCRIPTION, audit.newDescription())
                .set(FEATURE_FLAG_AUDIT_LOGS.OLD_CLIENT_VISIBLE, audit.oldClientVisible())
                .set(FEATURE_FLAG_AUDIT_LOGS.NEW_CLIENT_VISIBLE, audit.newClientVisible())
                .set(FEATURE_FLAG_AUDIT_LOGS.CHANGED_BY, audit.changedBy())
                .set(FEATURE_FLAG_AUDIT_LOGS.REASON, audit.reason())
                .set(FEATURE_FLAG_AUDIT_LOGS.REQUEST_ID, audit.requestId())
                .set(FEATURE_FLAG_AUDIT_LOGS.CREATED_AT, toOffsetDateTime(audit.createdAt()))
                .execute();
    }

    private FeatureFlagRecord map(Record record) {
        return new FeatureFlagRecord(
                record.get(FEATURE_FLAGS.ID),
                record.get(FEATURE_FLAGS.FLAG_KEY),
                Boolean.TRUE.equals(record.get(FEATURE_FLAGS.ENABLED)),
                record.get(FEATURE_FLAGS.DESCRIPTION),
                Boolean.TRUE.equals(record.get(FEATURE_FLAGS.CLIENT_VISIBLE)),
                record.get(FEATURE_FLAGS.UPDATED_BY),
                record.get(FEATURE_FLAGS.CREATED_AT).toInstant(),
                record.get(FEATURE_FLAGS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
