package com.redbank.kyc.application;

import java.util.UUID;

public interface ReferrerAccountClient {
    ActiveReferrerAccount findActiveAccount(UUID referrerUserId);
}
