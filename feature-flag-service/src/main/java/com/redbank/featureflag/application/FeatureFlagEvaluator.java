package com.redbank.featureflag.application;

import com.redbank.featureflag.domain.FeatureEvaluationContext;
import com.redbank.featureflag.domain.FeatureFlagConditionRecord;
import com.redbank.featureflag.domain.FeatureFlagRecord;
import com.redbank.featureflag.domain.FeatureFlagTargetingRuleRecord;
import com.redbank.featureflag.domain.FeatureFlagUserOverrideRecord;
import com.redbank.featureflag.domain.RuleMatchType;
import com.redbank.featureflag.domain.TargetAttribute;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagEvaluator {
    public Map<String, Boolean> evaluate(
            List<FeatureFlagRecord> flags,
            List<FeatureFlagUserOverrideRecord> overrides,
            List<FeatureFlagTargetingRuleRecord> rules,
            FeatureEvaluationContext context
    ) {
        Map<UUID, FeatureFlagUserOverrideRecord> overridesByFlag = overrides.stream()
                .collect(Collectors.toMap(FeatureFlagUserOverrideRecord::featureFlagId, Function.identity()));
        Map<UUID, List<FeatureFlagTargetingRuleRecord>> rulesByFlag = rules.stream()
                .collect(Collectors.groupingBy(FeatureFlagTargetingRuleRecord::featureFlagId));

        return flags.stream().collect(Collectors.toMap(
                FeatureFlagRecord::key,
                flag -> evaluate(
                        flag,
                        overridesByFlag.get(flag.id()),
                        rulesByFlag.getOrDefault(flag.id(), List.of()),
                        context
                )
        ));
    }

    private boolean evaluate(
            FeatureFlagRecord flag,
            FeatureFlagUserOverrideRecord override,
            List<FeatureFlagTargetingRuleRecord> rules,
            FeatureEvaluationContext context
    ) {
        if (override != null) {
            return override.enabled();
        }
        return rules.stream()
                .filter(FeatureFlagTargetingRuleRecord::active)
                .filter(rule -> matches(rule, context))
                .findFirst()
                .map(FeatureFlagTargetingRuleRecord::enabled)
                .orElse(flag.enabled());
    }

    private boolean matches(FeatureFlagTargetingRuleRecord rule, FeatureEvaluationContext context) {
        if (rule.conditions().isEmpty()) {
            return false;
        }
        if (rule.matchType() == RuleMatchType.ALL) {
            return rule.conditions().stream().allMatch(condition -> matches(condition, context));
        }
        return rule.conditions().stream().anyMatch(condition -> matches(condition, context));
    }

    private boolean matches(FeatureFlagConditionRecord condition, FeatureEvaluationContext context) {
        String actual = attributeValue(condition.attribute(), context);
        if (actual == null) {
            return false;
        }
        List<String> expected = condition.values();
        if (isNumeric(condition.attribute())) {
            BigDecimal actualNumber = decimal(actual);
            return switch (condition.operator()) {
                case EQUALS -> actualNumber.compareTo(decimal(expected.getFirst())) == 0;
                case NOT_EQUALS -> actualNumber.compareTo(decimal(expected.getFirst())) != 0;
                case IN -> expected.stream().map(this::decimal).anyMatch(value -> actualNumber.compareTo(value) == 0);
                case NOT_IN -> expected.stream().map(this::decimal).noneMatch(value -> actualNumber.compareTo(value) == 0);
                case GREATER_THAN -> actualNumber.compareTo(decimal(expected.getFirst())) > 0;
                case GREATER_THAN_OR_EQUAL -> actualNumber.compareTo(decimal(expected.getFirst())) >= 0;
                case LESS_THAN -> actualNumber.compareTo(decimal(expected.getFirst())) < 0;
                case LESS_THAN_OR_EQUAL -> actualNumber.compareTo(decimal(expected.getFirst())) <= 0;
            };
        }
        return switch (condition.operator()) {
            case EQUALS -> actual.equals(expected.getFirst());
            case NOT_EQUALS -> !actual.equals(expected.getFirst());
            case IN -> expected.contains(actual);
            case NOT_IN -> !expected.contains(actual);
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> false;
        };
    }

    private boolean isNumeric(TargetAttribute attribute) {
        return attribute == TargetAttribute.AVAILABLE_BALANCE
                || attribute == TargetAttribute.CURRENT_BALANCE;
    }

    private String attributeValue(TargetAttribute attribute, FeatureEvaluationContext context) {
        return switch (attribute) {
            case USER_ID -> string(context.userId());
            case PROFILE_ID -> string(context.profileId());
            case PROFILE_TYPE -> string(context.profileType());
            case JOB_ID -> context.jobId();
            case ACCOUNT_NUMBER -> context.accountNumber();
            case ACCOUNT_TYPE -> string(context.accountType());
            case ACCOUNT_STATUS -> string(context.accountStatus());
            case CURRENCY -> context.currency() == null
                    ? null
                    : context.currency().toUpperCase(Locale.ROOT);
            case AVAILABLE_BALANCE -> string(context.availableBalance());
            case CURRENT_BALANCE -> string(context.currentBalance());
        };
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
