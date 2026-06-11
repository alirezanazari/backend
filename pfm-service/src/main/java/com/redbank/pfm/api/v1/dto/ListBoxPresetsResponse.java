package com.redbank.pfm.api.v1.dto;

import com.redbank.contracts.pfm.BoxPresetSummary;
import java.util.List;

public record ListBoxPresetsResponse(
        List<BoxPresetSummary> presets
) {
}
