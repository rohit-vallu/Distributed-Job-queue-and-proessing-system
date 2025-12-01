package com.distributed.jobqueue.dto;

import com.distributed.jobqueue.model.JobStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response DTO for job details.
 */
@Data
@Builder
public class JobResponse {
    private Long id;
    private String tenantId;
    private JobStatus status;
    private String payload;
    private int attemptCount;
    private int maxRetries;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String lastError;
}
