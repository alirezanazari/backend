package com.redbank.transfer.api.v1.dto;

import java.util.List;

public record ListRecentTransfersResponse(
        List<RecentTransferDestination> items
) {
}
