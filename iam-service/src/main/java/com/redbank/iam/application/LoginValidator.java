package com.redbank.iam.application;

import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.iam.api.v1.dto.LoginRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LoginValidator {
    public void validate(LoginRequest request) {
        if (request.reason() != null) {
            return;
        }

        List<StatusDetail> details = new ArrayList<>();
        if (isBlank(request.username())) {
            details.add(new StatusDetail("data.username", "username is required", "USERNAME_REQUIRED"));
        } else if (request.username().length() < 3 || request.username().length() > 80) {
            details.add(new StatusDetail("data.username", "username must be 3-80 characters", "USERNAME_LENGTH"));
        }
        if (isBlank(request.password())) {
            details.add(new StatusDetail("data.password", "password is required", "PASSWORD_REQUIRED"));
        } else if (request.password().length() < 8 || request.password().length() > 200) {
            details.add(new StatusDetail("data.password", "password must be 8-200 characters", "PASSWORD_LENGTH"));
        }

        if (!details.isEmpty()) {
            throw new IamValidationException(details);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
