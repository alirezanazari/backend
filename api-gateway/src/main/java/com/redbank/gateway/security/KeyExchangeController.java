package com.redbank.gateway.security;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security")
@ConditionalOnProperty(name = "redbank.gateway.app-encryption.enabled", havingValue = "true")
public class KeyExchangeController {
    private final KeyExchangeService keyExchangeService;

    public KeyExchangeController(KeyExchangeService keyExchangeService) {
        this.keyExchangeService = keyExchangeService;
    }

    @PostMapping("/key-exchange")
    ResponseEntity<ApiResponse<KeyExchangeResponse>> exchange(
            @Valid @RequestBody ApiRequest<KeyExchangeRequest> request
    ) {
        KeyExchangeResponse response = keyExchangeService.create(request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), null, response));
    }
}
