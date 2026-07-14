package com.redbank.iam.api.v1;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.ResponseContext;
import com.redbank.contracts.envelope.Status;
import com.redbank.iam.api.v1.dto.DeviceVerifyRequest;
import com.redbank.iam.api.v1.dto.DeviceVerifyResponse;
import com.redbank.iam.api.v1.dto.LoginRequest;
import com.redbank.iam.api.v1.dto.LoginResponse;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import com.redbank.iam.api.v1.dto.RegisterResponse;
import com.redbank.iam.application.IamService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam")
public class IamController {
    private final IamService iamService;

    public IamController(IamService iamService) {
        this.iamService = iamService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody ApiRequest<RegisterRequest> request
    ) {
        RegisterResponse response = iamService.register(request.data());

        return ResponseEntity.status(201)
                .body(ApiResponse.created(request.context().requestId(), null, response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody ApiRequest<LoginRequest> request
    ) {
        LoginResponse response = iamService.login(request.context(), request.data());
        Status status = Boolean.TRUE.equals(response.deviceVerificationRequired())
                ? Status.deviceVerificationRequired()
                : Status.success();

        return ResponseEntity.status(status.httpStatus())
                .body(new ApiResponse<>(new ResponseContext(request.context().requestId(), null, status), response));
    }
    @PostMapping("/logout")
    public void logout(@RequestHeader(name = "Authorization", required = false) String  authorizationHeader){
        iamService.logout(authorizationHeader);
    }

    @PostMapping("/device-verify")
    public ResponseEntity<ApiResponse<DeviceVerifyResponse>> verifyDevice(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<DeviceVerifyRequest> request
    ) {
        DeviceVerifyResponse response = iamService.verifyDevice(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), null, response));
    }
}
