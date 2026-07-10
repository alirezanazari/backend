package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.pfm.BoxStatus;
import java.util.UUID;
import java.time.Instant;

public record CloseBoxResponse (UUID id,
                                BoxStatus status,
                                Instant closedAt
) {
}
