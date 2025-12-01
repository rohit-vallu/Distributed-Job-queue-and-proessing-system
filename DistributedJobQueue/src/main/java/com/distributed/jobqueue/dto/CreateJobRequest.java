package com.distributed.jobqueue.dto;

import lombok.Data;

/**
 * Request body for POST /api/jobs
 */
@Data
public class CreateJobRequest {

    /**
     * Arbitrary JSON payload as string.
     * Example: "{\"action\":\"sendEmail\",\"to\":\"user@example.com\"}"
     */
    private String payload;

    /**
     * Optional idempotency key to prevent duplicate submissions.
     */
    private String idempotencyKey;

    /**
     * Optional per-job max retries (default: 3).
     */
    private Integer maxRetries;
}
