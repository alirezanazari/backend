package com.redbank.iam.application;

import java.util.UUID;

public interface AccountCompletionClient {
    AccountCompletionResult getCompletion(UUID userId);
}
