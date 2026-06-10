package com.redbank.kyc.api.v1.dto;

import java.util.List;

public record ListJobsResponse(
        List<JobSummary> jobs
) {
    public ListJobsResponse {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }
}
