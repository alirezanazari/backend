package com.redbank.account.api.internal;

import com.redbank.account.api.internal.dto.CreateVaultAccountRequest;
import com.redbank.account.api.internal.dto.CreateVaultAccountResponse;
import com.redbank.account.api.internal.dto.GetAccountCompletionRequest;
import com.redbank.account.api.internal.dto.GetAccountCompletionResponse;
import com.redbank.account.api.internal.dto.GetCardAccountRequest;
import com.redbank.account.api.internal.dto.GetCardAccountResponse;
import com.redbank.account.api.internal.dto.GetFeatureEvaluationContextRequest;
import com.redbank.account.api.internal.dto.GetFeatureEvaluationContextResponse;
import com.redbank.account.api.internal.dto.GetReferralRewardAccountRequest;
import com.redbank.account.api.internal.dto.GetReferralRewardAccountResponse;
import com.redbank.account.api.internal.dto.ResolveTransferAccountRequest;
import com.redbank.account.api.internal.dto.ResolveTransferAccountResponse;
import com.redbank.account.application.AccountService;
import com.redbank.account.application.AccountUnauthorizedException;
import com.redbank.account.config.InternalAccessProperties;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.identity.AccountCompletionStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAccountControllerAuthorizationTest {
    private static final String GENERAL_INTERNAL_TOKEN = "general-internal-token";
    private static final String VAULT_CREATE_TOKEN = "vault-create-token";
    private static final String COMPLETION_GET_TOKEN = "completion-get-token";
    private static final String CARD_ELIGIBILITY_GET_TOKEN = "card-eligibility-get-token";
    private static final String REFERRAL_REWARD_ACCOUNT_GET_TOKEN = "referral-reward-account-get-token";
    private static final String FEATURE_EVALUATION_CONTEXT_GET_TOKEN = "feature-evaluation-context-get-token";
    private static final String TRANSFER_RESOLVE_TOKEN = "transfer-resolve-token";

    private final AccountService accountService = mock(AccountService.class);
    private final InternalAccountController controller = new InternalAccountController(
            accountService,
            new InternalAccessProperties(
                    GENERAL_INTERNAL_TOKEN,
                    VAULT_CREATE_TOKEN,
                    COMPLETION_GET_TOKEN,
                    CARD_ELIGIBILITY_GET_TOKEN,
                    REFERRAL_REWARD_ACCOUNT_GET_TOKEN,
                    FEATURE_EVALUATION_CONTEXT_GET_TOKEN,
                    TRANSFER_RESOLVE_TOKEN
            )
    );

    @Test
    void vaultCreateRejectsGeneralInternalToken() {
        CreateVaultAccountRequest request = request();

        assertThatThrownBy(() -> controller.createVaultAccount(GENERAL_INTERNAL_TOKEN, request))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
        verify(accountService, never()).createVaultAccount(any());
    }

    @Test
    void vaultCreateAllowsVaultScopedToken() {
        CreateVaultAccountRequest request = request();
        when(accountService.createVaultAccount(request)).thenReturn(new CreateVaultAccountResponse(account()));

        assertThatCode(() -> controller.createVaultAccount(VAULT_CREATE_TOKEN, request))
                .doesNotThrowAnyException();
        verify(accountService).createVaultAccount(request);
    }

    @Test
    void vaultCreateRejectsMissingToken() {
        assertThatThrownBy(() -> controller.createVaultAccount(null, request()))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
    }

    @Test
    void completionGetRejectsGeneralInternalTokenAndAllowsScopedToken() {
        GetAccountCompletionRequest request = new GetAccountCompletionRequest(UUID.randomUUID().toString());
        when(accountService.getCompletion(request)).thenReturn(new GetAccountCompletionResponse(
                AccountCompletionStatus.COMPLETED,
                new ProfileSummary(UUID.randomUUID().toString(), ProfileType.PERSONAL, "Personal", null, true)
        ));

        assertThatThrownBy(() -> controller.getCompletion(GENERAL_INTERNAL_TOKEN, request))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
        assertThatCode(() -> controller.getCompletion(COMPLETION_GET_TOKEN, request))
                .doesNotThrowAnyException();
        verify(accountService).getCompletion(request);
    }

    @Test
    void cardEligibilityGetRejectsGeneralInternalTokenAndAllowsScopedToken() {
        GetCardAccountRequest request = new GetCardAccountRequest(
                "123456789012345678",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );
        when(accountService.getCardAccount(request)).thenReturn(new GetCardAccountResponse(
                request.accountNumber(),
                request.userId(),
                request.profileId(),
                AccountType.SAVING,
                AccountStatus.ACTIVE,
                "IRR",
                true
        ));

        assertThatThrownBy(() -> controller.getCardAccount(GENERAL_INTERNAL_TOKEN, request))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
        assertThatCode(() -> controller.getCardAccount(CARD_ELIGIBILITY_GET_TOKEN, request))
                .doesNotThrowAnyException();
        verify(accountService).getCardAccount(request);
    }

    @Test
    void referralRewardAccountGetRejectsGeneralInternalTokenAndAllowsScopedToken() {
        GetReferralRewardAccountRequest request = new GetReferralRewardAccountRequest(
                UUID.randomUUID().toString(),
                "IRR"
        );
        when(accountService.getReferralRewardAccount(request)).thenReturn(new GetReferralRewardAccountResponse(
                request.userId(),
                "123456789012345678",
                request.currency()
        ));

        assertThatThrownBy(() -> controller.getReferralRewardAccount(GENERAL_INTERNAL_TOKEN, request))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
        assertThatCode(() -> controller.getReferralRewardAccount(REFERRAL_REWARD_ACCOUNT_GET_TOKEN, request))
                .doesNotThrowAnyException();
        verify(accountService).getReferralRewardAccount(request);
    }

    @Test
    void featureEvaluationContextGetRejectsGeneralInternalTokenAndAllowsScopedToken() {
        GetFeatureEvaluationContextRequest request = new GetFeatureEvaluationContextRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "123456789012345678"
        );
        when(accountService.getFeatureEvaluationContext(request)).thenReturn(new GetFeatureEvaluationContextResponse(
                request.userId(),
                request.profileId(),
                ProfileType.PERSONAL,
                "job-engineer",
                request.accountNumber(),
                AccountType.SAVING,
                AccountStatus.ACTIVE,
                "IRR",
                BigDecimal.TEN,
                BigDecimal.TEN
        ));

        assertThatThrownBy(() -> controller.getFeatureEvaluationContext(GENERAL_INTERNAL_TOKEN, request))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
        assertThatCode(() -> controller.getFeatureEvaluationContext(FEATURE_EVALUATION_CONTEXT_GET_TOKEN, request))
                .doesNotThrowAnyException();
        verify(accountService).getFeatureEvaluationContext(request);
    }

    @Test
    void transferResolveRejectsGeneralInternalTokenAndAllowsScopedToken() {
        ResolveTransferAccountRequest request = new ResolveTransferAccountRequest(
                "123456789012345678",
                null,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                true
        );
        when(accountService.resolveTransferAccount(request)).thenReturn(new ResolveTransferAccountResponse(
                UUID.randomUUID().toString(),
                request.userId(),
                request.profileId(),
                request.accountNumber(),
                "IR001234567890123456789012",
                "IRR",
                "Transfer Owner",
                null
        ));

        assertThatThrownBy(() -> controller.resolveTransferAccount(GENERAL_INTERNAL_TOKEN, request))
                .isInstanceOf(AccountUnauthorizedException.class)
                .hasMessage("Unauthorized");
        assertThatCode(() -> controller.resolveTransferAccount(TRANSFER_RESOLVE_TOKEN, request))
                .doesNotThrowAnyException();
        verify(accountService).resolveTransferAccount(request);
    }

    private CreateVaultAccountRequest request() {
        return new CreateVaultAccountRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "IRR");
    }

    private AccountSummary account() {
        return new AccountSummary(
                UUID.randomUUID().toString(),
                "123456789012345678",
                "IR001234567890123456789012",
                AccountType.VAULT,
                AccountStatus.ACTIVE,
                "IRR",
                null
        );
    }
}
