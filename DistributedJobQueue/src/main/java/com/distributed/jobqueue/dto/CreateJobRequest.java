package com.distributed.jobqueue.dto;

import lombok.Data;

/**
 * Request body for POST /api/jobs
 */
@Data
public class CreateJobRequest {

    private String payload;
    private String idempotencyKey;
    private Integer maxRetries;
}
