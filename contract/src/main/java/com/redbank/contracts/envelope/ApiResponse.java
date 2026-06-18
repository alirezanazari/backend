package com.redbank.contracts.envelope;

public record ApiResponse<T>(
        ResponseContext context,
        T data
) {
    public static <T> ApiResponse<T> success(String requestId, String profileId, T data) {
        return new ApiResponse<>(
                new ResponseContext(requestId, profileId, Status.success()),
                data
        );
    }

    public static <T> ApiResponse<T> created(String requestId, String profileId, T data) {
        return new ApiResponse<>(
                new ResponseContext(requestId, profileId, Status.created()),
                data
        );
    }

    public static <T> ApiResponse<T> failure(String requestId, String profileId, Status status) {
        return new ApiResponse<>(
                new ResponseContext(requestId, profileId, status),
                null
        );
    }

    public static <T> ApiResponse<T> failure(
            String requestId,
            String correlationId,
            String profileId,
            Status status
    ) {
        return new ApiResponse<>(
                new ResponseContext(requestId, correlationId, profileId, status),
                null
        );
    }
}
