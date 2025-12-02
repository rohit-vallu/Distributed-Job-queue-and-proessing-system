package com.distributed.jobqueue.service;

import com.distributed.jobqueue.model.Job;
import com.distributed.jobqueue.model.JobStatus;
import com.distributed.jobqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
@RequiredArgsConstructor
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    // how long a job lease lasts (if worker dies before ack)
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    // for simplicity: max jobs processed per tick
    private static final int BATCH_SIZE = 5;

    private final JobRepository jobRepository;
    private final JobEventService jobEventService;

    private final ObjectMapper objectMapper;

    // Run every 5 seconds
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollAndProcessJobs() {
        leaseAndProcessJobs();
    }

    @Transactional
    public void leaseAndProcessJobs() {
        Instant now = Instant.now();
        List<Job> candidates = jobRepository.findPendingForLease(now);
        if (candidates.isEmpty()) {
            return;
        }

        candidates.stream()
                .limit(BATCH_SIZE)
                .forEach(job -> {
                    // mark as RUNNING, set lease
                    job.setStatus(JobStatus.RUNNING);
                    job.setLeasedUntil(now.plus(LEASE_DURATION));
                    job.setUpdatedAt(now);
                    jobRepository.save(job);

                    log.info("Job leased: jobId={}, tenantId={}", job.getId(), job.getTenantId());
                    jobEventService.logEvent(
                            job.getId(),
                            job.getTenantId(),
                            "LEASED",
                            "Job leased by worker"
                    );
                });

        // After leasing, process outside of TX (to avoid long-running transactions)
        for (Job job : candidates.stream().limit(BATCH_SIZE).toList()) {
            processOneJob(job.getId());
        }
    }

    @Transactional
    public void processOneJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElse(null);
        if (job == null) {
            return;
        }

        try {
            if (job.getPayload().contains("failMe")) {
                job.setAttemptCount(3);
                throw new RuntimeException("Payload instructed failure");
            }
            log.info("Job started: jobId={}, tenantId={}", job.getId(), job.getTenantId());
            jobEventService.logEvent(
                    job.getId(),
                    job.getTenantId(),
                    "STARTED",
                    "Job started processing"
            );

            applyPayloadSideEffects(job);
            Thread.sleep(1000L);

            if (Math.random() < 0.2) {
                throw new RuntimeException("This failed");
            }

            job.setStatus(JobStatus.COMPLETED);
            job.setLeasedUntil(null);
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setUpdatedAt(Instant.now());
            job.setCompletedAt(Instant.now());
            job.setLastError(null);
            jobRepository.save(job);

            log.info("Job completed: jobId={}, tenantId={}", job.getId(), job.getTenantId());
            jobEventService.logEvent(
                    job.getId(),
                    job.getTenantId(),
                    "COMPLETED",
                    "Job completed successfully"
            );
        } catch (Exception e) {
            handleFailure(job, e);
        }
    }

    private void applyPayloadSideEffects(Job job) {
        try {
            if (job.getPayload() == null || job.getPayload().isBlank()) {
                return;
            }

            JsonNode root = objectMapper.readTree(job.getPayload());

            // If payload has a "color" field, emit a COLOR_CHANGE event
            if (root.hasNonNull("color")) {
                String color = root.get("color").asText();
                log.info("Applying color change", color);

                jobEventService.logEvent(
                        job.getId(),
                        job.getTenantId(),
                        "COLOR_CHANGE",
                        color
                );
            }

            // You can extend this later for more actions, e.g. "action": "something"

        } catch (Exception e) {
            log.warn("Failed to parse payload for job {} for side effects: {}", job.getId(), e.getMessage());
        }
    }

    @Transactional
    protected void handleFailure(Job job, Exception e) {
        int nextAttempt = job.getAttemptCount() + 1;
        job.setAttemptCount(nextAttempt);
        job.setUpdatedAt(Instant.now());
        job.setLastError(e.getMessage());

        if (nextAttempt > job.getMaxRetries()) {
            job.setStatus(JobStatus.DLQ);
            job.setLeasedUntil(null);
            log.warn("Job moved to DLQ: jobId={}, tenantId={}, error={}",
                    job.getId(), job.getTenantId(), e.getMessage());
            jobEventService.logEvent(
                    job.getId(),
                    job.getTenantId(),
                    "DLQ",
                    "Job moved to DLQ after retry exhaustion"
            );
        } else {
            job.setStatus(JobStatus.PENDING);
            job.setLeasedUntil(null);
            log.warn("Job failed, will retry: jobId={}, tenantId={}, attempt={}/{}, error={}",
                    job.getId(), job.getTenantId(), nextAttempt, job.getMaxRetries(), e.getMessage());
            jobEventService.logEvent(
                    job.getId(),
                    job.getTenantId(),
                    "FAILED",
                    "Job failed: " + e.getMessage()
            );

        }

        jobRepository.save(job);
    }
}
