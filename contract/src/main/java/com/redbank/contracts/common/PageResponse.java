package com.redbank.contracts.common;

public record PageResponse(
        Integer page,
        Integer size,
        Long totalItems,
        Boolean hasNext
) {
}
