package com.fixlog.presentation.dto.response;

import java.util.List;

public record BulkOperationResultDto(
        int totalRequested,
        int succeededCount,
        int failedCount,
        List<String> succeededIds,
        List<FailedItem> failures
) {
    public record FailedItem(String id, String reason) {}

    public static BulkOperationResultDto of(List<String> succeeded,
                                            List<FailedItem> failures,
                                            int totalRequested) {
        return new BulkOperationResultDto(
                totalRequested,
                succeeded.size(),
                failures.size(),
                succeeded,
                failures);
    }
}
