package com.redbank.iam.api.v1;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.iam.api.v1.dto.LoginRequest;
import com.redbank.iam.api.v1.dto.LoginResponse;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import com.redbank.iam.api.v1.dto.RegisterResponse;
import com.redbank.iam.config.JwtProperties;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam")
public class IamController {
    private final JwtProperties jwtProperties;
    private final Clock clock;

    @Autowired
    public IamController(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    IamController(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody ApiRequest<RegisterRequest> request
    ) {
        Instant expiresAt = clock.instant().plusSeconds(jwtProperties.accessTokenTtlSeconds());
        RegisterResponse response = new RegisterResponse(
                UUID.randomUUID().toString(),
                "phase1-onboarding-token-placeholder",
                "Bearer",
                expiresAt,
                AccountCompletionStatus.ACCOUNT_REQUIRED,
                "POST /api/v1/accounts/register"
        );

        return ResponseEntity.status(201)
                .body(ApiResponse.created(request.context().requestId(), null, response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody ApiRequest<LoginRequest> request
    ) {
        Instant expiresAt = clock.instant().plusSeconds(jwtProperties.accessTokenTtlSeconds());
        LoginResponse response = new LoginResponse(
                "phase1-access-token-placeholder",
                "Bearer",
                expiresAt,
                AccountCompletionStatus.ACCOUNT_REQUIRED,
                null,
                false,
                null
        );

        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), null, response));
    }
}
