package com.redbank.account.infrastructure.ledger;

import com.redbank.account.application.LedgerAccountClient;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "redbank.ledger", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopLedgerAccountClient implements LedgerAccountClient {
    @Override
    public boolean createAccount(String accountNumber, UUID ownerUserId, String currency) {
        return false;
    }
}
