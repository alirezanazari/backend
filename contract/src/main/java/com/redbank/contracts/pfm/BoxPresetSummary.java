package com.redbank.contracts.pfm;

import com.redbank.contracts.common.Image;

public record BoxPresetSummary(
        String presetId,
        String title,
        Image image,
        Integer sortOrder
) {
}
