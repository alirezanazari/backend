package com.redbank.iam.api.v1.dto;

import com.redbank.contracts.identity.LoginReason;

public record LoginRequest(
        String username,
        String password,
        LoginReason reason
) {
}
