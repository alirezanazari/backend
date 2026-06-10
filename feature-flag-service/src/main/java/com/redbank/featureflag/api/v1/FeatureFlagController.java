package com.redbank.featureflag.api.v1;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.featureflag.api.v1.dto.ListFeatureFlagsRequest;
import com.redbank.featureflag.api.v1.dto.ListFeatureFlagsResponse;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagRequest;
import com.redbank.featureflag.api.v1.dto.UpdateFeatureFlagResponse;
import com.redbank.featureflag.application.FeatureFlagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feature-flags")
public class FeatureFlagController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @PostMapping("/list")
    public ResponseEntity<ApiResponse<ListFeatureFlagsResponse>> list(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String internalServiceToken,
            @Valid @RequestBody ApiRequest<ListFeatureFlagsRequest> request
    ) {
        ListFeatureFlagsResponse response = featureFlagService.list(
                authorizationHeader,
                internalServiceToken,
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), null, response));
    }

    @PostMapping("/update")
    public ResponseEntity<ApiResponse<UpdateFeatureFlagResponse>> update(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<UpdateFeatureFlagRequest> request
    ) {
        UpdateFeatureFlagResponse response = featureFlagService.update(
                authorizationHeader,
                request.context().requestId(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), null, response));
    }
}
