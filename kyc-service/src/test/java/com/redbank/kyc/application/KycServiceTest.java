package com.redbank.kyc.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redbank.contracts.kyc.KycStatus;
import com.redbank.kyc.api.v1.dto.SetReferralRequest;
import com.redbank.kyc.config.KycProperties;
import com.redbank.kyc.domain.KycCaseRecord;
import com.redbank.kyc.domain.ReferralCodeRecord;
import com.redbank.kyc.domain.ReferralRecord;
import com.redbank.kyc.infrastructure.JobRepository;
import com.redbank.kyc.infrastructure.KycCaseRepository;
import com.redbank.kyc.infrastructure.ReferralCodeRepository;
import com.redbank.kyc.infrastructure.ReferralRepository;
import com.redbank.kyc.infrastructure.ReferralRewardRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class KycServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-24T09:00:00Z");
    private static final String REFERRAL_SCOPE = "ONBOARDING_REFERRAL_SET";

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final KycCaseRepository kycCaseRepository = mock(KycCaseRepository.class);
    private final ReferralRepository referralRepository = mock(ReferralRepository.class);
    private final ReferralRewardRepository referralRewardRepository = mock(ReferralRewardRepository.class);
    private final ReferralCodeRepository referralCodeRepository = mock(ReferralCodeRepository.class);
    private final ReferralCodeGenerator referralCodeGenerator = mock(ReferralCodeGenerator.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final ReferrerAccountClient referrerAccountClient = mock(ReferrerAccountClient.class);
    private final ReferralRewardClient referralRewardClient = mock(ReferralRewardClient.class);
    private final FeatureFlagClient featureFlagClient = mock(FeatureFlagClient.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

    @Test
    void setReferralRejectsApprovedKycEvenWithOnboardingReferralScope() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenService.verifyReferralToken("Bearer onboarding"))
                .thenReturn(token(userId));
        when(referralRepository.findByReferredUserId(userId)).thenReturn(Optional.empty());
        when(kycCaseRepository.findByUserId(userId)).thenReturn(Optional.of(kycCase(userId, KycStatus.APPROVED)));

        assertThatThrownBy(() -> service().setReferral("Bearer onboarding", new SetReferralRequest("ALICE_2026")))
                .isInstanceOf(KycBusinessRuleException.class)
                .hasMessage("Referral can only be set before KYC completion");

        verify(referralCodeRepository, never()).findByCode(any());
        verify(referralRepository, never()).save(any());
    }

    @Test
    void setReferralAllowsScopedOnboardingTokenBeforeKycCompletion() {
        UUID userId = UUID.randomUUID();
        UUID referrerUserId = UUID.randomUUID();
        when(jwtTokenService.verifyReferralToken("Bearer onboarding"))
                .thenReturn(token(userId));
        when(referralRepository.findByReferredUserId(userId)).thenReturn(Optional.empty());
        when(kycCaseRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(referralCodeRepository.findByCode("ALICE_2026"))
                .thenReturn(Optional.of(new ReferralCodeRecord(
                        UUID.randomUUID(),
                        referrerUserId,
                        "ALICE_2026",
                        "Alice",
                        NOW
                )));

        var response = service().setReferral("Bearer onboarding", new SetReferralRequest("alice_2026"));

        assertThat(response.referrerName()).isEqualTo("Alice");
        assertThat(response.status()).isEqualTo("PENDING_KYC");
        verify(referralRepository).save(any(ReferralRecord.class));
    }

    private KycService service() {
        return new KycService(
                jobRepository,
                kycCaseRepository,
                referralRepository,
                referralRewardRepository,
                referralCodeRepository,
                referralCodeGenerator,
                jwtTokenService,
                referrerAccountClient,
                referralRewardClient,
                featureFlagClient,
                new KycProperties(new BigDecimal("500000"), "IRR", true),
                transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private VerifiedToken token(UUID userId) {
        return new VerifiedToken(
                userId,
                UUID.randomUUID(),
                null,
                List.of("ONBOARDING"),
                List.of(REFERRAL_SCOPE)
        );
    }

    private KycCaseRecord kycCase(UUID userId, KycStatus status) {
        return new KycCaseRecord(
                UUID.randomUUID(),
                userId,
                status,
                null,
                NOW,
                NOW,
                NOW
        );
    }
}
