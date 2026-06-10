package com.redbank.transfer.api.v1;

import com.redbank.contracts.envelope.ApiRequest;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.transfer.api.v1.dto.CreateTransferRequest;
import com.redbank.transfer.api.v1.dto.CreateTransferResponse;
import com.redbank.transfer.api.v1.dto.ListRecentTransfersRequest;
import com.redbank.transfer.api.v1.dto.ListRecentTransfersResponse;
import com.redbank.transfer.api.v1.dto.ListTransferMethodsRequest;
import com.redbank.transfer.api.v1.dto.ListTransferMethodsResponse;
import com.redbank.transfer.application.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/methods/list")
    public ResponseEntity<ApiResponse<ListTransferMethodsResponse>> listMethods(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListTransferMethodsRequest> request
    ) {
        ListTransferMethodsResponse response = transferService.listMethods(
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

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<CreateTransferResponse>> create(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ApiRequest<CreateTransferRequest> request
    ) {
        CreateTransferResponse response = transferService.create(
                authorizationHeader,
                idempotencyKey,
                request.context().requestId(),
                request.context(),
                request.data()
        );
        return ResponseEntity.ok(ApiResponse.success(
                request.context().requestId(),
                profileId(request),
                response
        ));
    }

    @PostMapping("/recent/list")
    public ResponseEntity<ApiResponse<ListRecentTransfersResponse>> listRecent(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListRecentTransfersRequest> request
    ) {
        ListRecentTransfersResponse response = transferService.listRecent(
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
