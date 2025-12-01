package com.distributed.jobqueue.service;

import com.distributed.jobqueue.dto.CreateJobRequest;
import com.distributed.jobqueue.dto.JobResponse;
import com.distributed.jobqueue.model.Job;
import com.distributed.jobqueue.model.JobStatus;
import com.distributed.jobqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Handles job submission and querying.
 *
 * Responsibilities:
 * - Enforce per-tenant rate limits
 * - Enforce max concurrent jobs per tenant (pending + running)
 * - Apply idempotency key logic
 * - Map Job entity to DTO
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private static final int MAX_CONCURRENT_JOBS_PER_TENANT = 5;
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final JobRepository jobRepository;
    private final RateLimiterService rateLimiterService;

    public JobResponse submitJob(String tenantId, CreateJobRequest request) {
        if (!rateLimiterService.allowSubmission(tenantId)) {
            throw new IllegalStateException("Rate limit exceeded (10 new jobs per minute)");
        }

        // Idempotency: if key is provided, return existing job if present
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            return jobRepository.findByTenantIdAndIdempotencyKey(tenantId, request.getIdempotencyKey())
                    .map(this::toResponse)
                    .orElseGet(() -> createNewJob(tenantId, request));
        }

        return createNewJob(tenantId, request);
    }

    private JobResponse createNewJob(String tenantId, CreateJobRequest request) {
        long runningCount = jobRepository.countByTenantIdAndStatusIn(
                tenantId,
                List.of(JobStatus.PENDING, JobStatus.RUNNING)
        );
        if (runningCount >= MAX_CONCURRENT_JOBS_PER_TENANT) {
            throw new IllegalStateException("Too many concurrent jobs (max 5 pending/running)");
        }

        Instant now = Instant.now();
        Job job = Job.builder()
                .tenantId(tenantId)
                .idempotencyKey(request.getIdempotencyKey())
                .status(JobStatus.PENDING)
                .payload(request.getPayload())
                .attemptCount(0)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : DEFAULT_MAX_RETRIES)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Job saved = jobRepository.save(job);
        log.info("Job submitted: jobId={}, tenantId={}", saved.getId(), tenantId);
        return toResponse(saved);
    }

    public JobResponse getJob(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
        return toResponse(job);
    }

    public JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .tenantId(job.getTenantId())
                .status(job.getStatus())
                .payload(job.getPayload())
                .attemptCount(job.getAttemptCount())
                .maxRetries(job.getMaxRetries())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .completedAt(job.getCompletedAt())
                .lastError(job.getLastError())
                .build();
    }
}
