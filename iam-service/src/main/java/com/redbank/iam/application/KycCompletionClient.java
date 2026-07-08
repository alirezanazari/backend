package com.redbank.iam.application;

import com.redbank.contracts.identity.AccountCompletionStatus;
import java.util.UUID;

public interface KycCompletionClient {
    /**
     * Resolves the KYC-derived account completion status for a user. Used to refine the
     * account-service completion status (which tops out at KYC_REQUIRED) into KYC_PENDING or
     * COMPLETED once the user has an account.
     */
    AccountCompletionStatus getCompletionStatus(UUID userId);
}
