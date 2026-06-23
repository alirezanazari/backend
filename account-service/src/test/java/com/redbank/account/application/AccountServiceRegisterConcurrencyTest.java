package com.redbank.account.application;

import com.redbank.account.api.v1.dto.RegisterAccountRequest;
import com.redbank.account.config.AccountProperties;
import com.redbank.account.infrastructure.BankAccountRepository;
import com.redbank.account.infrastructure.ProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceRegisterConcurrencyTest {
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final BankAccountRepository bankAccountRepository = mock(BankAccountRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final KycJobCatalog kycJobCatalog = mock(KycJobCatalog.class);
    private final AccountNumberGenerator accountNumberGenerator = mock(AccountNumberGenerator.class);
    private final IbanGenerator ibanGenerator = mock(IbanGenerator.class);
    private final AccountOutboxService accountOutboxService = mock(AccountOutboxService.class);
    private final LedgerBalanceClient ledgerBalanceClient = mock(LedgerBalanceClient.class);
    private final AccountService accountService = new AccountService(
            profileRepository,
            bankAccountRepository,
            jwtTokenService,
            kycJobCatalog,
            accountNumberGenerator,
            ibanGenerator,
            accountOutboxService,
            ledgerBalanceClient,
            new AccountProperties("IR", "0620", "IRR", 5, List.of("job-engineer")),
            Clock.fixed(Instant.parse("2026-06-23T08:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void registerStopsWhenDatabaseRejectsSecondPersonalProfile() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenService.verifyAccountRegistrationToken("Bearer onboarding-token"))
                .thenReturn(new VerifiedToken(userId, null, null, List.of("ONBOARDING"), List.of("ACCOUNT_REGISTER")));
        when(kycJobCatalog.exists("job-engineer")).thenReturn(true);
        when(profileRepository.countByUserId(userId)).thenReturn(0);
        when(accountNumberGenerator.generate()).thenReturn("123456789012345678");
        when(ibanGenerator.generate("123456789012345678")).thenReturn("IR001234567890123456789012");
        doThrow(new DataIntegrityViolationException("uk_profiles_single_personal_per_user"))
                .when(profileRepository)
                .save(any());

        assertThatThrownBy(() -> accountService.register(
                "Bearer onboarding-token",
                new RegisterAccountRequest("job-engineer", "Personal", null, "IRR")
        ))
                .isInstanceOf(AccountConflictException.class)
                .hasMessage("Account foundation already exists for this user");

        verify(bankAccountRepository, never()).save(any());
        verify(accountOutboxService, never()).enqueueLedgerAccountCreation(any());
        verify(accountOutboxService, never()).enqueueDefaultCardCreation(any(), any());
    }
}
