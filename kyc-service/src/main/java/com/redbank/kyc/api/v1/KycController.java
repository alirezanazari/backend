package com.redbank.kyc.api.v1;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.kyc.api.v1.dto.CompleteKycRequest;
import com.redbank.kyc.api.v1.dto.CompleteKycResponse;
import com.redbank.kyc.api.v1.dto.GetKycStatusRequest;
import com.redbank.kyc.api.v1.dto.GetKycStatusResponse;
import com.redbank.kyc.api.v1.dto.ListJobsRequest;
import com.redbank.kyc.api.v1.dto.ListJobsResponse;
import com.redbank.kyc.api.v1.dto.SetPhoneRequest;
import com.redbank.kyc.api.v1.dto.SetPhoneResponse;
import com.redbank.kyc.api.v1.dto.SetReferralRequest;
import com.redbank.kyc.api.v1.dto.SetReferralResponse;
import com.redbank.kyc.application.KycService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycController {
    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping("/jobs/list")
    public ResponseEntity<ApiResponse<ListJobsResponse>> listJobs(
            @Valid @RequestBody ApiRequest<ListJobsRequest> request
    ) {
        ListJobsResponse response = kycService.listJobs(request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), null, response));
    }

    @PostMapping("/phone/set")
    public ResponseEntity<ApiResponse<SetPhoneResponse>> setPhone(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<SetPhoneRequest> request
    ) {
        SetPhoneResponse response = kycService.setPhone(authorizationHeader, request.data());
        return ResponseEntity.status(201).body(ApiResponse.created(
                request.context().requestId(),
                null,
                response
        ));
    }

    @PostMapping("/referral/set")
    public ResponseEntity<ApiResponse<SetReferralResponse>> setReferral(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<SetReferralRequest> request
    ) {
        SetReferralResponse response = kycService.setReferral(authorizationHeader, request.data());
        return ResponseEntity.status(201).body(ApiResponse.created(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    @PostMapping("/status/get")
    public ResponseEntity<ApiResponse<GetKycStatusResponse>> getStatus(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<GetKycStatusRequest> request
    ) {
        GetKycStatusResponse response = kycService.getStatus(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<CompleteKycResponse>> complete(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<CompleteKycRequest> request
    ) {
        CompleteKycResponse response = kycService.complete(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    private String profileId(ApiRequest<?> request) {
        return request.context().account() == null ? null : request.context().account().profileId();
    }
}
