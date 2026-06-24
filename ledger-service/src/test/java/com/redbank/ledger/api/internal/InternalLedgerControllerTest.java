package com.redbank.ledger.api.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redbank.contracts.common.Money;
import com.redbank.ledger.api.internal.dto.InternalTransferRequest;
import com.redbank.ledger.api.internal.dto.LedgerTransactionResponse;
import com.redbank.ledger.api.internal.dto.ReferralRewardRequest;
import com.redbank.ledger.application.InboxEventProcessor;
import com.redbank.ledger.application.LedgerService;
import com.redbank.ledger.application.LedgerUnauthorizedException;
import com.redbank.ledger.config.InternalAccessProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InternalLedgerControllerTest {
    private static final String GENERAL_TOKEN = "general-service-token";
    private static final String INTERNAL_TRANSFER_TOKEN = "internal-transfer-token";
    private static final String REFERRAL_REWARD_TOKEN = "referral-reward-token";

    private final LedgerService ledgerService = mock(LedgerService.class);
    private final InboxEventProcessor inboxEventProcessor = mock(InboxEventProcessor.class);
    private final InternalLedgerController controller = new InternalLedgerController(
            ledgerService,
            inboxEventProcessor,
            new InternalAccessProperties(GENERAL_TOKEN, INTERNAL_TRANSFER_TOKEN, REFERRAL_REWARD_TOKEN)
    );

    @Test
    void internalTransferRejectsGeneralServiceToken() {
        assertThatThrownBy(() -> controller.internalTransfer(GENERAL_TOKEN, internalTransferRequest()))
                .isInstanceOf(LedgerUnauthorizedException.class);

        verifyNoInteractions(ledgerService);
    }

    @Test
    void internalTransferAcceptsDedicatedInternalTransferToken() {
        InternalTransferRequest request = internalTransferRequest();
        when(ledgerService.postInternalTransfer(request))
                .thenReturn(new LedgerTransactionResponse("ledger-456", "POSTED"));

        var response = controller.internalTransfer(INTERNAL_TRANSFER_TOKEN, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().ledgerTransactionId()).isEqualTo("ledger-456");
        verify(ledgerService).postInternalTransfer(request);
    }

    @Test
    void referralRewardRejectsGeneralServiceToken() {
        assertThatThrownBy(() -> controller.referralReward(GENERAL_TOKEN, referralRewardRequest()))
                .isInstanceOf(LedgerUnauthorizedException.class);

        verifyNoInteractions(ledgerService);
    }

    @Test
    void referralRewardAcceptsDedicatedReferralRewardToken() {
        ReferralRewardRequest request = referralRewardRequest();
        when(ledgerService.postReferralReward(request))
                .thenReturn(new LedgerTransactionResponse("ledger-123", "POSTED"));

        var response = controller.referralReward(REFERRAL_REWARD_TOKEN, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().ledgerTransactionId()).isEqualTo("ledger-123");
        verify(ledgerService).postReferralReward(request);
    }

    private ReferralRewardRequest referralRewardRequest() {
        return new ReferralRewardRequest(
                "reward-123",
                "123456789012345678",
                new Money(new BigDecimal("500000"), "IRR")
        );
    }

    private InternalTransferRequest internalTransferRequest() {
        return new InternalTransferRequest(
                "transfer-123",
                "111111111111111111",
                "222222222222222222",
                new Money(new BigDecimal("500000"), "IRR"),
                "idem-123"
        );
    }
}
