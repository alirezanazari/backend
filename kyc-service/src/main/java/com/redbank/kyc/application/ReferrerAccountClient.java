package com.redbank.kyc.application;

import java.util.UUID;

public interface ReferrerAccountClient {
    ResolvedReferrer resolveByCode(String referralCode);

    ActiveReferrerAccount findActiveAccount(UUID referrerUserId);
}
