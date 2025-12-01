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

@Service
@RequiredArgsConstructor
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    // how long a job lease lasts (if worker dies before ack)
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    // for demo: max jobs processed per tick
    private static final int BATCH_SIZE = 5;

    private final JobRepository jobRepository;
    private final JobService jobService; // for reusing toResponse if needed

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
            log.info("Job started: jobId={}, tenantId={}", job.getId(), job.getTenantId());

            // ======== SIMULATED JOB WORK ============
            // In reality you'd parse job.getPayload() and perform some actual work.
            // Here we just sleep for demo.
            Thread.sleep(1000L);
            // maybe pretend to sometimes fail:
            if (Math.random() < 0.2) {
                throw new RuntimeException("Simulated random failure");
            }
            // ========================================

            // ACK success
            job.setStatus(JobStatus.COMPLETED);
            job.setLeasedUntil(null);
            job.setAttemptCount(job.getAttemptCount() + 1);
            job.setUpdatedAt(Instant.now());
            job.setCompletedAt(Instant.now());
            job.setLastError(null);
            jobRepository.save(job);

            log.info("Job completed: jobId={}, tenantId={}", job.getId(), job.getTenantId());
        } catch (Exception e) {
            handleFailure(job, e);
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
        } else {
            job.setStatus(JobStatus.PENDING);
            job.setLeasedUntil(null);
            log.warn("Job failed, will retry: jobId={}, tenantId={}, attempt={}/{}, error={}",
                    job.getId(), job.getTenantId(), nextAttempt, job.getMaxRetries(), e.getMessage());
        }

        jobRepository.save(job);
    }
}
