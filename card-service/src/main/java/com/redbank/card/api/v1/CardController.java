package com.redbank.card.api.v1;

import com.redbank.card.api.v1.dto.BlockCardRequest;
import com.redbank.card.api.v1.dto.BlockCardResponse;
import com.redbank.card.api.v1.dto.CardStatusResponse;
import com.redbank.card.api.v1.dto.ExpireCardRequest;
import com.redbank.card.api.v1.dto.FreezeCardRequest;
import com.redbank.card.api.v1.dto.ListCardsRequest;
import com.redbank.card.api.v1.dto.ListCardsResponse;
import com.redbank.card.api.v1.dto.OrderCardRequest;
import com.redbank.card.api.v1.dto.OrderCardResponse;
import com.redbank.card.api.v1.dto.UnblockCardRequest;
import com.redbank.card.api.v1.dto.UnfreezeCardRequest;
import com.redbank.card.application.CardService;
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
@RequestMapping("/api/v1/card")
public class CardController {
    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping("/list")
    public ResponseEntity<ApiResponse<ListCardsResponse>> list(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ListCardsRequest> request
    ) {
        ListCardsResponse response = cardService.listCards(
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

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<OrderCardResponse>> order(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<OrderCardRequest> request
    ) {
        OrderCardResponse response = cardService.orderCard(
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

    @PostMapping("/freeze")
    public ResponseEntity<ApiResponse<CardStatusResponse>> freeze(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<FreezeCardRequest> request
    ) {
        CardStatusResponse response = cardService.freeze(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return success(request, response);
    }

    @PostMapping("/unfreeze")
    public ResponseEntity<ApiResponse<CardStatusResponse>> unfreeze(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<UnfreezeCardRequest> request
    ) {
        CardStatusResponse response = cardService.unfreeze(
                authorizationHeader,
                request.context(),
                request.data()
        );
        return success(request, response);
    }

    @PostMapping("/block")
    public ResponseEntity<ApiResponse<BlockCardResponse>> block(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<BlockCardRequest> request
    ) {
        BlockCardResponse response = cardService.block(
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

    @PostMapping("/unblock")
    public ResponseEntity<ApiResponse<CardStatusResponse>> unblock(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<UnblockCardRequest> request
    ) {
        CardStatusResponse response = cardService.unblock(authorizationHeader, request.data());
        return success(request, response);
    }

    @PostMapping("/expire")
    public ResponseEntity<ApiResponse<CardStatusResponse>> expire(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody ApiRequest<ExpireCardRequest> request
    ) {
        CardStatusResponse response = cardService.expire(authorizationHeader, request.data());
        return success(request, response);
    }

    private <T> ResponseEntity<ApiResponse<T>> success(ApiRequest<?> request, T response) {
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
