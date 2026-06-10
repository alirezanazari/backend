package com.redbank.kyc.application;

import com.redbank.contracts.common.Money;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.contracts.kyc.KycStatus;
import com.redbank.kyc.api.v1.dto.CompleteKycRequest;
import com.redbank.kyc.api.v1.dto.CompleteKycResponse;
import com.redbank.kyc.api.v1.dto.GetKycStatusRequest;
import com.redbank.kyc.api.v1.dto.GetKycStatusResponse;
import com.redbank.kyc.api.v1.dto.JobSummary;
import com.redbank.kyc.api.v1.dto.ListJobsRequest;
import com.redbank.kyc.api.v1.dto.ListJobsResponse;
import com.redbank.kyc.api.v1.dto.SetReferralRequest;
import com.redbank.kyc.api.v1.dto.SetReferralResponse;
import com.redbank.kyc.config.KycProperties;
import com.redbank.kyc.domain.KycCaseHistoryRecord;
import com.redbank.kyc.domain.KycCaseRecord;
import com.redbank.kyc.domain.ReferralRecord;
import com.redbank.kyc.domain.ReferralRewardRecord;
import com.redbank.kyc.domain.ReferralRewardStatus;
import com.redbank.kyc.domain.ReferralStatus;
import com.redbank.kyc.infrastructure.JobRepository;
import com.redbank.kyc.infrastructure.KycCaseRepository;
import com.redbank.kyc.infrastructure.ReferralRepository;
import com.redbank.kyc.infrastructure.ReferralRewardRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KycService {
    private static final String REFERRAL_REWARD_FLAG = "referral.reward.enabled";

    private final JobRepository jobRepository;
    private final KycCaseRepository kycCaseRepository;
    private final ReferralRepository referralRepository;
    private final ReferralRewardRepository referralRewardRepository;
    private final JwtTokenService jwtTokenService;
    private final ReferrerAccountClient referrerAccountClient;
    private final ReferralRewardClient referralRewardClient;
    private final FeatureFlagClient featureFlagClient;
    private final KycProperties kycProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public KycService(
            JobRepository jobRepository,
            KycCaseRepository kycCaseRepository,
            ReferralRepository referralRepository,
            ReferralRewardRepository referralRewardRepository,
            JwtTokenService jwtTokenService,
            ReferrerAccountClient referrerAccountClient,
            ReferralRewardClient referralRewardClient,
            FeatureFlagClient featureFlagClient,
            KycProperties kycProperties,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.kycCaseRepository = kycCaseRepository;
        this.referralRepository = referralRepository;
        this.referralRewardRepository = referralRewardRepository;
        this.jwtTokenService = jwtTokenService;
        this.referrerAccountClient = referrerAccountClient;
        this.referralRewardClient = referralRewardClient;
        this.featureFlagClient = featureFlagClient;
        this.kycProperties = kycProperties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListJobsResponse listJobs(ListJobsRequest request) {
        return new ListJobsResponse(jobRepository.findActive().stream()
                .map(job -> new JobSummary(job.id(), job.code(), job.title()))
                .toList());
    }

    @Transactional
    public SetReferralResponse setReferral(String authorizationHeader, SetReferralRequest request) {
        VerifiedToken token = jwtTokenService.verifyReferralToken(authorizationHeader);
        String referralCode = request.referralCode().trim().toUpperCase(Locale.ROOT);
        ReferralRecord existing = referralRepository.findByReferredUserId(token.userId()).orElse(null);
        if (existing != null) {
            if (!existing.referralCode().equals(referralCode)) {
                throw new KycConflictException("Referral cannot be changed after it is set");
            }
            return toReferralResponse(existing);
        }

        ResolvedReferrer referrer = referrerAccountClient.resolveByCode(referralCode);
        if (referrer == null || referrer.userId() == null) {
            throw new KycNotFoundException("Referral code was not found");
        }
        if (referrer.userId().equals(token.userId())) {
            throw new KycBusinessRuleException("A user cannot refer themselves");
        }

        Instant now = clock.instant();
        ReferralRecord referral = new ReferralRecord(
                UUID.randomUUID(),
                token.userId(),
                referrer.userId(),
                referralCode,
                referrer.displayName(),
                ReferralStatus.PENDING_KYC,
                now,
                now
        );
        try {
            referralRepository.save(referral);
            return toReferralResponse(referral);
        } catch (DataIntegrityViolationException exception) {
            ReferralRecord concurrent = referralRepository.findByReferredUserId(token.userId())
                    .orElseThrow(() -> new KycConflictException("Referral could not be saved"));
            if (!concurrent.referralCode().equals(referralCode)) {
                throw new KycConflictException("Referral cannot be changed after it is set");
            }
            return toReferralResponse(concurrent);
        }
    }

    @Transactional(readOnly = true)
    public GetKycStatusResponse getStatus(
            String authorizationHeader,
            GetKycStatusRequest request
    ) {
        VerifiedToken token = jwtTokenService.verifyUserOrAdminToken(authorizationHeader);
        UUID targetUserId = targetUserId(token, request.userId());
        return kycCaseRepository.findByUserId(targetUserId)
                .map(this::toStatusResponse)
                .orElseGet(() -> new GetKycStatusResponse(
                        null,
                        KycStatus.NOT_STARTED,
                        AccountCompletionStatus.KYC_REQUIRED,
                        null,
                        null
                ));
    }

    public CompleteKycResponse complete(String authorizationHeader, CompleteKycRequest request) {
        VerifiedToken actor = jwtTokenService.verifyAdminToken(authorizationHeader);
        validateCompletion(request);
        UUID userId = parseUuid(request.userId(), "data.userId", "USER_ID_UUID");

        CompletionPlan plan = Objects.requireNonNull(transactionTemplate.execute(
                status -> prepareCompletion(userId, actor.userId(), request)
        ));
        boolean rewardRequested = requestReferralReward(plan);
        return new CompleteKycResponse(plan.kycCase().id().toString(), plan.kycCase().status(), rewardRequested);
    }

    private CompletionPlan prepareCompletion(UUID userId, UUID actorId, CompleteKycRequest request) {
        Instant now = clock.instant();
        KycCaseRecord current = kycCaseRepository.findByUserId(userId).orElse(null);
        KycCaseRecord completed = completeCase(current, userId, actorId, request, now);

        ReferralRecord referral = request.status() == KycStatus.APPROVED
                ? referralRepository.findByReferredUserId(userId).orElse(null)
                : null;
        ReferralRewardRecord reward = referral == null ? null : ensureReward(referral, now);
        return new CompletionPlan(completed, referral, reward);
    }

    private KycCaseRecord completeCase(
            KycCaseRecord current,
            UUID userId,
            UUID actorId,
            CompleteKycRequest request,
            Instant now
    ) {
        String reason = normalizedReason(request.reason());
        String rejectionReason = request.status() == KycStatus.REJECTED ? reason : null;
        if (current == null) {
            KycCaseRecord created = new KycCaseRecord(
                    UUID.randomUUID(),
                    userId,
                    request.status(),
                    rejectionReason,
                    now,
                    now,
                    now
            );
            kycCaseRepository.save(created);
            saveHistory(created.id(), null, request.status(), reason, actorId, now);
            return created;
        }
        if (current.status() == KycStatus.APPROVED && request.status() != KycStatus.APPROVED) {
            throw new KycBusinessRuleException("Approved KYC cannot be changed");
        }
        if (current.status() == request.status()) {
            return current;
        }
        if (!kycCaseRepository.updateStatus(
                current.id(),
                current.status(),
                request.status(),
                rejectionReason,
                now,
                now
        )) {
            throw new KycConflictException("KYC status changed concurrently");
        }
        saveHistory(current.id(), current.status(), request.status(), reason, actorId, now);
        return current.withStatus(request.status(), rejectionReason, now, now);
    }

    private ReferralRewardRecord ensureReward(ReferralRecord referral, Instant now) {
        ReferralRewardRecord existing = referralRewardRepository.findByReferralId(referral.id()).orElse(null);
        if (existing != null) {
            return existing;
        }
        ReferralRewardRecord reward = new ReferralRewardRecord(
                UUID.randomUUID(),
                referral.id(),
                null,
                kycProperties.referralRewardAmount(),
                kycProperties.referralRewardCurrency(),
                ReferralRewardStatus.PENDING,
                null,
                null,
                now,
                now
        );
        try {
            referralRewardRepository.save(reward);
            referralRepository.updateStatus(
                    referral.id(),
                    ReferralStatus.PENDING_KYC,
                    ReferralStatus.REWARD_PENDING,
                    now
            );
            return reward;
        } catch (DataIntegrityViolationException exception) {
            return referralRewardRepository.findByReferralId(referral.id())
                    .orElseThrow(() -> new KycConflictException("Referral reward could not be prepared"));
        }
    }

    private boolean requestReferralReward(CompletionPlan plan) {
        if (plan.referral() == null
                || plan.reward() == null
                || plan.reward().status() == ReferralRewardStatus.POSTED) {
            return false;
        }
        if (!featureFlagClient.isEnabled(REFERRAL_REWARD_FLAG)) {
            return false;
        }
        ActiveReferrerAccount account = referrerAccountClient.findActiveAccount(plan.referral().referrerUserId());
        if (account == null || account.accountNumber() == null || account.accountNumber().isBlank()) {
            throw new KycBusinessRuleException("Referrer must have an active account");
        }
        if (!kycProperties.referralRewardCurrency().equalsIgnoreCase(account.currency())) {
            throw new KycBusinessRuleException("Referrer account currency is not eligible for the reward");
        }

        ReferralRewardResult result = referralRewardClient.requestReward(new ReferralRewardCommand(
                plan.reward().id().toString(),
                account.accountNumber(),
                new Money(plan.reward().amount(), plan.reward().currency())
        ));
        if (result == null || result.status() == null) {
            throw new KycExternalServiceException("Referral reward response was empty", null);
        }

        ReferralRewardStatus nextStatus = "POSTED".equalsIgnoreCase(result.status())
                || "SUCCESS".equalsIgnoreCase(result.status())
                ? ReferralRewardStatus.POSTED
                : ReferralRewardStatus.REQUESTED;
        Instant now = clock.instant();
        transactionTemplate.executeWithoutResult(status -> {
            if (!referralRewardRepository.markResult(
                    plan.reward().id(),
                    plan.reward().status(),
                    nextStatus,
                    account.accountNumber(),
                    result.ledgerTransactionId(),
                    now
            )) {
                ReferralRewardRecord latest = referralRewardRepository.findByReferralId(plan.referral().id())
                        .orElseThrow(() -> new KycConflictException("Referral reward was not found"));
                if (latest.status() != ReferralRewardStatus.POSTED) {
                    throw new KycConflictException("Referral reward changed concurrently");
                }
            }
            ReferralStatus nextReferralStatus = nextStatus == ReferralRewardStatus.POSTED
                    ? ReferralStatus.REWARDED
                    : ReferralStatus.REWARD_PENDING;
            referralRepository.updateStatus(
                    plan.referral().id(),
                    ReferralStatus.REWARD_PENDING,
                    nextReferralStatus,
                    now
            );
        });
        return true;
    }

    private void validateCompletion(CompleteKycRequest request) {
        if (request.status() != KycStatus.APPROVED && request.status() != KycStatus.REJECTED) {
            throw validation(
                    "data.status",
                    "status must be APPROVED or REJECTED",
                    "KYC_COMPLETION_STATUS"
            );
        }
        if (request.status() == KycStatus.REJECTED
                && (request.reason() == null || request.reason().isBlank())) {
            throw validation("data.reason", "reason is required when KYC is rejected", "REJECTION_REASON_REQUIRED");
        }
    }

    private UUID targetUserId(VerifiedToken token, String requestedUserId) {
        if (requestedUserId == null || requestedUserId.isBlank()) {
            return token.userId();
        }
        UUID target = parseUuid(requestedUserId, "data.userId", "USER_ID_UUID");
        if (!target.equals(token.userId()) && !token.hasRole("ADMIN") && !token.hasRole("SYSTEM")) {
            throw new KycUnauthorizedException("Unauthorized");
        }
        return target;
    }

    private UUID parseUuid(String value, String field, String code) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw validation(field, field.substring(field.lastIndexOf('.') + 1) + " must be a UUID", code);
        }
    }

    private void saveHistory(
            UUID caseId,
            KycStatus previousStatus,
            KycStatus newStatus,
            String reason,
            UUID actorId,
            Instant createdAt
    ) {
        kycCaseRepository.saveHistory(new KycCaseHistoryRecord(
                UUID.randomUUID(),
                caseId,
                previousStatus,
                newStatus,
                reason,
                actorId,
                createdAt
        ));
    }

    private SetReferralResponse toReferralResponse(ReferralRecord referral) {
        return new SetReferralResponse(
                referral.id().toString(),
                referral.referrerName(),
                referral.status().name()
        );
    }

    private GetKycStatusResponse toStatusResponse(KycCaseRecord kycCase) {
        return new GetKycStatusResponse(
                kycCase.id().toString(),
                kycCase.status(),
                completionStatus(kycCase.status()),
                kycCase.completedAt(),
                kycCase.rejectionReason()
        );
    }

    private AccountCompletionStatus completionStatus(KycStatus status) {
        return switch (status) {
            case NOT_STARTED, IN_PROGRESS, REJECTED -> AccountCompletionStatus.KYC_REQUIRED;
            case PENDING_REVIEW -> AccountCompletionStatus.KYC_PENDING;
            case APPROVED -> AccountCompletionStatus.COMPLETED;
        };
    }

    private String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private KycValidationException validation(String field, String message, String code) {
        return new KycValidationException(List.of(new StatusDetail(field, message, code)));
    }

    private record CompletionPlan(
            KycCaseRecord kycCase,
            ReferralRecord referral,
            ReferralRewardRecord reward
    ) {
    }
}
