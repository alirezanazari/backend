package com.redbank.gateway.debug;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("develop")
@RequestMapping("/debug/v1/tokens")
@ConditionalOnProperty(name = "redbank.debug.enabled", havingValue = "true")
public class DebugTokenController {
    private final DebugTokenService debugTokenService;

    public DebugTokenController(DebugTokenService debugTokenService) {
        this.debugTokenService = debugTokenService;
    }

    @PostMapping("/user")
    ResponseEntity<ApiResponse<DebugTokenResponse>> user(@Valid @RequestBody ApiRequest<DebugTokenRequest> request) {
        DebugTokenResponse response = debugTokenService.createUserToken(request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), request.data().profileId(), response));
    }

    @PostMapping("/admin")
    ResponseEntity<ApiResponse<DebugTokenResponse>> admin(@Valid @RequestBody ApiRequest<DebugTokenRequest> request) {
        DebugTokenResponse response = debugTokenService.createAdminToken(request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), request.data().profileId(), response));
    }

    @PostMapping("/service")
    ResponseEntity<ApiResponse<DebugTokenResponse>> service(@Valid @RequestBody ApiRequest<DebugTokenRequest> request) {
        DebugTokenResponse response = debugTokenService.createServiceToken(request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), request.data().profileId(), response));
    }
}
