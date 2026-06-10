package com.redbank.transfer.infrastructure.ledger;

import com.redbank.contracts.common.Money;
import com.redbank.transfer.application.LedgerTransferClient;
import com.redbank.transfer.application.LedgerTransferResult;
import com.redbank.transfer.config.LedgerClientProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
        prefix = "redbank.ledger-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpLedgerTransferClient implements LedgerTransferClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpLedgerTransferClient(LedgerClientProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public LedgerTransferResult post(
            String transferId,
            String sourceAccountNumber,
            String destinationAccountNumber,
            Money amount,
            String idempotencyKey
    ) {
        // TODO
        throw new UnsupportedOperationException("Implement ledger internal transfer posting");
    }
}
