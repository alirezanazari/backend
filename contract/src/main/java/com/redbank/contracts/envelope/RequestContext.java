package com.redbank.contracts.envelope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestContext(
        @NotBlank @Size(max = 80) String requestId,
        @Valid AccountContext account,
        @Valid OsContext os,
        @Valid AppContext app,
        @Valid DeviceContext device,
        @Valid NetworkContext network
) {
}
