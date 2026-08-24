package com.redbank.pfm.api.v1;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.pfm.api.v1.dto.*;
import com.redbank.pfm.application.PfmService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pfm")
public class PfmController {
    private final PfmService pfmService;

    public PfmController(PfmService pfmService) {
        this.pfmService = pfmService;
    }

    @PostMapping("/box-presets/list")
    public ResponseEntity<ApiResponse<ListBoxPresetsResponse>> listPresets(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListBoxPresetsRequest> request
    ) {
        ListBoxPresetsResponse response = pfmService.listPresets(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    @PostMapping("/boxes/create")
    public ResponseEntity<ApiResponse<CreateBoxResponse>> createBox(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<CreateBoxRequest> request
    ) {
        CreateBoxResponse response = pfmService.createBox(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return ResponseEntity.status(201).body(ApiResponse.created(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    @PostMapping("/boxes/list")
    public ResponseEntity<ApiResponse<ListBoxesResponse>> listBoxes(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListBoxesRequest> request
    ) {
        ListBoxesResponse response = pfmService.listBoxes(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    @PostMapping("/boxes/edit")
    public ResponseEntity<ApiResponse<EditBoxResponse>> editBox(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<EditBoxRequest> request
    ) {
        EditBoxResponse response = pfmService.editBox(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }
    @PostMapping("/boxes/close")
    public ResponseEntity<ApiResponse<CloseBoxResponse>> closeBox(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<CloseBoxRequest> request
    ) {
        CloseBoxResponse response = pfmService.closeBox(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }
    @PostMapping("/addFundsToBox")
    public ResponseEntity<ApiResponse<BoxTransferResponse>> addFundsToBox(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<BoxTransferRequest> request
    ){
        BoxTransferResponse response = pfmService.addFundsToBox(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));

    }

    @PostMapping("/transactions/list")
    public ResponseEntity<ApiResponse<ListPfmTransactionsResponse>> listTransactions(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListPfmTransactionsRequest> request
    ) {
        ListPfmTransactionsResponse response = pfmService.listTransactions(
                authorizationHeader,
                request.context(),
                request.data()
        );
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
