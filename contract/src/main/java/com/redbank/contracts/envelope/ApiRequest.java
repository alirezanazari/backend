package com.redbank.contracts.envelope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ApiRequest<T>(
        @NotNull @Valid RequestContext context,
        @NotNull @Valid T data
) {
}
