package com.redbank.account.api.v1;

import com.redbank.account.api.v1.dto.CloseAccountRequest;
import com.redbank.account.api.v1.dto.CloseAccountResponse;
import com.redbank.account.api.v1.dto.CreateProfileRequest;
import com.redbank.account.api.v1.dto.CreateProfileResponse;
import com.redbank.account.api.v1.dto.EditProfileRequest;
import com.redbank.account.api.v1.dto.EditProfileResponse;
import com.redbank.account.api.v1.dto.GetAccountBalanceRequest;
import com.redbank.account.api.v1.dto.GetAccountBalanceResponse;
import com.redbank.account.api.v1.dto.GetAccountOwnerRequest;
import com.redbank.account.api.v1.dto.GetAccountOwnerResponse;
import com.redbank.account.api.v1.dto.ListAccountsRequest;
import com.redbank.account.api.v1.dto.ListAccountsResponse;
import com.redbank.account.api.v1.dto.ListProfilesRequest;
import com.redbank.account.api.v1.dto.ListProfilesResponse;
import com.redbank.account.api.v1.dto.OpenAccountRequest;
import com.redbank.account.api.v1.dto.OpenAccountResponse;
import com.redbank.account.api.v1.dto.RegisterAccountRequest;
import com.redbank.account.api.v1.dto.RegisterAccountResponse;
import com.redbank.account.api.v1.dto.SetActiveProfileRequest;
import com.redbank.account.api.v1.dto.SetActiveProfileResponse;
import com.redbank.account.application.AccountService;
import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/v1/accounts/register")
    public ResponseEntity<ApiResponse<RegisterAccountResponse>> register(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<RegisterAccountRequest> request
    ) {
        RegisterAccountResponse response = accountService.register(authorizationHeader, request.data());
        return ResponseEntity.status(201)
                .body(ApiResponse.created(request.context().requestId(), response.profile().profileId(), response));
    }

    @PostMapping("/api/v1/accounts/list")
    public ResponseEntity<ApiResponse<ListAccountsResponse>> listAccounts(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListAccountsRequest> request
    ) {
        ListAccountsResponse response = accountService.listAccounts(authorizationHeader, request.context(), request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), profileId(request), response));
    }

    @PostMapping("/api/v1/accounts/balance/get")
    public ResponseEntity<ApiResponse<GetAccountBalanceResponse>> getBalance(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<GetAccountBalanceRequest> request
    ) {
        GetAccountBalanceResponse response = accountService.getBalance(authorizationHeader, request.context(), request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), profileId(request), response));
    }

    @PostMapping("/api/v1/accounts/close")
    public ResponseEntity<ApiResponse<CloseAccountResponse>> closeAccount(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<CloseAccountRequest> request
    ) {
        CloseAccountResponse response = accountService.closeAccount(authorizationHeader, request.context(), request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), profileId(request), response));
    }

    @PostMapping({"/api/v1/accounts/open", "/api/v1/account/open"})
    public ResponseEntity<ApiResponse<OpenAccountResponse>> openAccount(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<OpenAccountRequest> request
    ) {
        OpenAccountResponse response = accountService.openAccount(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), response.profile().profileId(), response));
    }

    @PostMapping("/api/v1/accounts/owner/get")
    public ResponseEntity<ApiResponse<GetAccountOwnerResponse>> getOwner(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<GetAccountOwnerRequest> request
    ) {
        GetAccountOwnerResponse response = accountService.getOwner(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), profileId(request), response));
    }

    @PostMapping("/api/v1/profiles/create")
    public ResponseEntity<ApiResponse<CreateProfileResponse>> createProfile(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<CreateProfileRequest> request
    ) {
        CreateProfileResponse response = accountService.createProfile(authorizationHeader, request.data());
        return ResponseEntity.status(201)
                .body(ApiResponse.created(request.context().requestId(), profileId(request), response));
    }

    @PostMapping("/api/v1/profiles/list")
    public ResponseEntity<ApiResponse<ListProfilesResponse>> listProfiles(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListProfilesRequest> request
    ) {
        ListProfilesResponse response = accountService.listProfiles(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(request.context().requestId(), response.activeProfileId(), response));
    }

    @PostMapping("/api/v1/profiles/active/set")
    public ResponseEntity<ApiResponse<SetActiveProfileResponse>> setActiveProfile(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<SetActiveProfileRequest> request
    ) {
        SetActiveProfileResponse response = accountService.setActiveProfile(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                response.activeProfile().profileId(),
                response
        ));
    }

    @PostMapping("/api/v1/profiles/edit")
    public ResponseEntity<ApiResponse<EditProfileResponse>> editProfile(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<EditProfileRequest> request
    ) {
        EditProfileResponse response = accountService.editProfile(authorizationHeader, request.data());
        return ResponseEntity.ok(ApiResponse.success(
            request.context().requestId(), 
            request.data().profileId(), 
            response
        ));
    }

    private String profileId(ApiRequest<?> request) {
        return request.context().account() == null ? null : request.context().account().profileId();
    }
}
