package com.redbank.featureflag.application;

import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.featureflag.api.v1.dto.FeatureFlagSummary;
import com.redbank.featureflag.api.v1.dto.ListFeatureFlagsRequest;
import com.redbank.featureflag.api.v1.dto.ListFeatureFlagsResponse;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagRequest;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagResponse;
import com.redbank.featureflag.config.InternalAccessProperties;
import com.redbank.featureflag.domain.FeatureFlagAuditRecord;
import com.redbank.featureflag.domain.FeatureFlagRecord;
import com.redbank.featureflag.infrastructure.FeatureFlagRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository featureFlagRepository;
    private final JwtTokenService jwtTokenService;
    private final InternalAccessProperties internalAccessProperties;
    private final Clock clock;

    public FeatureFlagService(
            FeatureFlagRepository featureFlagRepository,
            JwtTokenService jwtTokenService,
            InternalAccessProperties internalAccessProperties,
            Clock clock
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.jwtTokenService = jwtTokenService;
        this.internalAccessProperties = internalAccessProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListFeatureFlagsResponse list(
            String authorizationHeader,
            String internalServiceToken,
            ListFeatureFlagsRequest request
    ) {
        authorizeList(authorizationHeader, internalServiceToken);
        List<String> keys = normalizeKeys(request.keys());
        boolean includeDisabled = Boolean.TRUE.equals(request.includeDisabled());
        return new ListFeatureFlagsResponse(featureFlagRepository.find(keys, includeDisabled).stream()
                .map(this::toSummary)
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
        Instant now = clock.instant();
        if (!featureFlagRepository.update(current, request.enabled(), description, actor.actorId(), now)) {
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
                actor.actorId(),
                request.reason().trim(),
                requestId,
                now
        ));
        FeatureFlagRecord updated = current.withUpdate(request.enabled(), description, actor.actorId(), now);
        return new UpdateFeatureFlagResponse(toSummary(updated), now);
    }

    private void authorizeList(String authorizationHeader, String internalServiceToken) {
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

    private FeatureFlagSummary toSummary(FeatureFlagRecord flag) {
        return new FeatureFlagSummary(flag.key(), flag.enabled(), flag.description());
    }

    private FeatureFlagValidationException validation(String field, String message, String code) {
        return new FeatureFlagValidationException(List.of(new StatusDetail(field, message, code)));
    }
}
