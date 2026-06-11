package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.pfm.BoxSummary;

public record EditBoxResponse(
        BoxSummary box
) {
}
