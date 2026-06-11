package com.redbank.featureflag.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.featureflag.domain.FeatureFlagConditionRecord;
import com.redbank.featureflag.domain.FeatureFlagTargetingAuditRecord;
import com.redbank.featureflag.domain.FeatureFlagTargetingRuleRecord;
import com.redbank.featureflag.domain.FeatureFlagUserOverrideRecord;
import com.redbank.featureflag.domain.RuleMatchType;
import com.redbank.featureflag.domain.TargetAttribute;
import com.redbank.featureflag.domain.TargetOperator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.featureflag.jooq.tables.FeatureFlagRuleConditions.FEATURE_FLAG_RULE_CONDITIONS;
import static com.redbank.featureflag.jooq.tables.FeatureFlagTargetingAuditLogs.FEATURE_FLAG_TARGETING_AUDIT_LOGS;
import static com.redbank.featureflag.jooq.tables.FeatureFlagTargetingRules.FEATURE_FLAG_TARGETING_RULES;
import static com.redbank.featureflag.jooq.tables.FeatureFlagUserOverrides.FEATURE_FLAG_USER_OVERRIDES;

@Repository
public class FeatureFlagTargetingRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public FeatureFlagTargetingRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    public List<FeatureFlagUserOverrideRecord> findOverrides(List<UUID> flagIds, UUID userId) {
        if (flagIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(FEATURE_FLAG_USER_OVERRIDES)
                .where(FEATURE_FLAG_USER_OVERRIDES.FEATURE_FLAG_ID.in(flagIds))
                .and(FEATURE_FLAG_USER_OVERRIDES.USER_ID.eq(userId))
                .fetch(this::mapOverride);
    }

    public List<FeatureFlagUserOverrideRecord> findAllOverrides(UUID flagId) {
        return dsl.selectFrom(FEATURE_FLAG_USER_OVERRIDES)
                .where(FEATURE_FLAG_USER_OVERRIDES.FEATURE_FLAG_ID.eq(flagId))
                .orderBy(FEATURE_FLAG_USER_OVERRIDES.USER_ID.asc())
                .fetch(this::mapOverride);
    }

    public List<FeatureFlagTargetingRuleRecord> findRules(List<UUID> flagIds) {
        if (flagIds.isEmpty()) {
            return List.of();
        }
        List<RuleRow> rules = dsl.selectFrom(FEATURE_FLAG_TARGETING_RULES)
                .where(FEATURE_FLAG_TARGETING_RULES.FEATURE_FLAG_ID.in(flagIds))
                .orderBy(
                        FEATURE_FLAG_TARGETING_RULES.FEATURE_FLAG_ID.asc(),
                        FEATURE_FLAG_TARGETING_RULES.PRIORITY.asc()
                )
                .fetch(record -> new RuleRow(
                        record.get(FEATURE_FLAG_TARGETING_RULES.ID),
                        record.get(FEATURE_FLAG_TARGETING_RULES.FEATURE_FLAG_ID),
                        record.get(FEATURE_FLAG_TARGETING_RULES.RULE_NAME),
                        record.get(FEATURE_FLAG_TARGETING_RULES.PRIORITY),
                        Boolean.TRUE.equals(record.get(FEATURE_FLAG_TARGETING_RULES.ENABLED)),
                        RuleMatchType.valueOf(record.get(FEATURE_FLAG_TARGETING_RULES.MATCH_TYPE)),
                        Boolean.TRUE.equals(record.get(FEATURE_FLAG_TARGETING_RULES.IS_ACTIVE)),
                        record.get(FEATURE_FLAG_TARGETING_RULES.UPDATED_BY),
                        record.get(FEATURE_FLAG_TARGETING_RULES.CREATED_AT).toInstant(),
                        record.get(FEATURE_FLAG_TARGETING_RULES.UPDATED_AT).toInstant()
                ));
        if (rules.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<FeatureFlagConditionRecord>> conditionsByRule = new LinkedHashMap<>();
        dsl.selectFrom(FEATURE_FLAG_RULE_CONDITIONS)
                .where(FEATURE_FLAG_RULE_CONDITIONS.TARGETING_RULE_ID.in(
                        rules.stream().map(RuleRow::id).toList()
                ))
                .orderBy(FEATURE_FLAG_RULE_CONDITIONS.CREATED_AT.asc())
                .fetch()
                .forEach(record -> conditionsByRule
                        .computeIfAbsent(
                                record.get(FEATURE_FLAG_RULE_CONDITIONS.TARGETING_RULE_ID),
                                ignored -> new ArrayList<>()
                        )
                        .add(mapCondition(record)));

        return rules.stream()
                .map(rule -> new FeatureFlagTargetingRuleRecord(
                        rule.id(),
                        rule.featureFlagId(),
                        rule.name(),
                        rule.priority(),
                        rule.enabled(),
                        rule.matchType(),
                        rule.active(),
                        rule.updatedBy(),
                        rule.createdAt(),
                        rule.updatedAt(),
                        conditionsByRule.getOrDefault(rule.id(), List.of())
                ))
                .toList();
    }

    public List<FeatureFlagTargetingRuleRecord> findRules(UUID flagId) {
        return findRules(List.of(flagId));
    }

    public void replace(
            UUID flagId,
            List<FeatureFlagUserOverrideRecord> overrides,
            List<FeatureFlagTargetingRuleRecord> rules
    ) {
        dsl.deleteFrom(FEATURE_FLAG_RULE_CONDITIONS)
                .where(FEATURE_FLAG_RULE_CONDITIONS.TARGETING_RULE_ID.in(
                        dsl.select(FEATURE_FLAG_TARGETING_RULES.ID)
                                .from(FEATURE_FLAG_TARGETING_RULES)
                                .where(FEATURE_FLAG_TARGETING_RULES.FEATURE_FLAG_ID.eq(flagId))
                ))
                .execute();
        dsl.deleteFrom(FEATURE_FLAG_TARGETING_RULES)
                .where(FEATURE_FLAG_TARGETING_RULES.FEATURE_FLAG_ID.eq(flagId))
                .execute();
        dsl.deleteFrom(FEATURE_FLAG_USER_OVERRIDES)
                .where(FEATURE_FLAG_USER_OVERRIDES.FEATURE_FLAG_ID.eq(flagId))
                .execute();

        overrides.forEach(this::saveOverride);
        rules.forEach(this::saveRule);
    }

    public void saveAudit(FeatureFlagTargetingAuditRecord audit) {
        dsl.insertInto(FEATURE_FLAG_TARGETING_AUDIT_LOGS)
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.ID, audit.id())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.FEATURE_FLAG_ID, audit.featureFlagId())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.FLAG_KEY, audit.key())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.OLD_TARGETING_JSON, audit.oldTargetingJson())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.NEW_TARGETING_JSON, audit.newTargetingJson())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.CHANGED_BY, audit.changedBy())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.REASON, audit.reason())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.REQUEST_ID, audit.requestId())
                .set(FEATURE_FLAG_TARGETING_AUDIT_LOGS.CREATED_AT, toOffsetDateTime(audit.createdAt()))
                .execute();
    }

    private void saveOverride(FeatureFlagUserOverrideRecord override) {
        dsl.insertInto(FEATURE_FLAG_USER_OVERRIDES)
                .set(FEATURE_FLAG_USER_OVERRIDES.ID, override.id())
                .set(FEATURE_FLAG_USER_OVERRIDES.FEATURE_FLAG_ID, override.featureFlagId())
                .set(FEATURE_FLAG_USER_OVERRIDES.USER_ID, override.userId())
                .set(FEATURE_FLAG_USER_OVERRIDES.ENABLED, override.enabled())
                .set(FEATURE_FLAG_USER_OVERRIDES.UPDATED_BY, override.updatedBy())
                .set(FEATURE_FLAG_USER_OVERRIDES.CREATED_AT, toOffsetDateTime(override.createdAt()))
                .set(FEATURE_FLAG_USER_OVERRIDES.UPDATED_AT, toOffsetDateTime(override.updatedAt()))
                .execute();
    }

    private void saveRule(FeatureFlagTargetingRuleRecord rule) {
        dsl.insertInto(FEATURE_FLAG_TARGETING_RULES)
                .set(FEATURE_FLAG_TARGETING_RULES.ID, rule.id())
                .set(FEATURE_FLAG_TARGETING_RULES.FEATURE_FLAG_ID, rule.featureFlagId())
                .set(FEATURE_FLAG_TARGETING_RULES.RULE_NAME, rule.name())
                .set(FEATURE_FLAG_TARGETING_RULES.PRIORITY, rule.priority())
                .set(FEATURE_FLAG_TARGETING_RULES.ENABLED, rule.enabled())
                .set(FEATURE_FLAG_TARGETING_RULES.MATCH_TYPE, rule.matchType().name())
                .set(FEATURE_FLAG_TARGETING_RULES.IS_ACTIVE, rule.active())
                .set(FEATURE_FLAG_TARGETING_RULES.UPDATED_BY, rule.updatedBy())
                .set(FEATURE_FLAG_TARGETING_RULES.CREATED_AT, toOffsetDateTime(rule.createdAt()))
                .set(FEATURE_FLAG_TARGETING_RULES.UPDATED_AT, toOffsetDateTime(rule.updatedAt()))
                .execute();
        rule.conditions().forEach(this::saveCondition);
    }

    private void saveCondition(FeatureFlagConditionRecord condition) {
        dsl.insertInto(FEATURE_FLAG_RULE_CONDITIONS)
                .set(FEATURE_FLAG_RULE_CONDITIONS.ID, condition.id())
                .set(FEATURE_FLAG_RULE_CONDITIONS.TARGETING_RULE_ID, condition.targetingRuleId())
                .set(FEATURE_FLAG_RULE_CONDITIONS.ATTRIBUTE, condition.attribute().name())
                .set(FEATURE_FLAG_RULE_CONDITIONS.OPERATOR, condition.operator().name())
                .set(FEATURE_FLAG_RULE_CONDITIONS.VALUES_JSON, writeValues(condition.values()))
                .set(FEATURE_FLAG_RULE_CONDITIONS.CREATED_AT, toOffsetDateTime(condition.createdAt()))
                .execute();
    }

    private FeatureFlagUserOverrideRecord mapOverride(Record record) {
        return new FeatureFlagUserOverrideRecord(
                record.get(FEATURE_FLAG_USER_OVERRIDES.ID),
                record.get(FEATURE_FLAG_USER_OVERRIDES.FEATURE_FLAG_ID),
                record.get(FEATURE_FLAG_USER_OVERRIDES.USER_ID),
                Boolean.TRUE.equals(record.get(FEATURE_FLAG_USER_OVERRIDES.ENABLED)),
                record.get(FEATURE_FLAG_USER_OVERRIDES.UPDATED_BY),
                record.get(FEATURE_FLAG_USER_OVERRIDES.CREATED_AT).toInstant(),
                record.get(FEATURE_FLAG_USER_OVERRIDES.UPDATED_AT).toInstant()
        );
    }

    private FeatureFlagConditionRecord mapCondition(Record record) {
        return new FeatureFlagConditionRecord(
                record.get(FEATURE_FLAG_RULE_CONDITIONS.ID),
                record.get(FEATURE_FLAG_RULE_CONDITIONS.TARGETING_RULE_ID),
                TargetAttribute.valueOf(record.get(FEATURE_FLAG_RULE_CONDITIONS.ATTRIBUTE)),
                TargetOperator.valueOf(record.get(FEATURE_FLAG_RULE_CONDITIONS.OPERATOR)),
                readValues(record.get(FEATURE_FLAG_RULE_CONDITIONS.VALUES_JSON)),
                record.get(FEATURE_FLAG_RULE_CONDITIONS.CREATED_AT).toInstant()
        );
    }

    private String writeValues(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Feature flag condition values could not be serialized", exception);
        }
    }

    private List<String> readValues(String valuesJson) {
        try {
            return objectMapper.readValue(valuesJson, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Feature flag condition values could not be read", exception);
        }
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record RuleRow(
            UUID id,
            UUID featureFlagId,
            String name,
            int priority,
            boolean enabled,
            RuleMatchType matchType,
            boolean active,
            UUID updatedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
