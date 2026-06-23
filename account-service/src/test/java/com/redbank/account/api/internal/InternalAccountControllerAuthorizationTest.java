package com.redbank.account.api.internal;

import com.redbank.account.api.internal.dto.CreateVaultAccountRequest;
import com.redbank.account.api.internal.dto.CreateVaultAccountResponse;
import com.redbank.account.application.AccountService;
import com.redbank.account.application.AccountUnauthorizedException;
import com.redbank.account.config.InternalAccessProperties;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.AccountType;
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

    private final AccountService accountService = mock(AccountService.class);
    private final InternalAccountController controller = new InternalAccountController(
            accountService,
            new InternalAccessProperties(GENERAL_INTERNAL_TOKEN, VAULT_CREATE_TOKEN)
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
