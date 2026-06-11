package com.redbank.featureflag.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.featureflag.api.v1.dto.EvaluateFeatureFlagsRequest;
import com.redbank.featureflag.api.v1.dto.EvaluateFeatureFlagsResponse;
import com.redbank.featureflag.api.v1.dto.EvaluatedFeatureFlag;
import com.redbank.featureflag.api.v1.dto.FeatureFlagCondition;
import com.redbank.featureflag.api.v1.dto.FeatureFlagSummary;
import com.redbank.featureflag.api.v1.dto.FeatureFlagTargetingResponse;
import com.redbank.featureflag.api.v1.dto.FeatureFlagTargetingRule;
import com.redbank.featureflag.api.v1.dto.FeatureFlagUserOverride;
import com.redbank.featureflag.api.v1.dto.GetFeatureFlagTargetingRequest;
import com.redbank.featureflag.api.v1.dto.ListFeatureFlagsRequest;
import com.redbank.featureflag.api.v1.dto.ListFeatureFlagsResponse;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagRequest;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagResponse;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagTargetingRequest;
import com.redbank.featureflag.config.InternalAccessProperties;
import com.redbank.featureflag.domain.FeatureEvaluationContext;
import com.redbank.featureflag.domain.FeatureFlagAuditRecord;
import com.redbank.featureflag.domain.FeatureFlagConditionRecord;
import com.redbank.featureflag.domain.FeatureFlagRecord;
import com.redbank.featureflag.domain.FeatureFlagTargetingAuditRecord;
import com.redbank.featureflag.domain.FeatureFlagTargetingRuleRecord;
import com.redbank.featureflag.domain.FeatureFlagUserOverrideRecord;
import com.redbank.featureflag.domain.TargetAttribute;
import com.redbank.featureflag.domain.TargetOperator;
import com.redbank.featureflag.infrastructure.FeatureFlagRepository;
import com.redbank.featureflag.infrastructure.FeatureFlagTargetingRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagTargetingRepository targetingRepository;
    private final FeatureFlagEvaluator evaluator;
    private final FeatureEvaluationContextClient evaluationContextClient;
    private final JwtTokenService jwtTokenService;
    private final InternalAccessProperties internalAccessProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FeatureFlagService(
            FeatureFlagRepository featureFlagRepository,
            FeatureFlagTargetingRepository targetingRepository,
            FeatureFlagEvaluator evaluator,
            FeatureEvaluationContextClient evaluationContextClient,
            JwtTokenService jwtTokenService,
            InternalAccessProperties internalAccessProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.targetingRepository = targetingRepository;
        this.evaluator = evaluator;
        this.evaluationContextClient = evaluationContextClient;
        this.jwtTokenService = jwtTokenService;
        this.internalAccessProperties = internalAccessProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListFeatureFlagsResponse list(
            String authorizationHeader,
            String internalServiceToken,
            ListFeatureFlagsRequest request
    ) {
        authorizeAdminOrInternal(authorizationHeader, internalServiceToken);
        List<String> keys = normalizeKeys(request.keys());
        boolean includeDisabled = Boolean.TRUE.equals(request.includeDisabled());
        return new ListFeatureFlagsResponse(featureFlagRepository.find(keys, includeDisabled).stream()
                .map(this::toSummary)
                .toList());
    }

    @Transactional(readOnly = true)
    public EvaluateFeatureFlagsResponse evaluate(
            String authorizationHeader,
            String internalServiceToken,
            RequestContext requestContext,
            EvaluateFeatureFlagsRequest request
    ) {
        boolean internal = matchesInternalToken(internalServiceToken);
        UUID userId;
        UUID profileId;
        String accountNumber;
        if (internal) {
            userId = parseRequiredUuid(request.subjectUserId(), "data.subjectUserId", "SUBJECT_USER_ID_UUID");
            profileId = parseOptionalUuid(request.profileId(), "data.profileId", "PROFILE_ID_UUID");
            accountNumber = normalizeAccountNumber(request.accountNumber(), "data.accountNumber");
        } else {
            VerifiedToken token = jwtTokenService.verifyUserToken(authorizationHeader);
            if (request.subjectUserId() != null
                    || request.profileId() != null
                    || request.accountNumber() != null) {
                throw new FeatureFlagUnauthorizedException(
                        "Users cannot provide feature evaluation subject attributes"
                );
            }
            userId = token.actorId();
            profileId = requestContext.account() != null
                    && requestContext.account().profileId() != null
                    && !requestContext.account().profileId().isBlank()
                    ? parseOptionalUuid(requestContext.account().profileId(), "context.account.profileId", "PROFILE_ID_UUID")
                    : token.profileId();
            accountNumber = requestContext.account() == null
                    ? null
                    : requestContext.account().accountNumber();
        }

        FeatureEvaluationContext context = evaluationContextClient.resolve(userId, profileId, accountNumber);
        List<FeatureFlagRecord> flags = featureFlagRepository.findForEvaluation(
                normalizeKeys(request.keys()),
                !internal
        );
        List<UUID> flagIds = flags.stream().map(FeatureFlagRecord::id).toList();
        var values = evaluator.evaluate(
                flags,
                targetingRepository.findOverrides(flagIds, userId),
                targetingRepository.findRules(flagIds),
                context
        );
        return new EvaluateFeatureFlagsResponse(flags.stream()
                .map(flag -> new EvaluatedFeatureFlag(flag.key(), values.get(flag.key())))
                .toList());
    }

    @Transactional
    public UpdateFeatureFlagResponse update(
            String authorizationHeader,
            String requestId,
            UpdateFeatureFlagRequest request
    ) {
        VerifiedToken actor = jwtTokenService.verifyAdminToken(authorizationHeader);
        String key = normalizeKey(request.key());
        FeatureFlagRecord current = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag was not found"));
        String description = request.description() == null
                ? current.description()
                : normalizeDescription(request.description());
        boolean clientVisible = request.clientVisible() == null
                ? current.clientVisible()
                : request.clientVisible();
        Instant now = clock.instant();
        if (!featureFlagRepository.update(
                current,
                request.enabled(),
                description,
                clientVisible,
                actor.actorId(),
                now
        )) {
            throw new FeatureFlagConflictException("Feature flag changed concurrently");
        }
        featureFlagRepository.saveAudit(new FeatureFlagAuditRecord(
                UUID.randomUUID(),
                current.id(),
                current.key(),
                current.enabled(),
                request.enabled(),
                current.description(),
                description,
                current.clientVisible(),
                clientVisible,
                actor.actorId(),
                request.reason().trim(),
                requestId,
                now
        ));
        FeatureFlagRecord updated = current.withUpdate(
                request.enabled(),
                description,
                clientVisible,
                actor.actorId(),
                now
        );
        return new UpdateFeatureFlagResponse(toSummary(updated), now);
    }

    @Transactional(readOnly = true)
    public FeatureFlagTargetingResponse getTargeting(
            String authorizationHeader,
            GetFeatureFlagTargetingRequest request
    ) {
        jwtTokenService.verifyAdminToken(authorizationHeader);
        FeatureFlagRecord flag = featureFlagRepository.findByKey(normalizeKey(request.key()))
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag was not found"));
        return toTargetingResponse(
                flag,
                targetingRepository.findAllOverrides(flag.id()),
                targetingRepository.findRules(flag.id()),
                flag.updatedAt()
        );
    }

    @Transactional
    public FeatureFlagTargetingResponse updateTargeting(
            String authorizationHeader,
            String requestId,
            UpdateFeatureFlagTargetingRequest request
    ) {
        VerifiedToken actor = jwtTokenService.verifyAdminToken(authorizationHeader);
        FeatureFlagRecord flag = featureFlagRepository.findByKey(normalizeKey(request.key()))
                .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag was not found"));
        validateTargeting(request);

        List<FeatureFlagUserOverrideRecord> oldOverrides = targetingRepository.findAllOverrides(flag.id());
        List<FeatureFlagTargetingRuleRecord> oldRules = targetingRepository.findRules(flag.id());
        Instant now = clock.instant();
        List<FeatureFlagUserOverrideRecord> overrides = toOverrideRecords(
                flag.id(),
                request.userOverrides(),
                actor.actorId(),
                now
        );
        List<FeatureFlagTargetingRuleRecord> rules = toRuleRecords(
                flag.id(),
                request.rules(),
                actor.actorId(),
                now
        );
        if (!featureFlagRepository.touch(flag, actor.actorId(), now)) {
            throw new FeatureFlagConflictException("Feature flag targeting changed concurrently");
        }
        targetingRepository.replace(flag.id(), overrides, rules);

        FeatureFlagTargetingResponse previous = toTargetingResponse(
                flag,
                oldOverrides,
                oldRules,
                flag.updatedAt()
        );
        FeatureFlagTargetingResponse updated = toTargetingResponse(flag, overrides, rules, now);
        targetingRepository.saveAudit(new FeatureFlagTargetingAuditRecord(
                UUID.randomUUID(),
                flag.id(),
                flag.key(),
                writeJson(previous),
                writeJson(updated),
                actor.actorId(),
                request.reason().trim(),
                requestId,
                now
        ));
        return updated;
    }

    private void authorizeAdminOrInternal(String authorizationHeader, String internalServiceToken) {
        if (!matchesInternalToken(internalServiceToken)) {
            jwtTokenService.verifyAdminToken(authorizationHeader);
        }
    }

    private boolean matchesInternalToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        byte[] expected = internalAccessProperties.serviceTokenSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private void validateTargeting(UpdateFeatureFlagTargetingRequest request) {
        List<FeatureFlagUserOverride> overrides = safeList(request.userOverrides());
        Set<UUID> userIds = new HashSet<>();
        for (FeatureFlagUserOverride override : overrides) {
            UUID userId = parseRequiredUuid(override.userId(), "data.userOverrides.userId", "USER_ID_UUID");
            if (!userIds.add(userId)) {
                throw validation(
                        "data.userOverrides",
                        "user overrides must contain unique user IDs",
                        "DUPLICATE_USER_OVERRIDE"
                );
            }
        }

        Set<Integer> priorities = new HashSet<>();
        for (FeatureFlagTargetingRule rule : safeList(request.rules())) {
            if (!priorities.add(rule.priority())) {
                throw validation(
                        "data.rules",
                        "rules must contain unique priorities",
                        "DUPLICATE_RULE_PRIORITY"
                );
            }
            if (rule.conditions() == null || rule.conditions().isEmpty()) {
                throw validation(
                        "data.rules.conditions",
                        "each targeting rule requires at least one condition",
                        "RULE_CONDITION_REQUIRED"
                );
            }
            rule.conditions().forEach(this::validateCondition);
        }
    }

    private void validateCondition(FeatureFlagCondition condition) {
        List<String> values = condition.values().stream()
                .map(value -> value == null ? null : value.trim())
                .toList();
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw validation(
                    "data.rules.conditions.values",
                    "condition values must not be blank",
                    "CONDITION_VALUE_BLANK"
            );
        }
        boolean membership = condition.operator() == TargetOperator.IN
                || condition.operator() == TargetOperator.NOT_IN;
        if (!membership && values.size() != 1) {
            throw validation(
                    "data.rules.conditions.values",
                    "this operator requires exactly one value",
                    "CONDITION_VALUE_COUNT"
            );
        }
        boolean numericOperator = switch (condition.operator()) {
            case GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL -> true;
            default -> false;
        };
        boolean numericAttribute = condition.attribute() == TargetAttribute.AVAILABLE_BALANCE
                || condition.attribute() == TargetAttribute.CURRENT_BALANCE;
        if (numericOperator && !numericAttribute) {
            throw validation(
                    "data.rules.conditions.operator",
                    "numeric operators can only target balance attributes",
                    "CONDITION_OPERATOR_ATTRIBUTE"
            );
        }
        if (numericAttribute) {
            values.forEach(value -> parseDecimal(value, "data.rules.conditions.values"));
        }
        if (condition.attribute() == TargetAttribute.USER_ID
                || condition.attribute() == TargetAttribute.PROFILE_ID) {
            values.forEach(value -> parseRequiredUuid(value, "data.rules.conditions.values", "CONDITION_UUID"));
        }
        if (condition.attribute() == TargetAttribute.ACCOUNT_NUMBER
                && values.stream().anyMatch(value -> !value.matches("\\d{18}"))) {
            throw validation(
                    "data.rules.conditions.values",
                    "account number condition values must be 18 digits",
                    "CONDITION_ACCOUNT_NUMBER"
            );
        }
        switch (condition.attribute()) {
            case PROFILE_TYPE -> validateEnumValues(
                    values,
                    ProfileType.class,
                    "CONDITION_PROFILE_TYPE"
            );
            case ACCOUNT_TYPE -> validateEnumValues(
                    values,
                    AccountType.class,
                    "CONDITION_ACCOUNT_TYPE"
            );
            case ACCOUNT_STATUS -> validateEnumValues(
                    values,
                    AccountStatus.class,
                    "CONDITION_ACCOUNT_STATUS"
            );
            case CURRENCY -> {
                if (values.stream().anyMatch(value -> !value.matches("[A-Za-z]{3}"))) {
                    throw validation(
                            "data.rules.conditions.values",
                            "currency condition values must be 3 letters",
                            "CONDITION_CURRENCY"
                    );
                }
            }
            default -> {
            }
        }
    }

    private List<FeatureFlagUserOverrideRecord> toOverrideRecords(
            UUID flagId,
            List<FeatureFlagUserOverride> overrides,
            UUID actorId,
            Instant now
    ) {
        return safeList(overrides).stream()
                .map(override -> new FeatureFlagUserOverrideRecord(
                        UUID.randomUUID(),
                        flagId,
                        UUID.fromString(override.userId()),
                        override.enabled(),
                        actorId,
                        now,
                        now
                ))
                .toList();
    }

    private List<FeatureFlagTargetingRuleRecord> toRuleRecords(
            UUID flagId,
            List<FeatureFlagTargetingRule> rules,
            UUID actorId,
            Instant now
    ) {
        return safeList(rules).stream()
                .map(rule -> {
                    UUID ruleId = UUID.randomUUID();
                    List<FeatureFlagConditionRecord> conditions = rule.conditions().stream()
                            .map(condition -> new FeatureFlagConditionRecord(
                                    UUID.randomUUID(),
                                    ruleId,
                                    condition.attribute(),
                                    condition.operator(),
                                    normalizeConditionValues(condition),
                                    now
                            ))
                            .toList();
                    return new FeatureFlagTargetingRuleRecord(
                            ruleId,
                            flagId,
                            rule.name().trim(),
                            rule.priority(),
                            rule.enabled(),
                            rule.matchType(),
                            rule.active(),
                            actorId,
                            now,
                            now,
                            conditions
                    );
                })
                .toList();
    }

    private List<String> normalizeConditionValues(FeatureFlagCondition condition) {
        return condition.values().stream()
                .map(String::trim)
                .map(value -> switch (condition.attribute()) {
                    case USER_ID, PROFILE_ID -> UUID.fromString(value).toString();
                    case PROFILE_TYPE, ACCOUNT_TYPE, ACCOUNT_STATUS, CURRENCY -> value.toUpperCase(Locale.ROOT);
                    case AVAILABLE_BALANCE, CURRENT_BALANCE ->
                            new BigDecimal(value).stripTrailingZeros().toPlainString();
                    default -> value;
                })
                .distinct()
                .toList();
    }

    private FeatureFlagTargetingResponse toTargetingResponse(
            FeatureFlagRecord flag,
            List<FeatureFlagUserOverrideRecord> overrides,
            List<FeatureFlagTargetingRuleRecord> rules,
            Instant updatedAt
    ) {
        return new FeatureFlagTargetingResponse(
                flag.key(),
                overrides.stream()
                        .map(override -> new FeatureFlagUserOverride(
                                override.userId().toString(),
                                override.enabled()
                        ))
                        .toList(),
                rules.stream()
                        .map(rule -> new FeatureFlagTargetingRule(
                                rule.name(),
                                rule.priority(),
                                rule.enabled(),
                                rule.matchType(),
                                rule.active(),
                                rule.conditions().stream()
                                        .map(condition -> new FeatureFlagCondition(
                                                condition.attribute(),
                                                condition.operator(),
                                                condition.values()
                                        ))
                                        .toList()
                        ))
                        .toList(),
                updatedAt
        );
    }

    private List<String> normalizeKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.stream()
                .map(key -> {
                    if (key == null || key.isBlank()) {
                        throw validation("data.keys", "keys must not contain blank values", "FLAG_KEY_BLANK");
                    }
                    return normalizeKey(key);
                })
                .distinct()
                .toList();
    }

    private String normalizeKey(String key) {
        String normalized = Objects.requireNonNull(key).trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 160) {
            throw validation("data.key", "key must be at most 160 characters", "FLAG_KEY_SIZE");
        }
        if (!normalized.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) {
            throw validation(
                    "data.key",
                    "key must contain lowercase words separated by dots or hyphens",
                    "FLAG_KEY_FORMAT"
            );
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeAccountNumber(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.matches("\\d{18}")) {
            throw validation(field, "accountNumber must be 18 digits", "ACCOUNT_NUMBER_FORMAT");
        }
        return value;
    }

    private UUID parseRequiredUuid(String value, String field, String code) {
        if (value == null || value.isBlank()) {
            throw validation(field, field + " is required", code);
        }
        return parseOptionalUuid(value, field, code);
    }

    private UUID parseOptionalUuid(String value, String field, String code) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation(field, field + " must be a UUID", code);
        }
    }

    private BigDecimal parseDecimal(String value, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw validation(field, "balance condition values must be decimal numbers", "CONDITION_DECIMAL");
        }
    }

    private <E extends Enum<E>> void validateEnumValues(
            List<String> values,
            Class<E> enumType,
            String code
    ) {
        try {
            values.forEach(value -> Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            throw validation(
                    "data.rules.conditions.values",
                    "condition value is not supported for " + enumType.getSimpleName(),
                    code
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Feature flag targeting audit could not be serialized", exception);
        }
    }

    private FeatureFlagSummary toSummary(FeatureFlagRecord flag) {
        return new FeatureFlagSummary(
                flag.key(),
                flag.enabled(),
                flag.description(),
                flag.clientVisible()
        );
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private FeatureFlagValidationException validation(String field, String message, String code) {
        return new FeatureFlagValidationException(List.of(new StatusDetail(field, message, code)));
    }
}
