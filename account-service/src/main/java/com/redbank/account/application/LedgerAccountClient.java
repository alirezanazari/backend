package com.redbank.account.application;

import java.util.UUID;

public interface LedgerAccountClient {
    boolean createAccount(String accountNumber, UUID ownerUserId, String currency);
    boolean close(String accountNumber);
}
