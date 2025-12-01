package com.distributed.jobqueue.controller;

import com.distributed.jobqueue.model.JobStatus;
import com.distributed.jobqueue.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Simple JSON metrics endpoint for observability.
 */
@RestController
@RequiredArgsConstructor
public class MetricsController {

    private final JobRepository jobRepository;

    @GetMapping("/api/metrics")
    public Map<String, Object> metrics() {
        long total = jobRepository.count();
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long dlq = jobRepository.countByStatus(JobStatus.DLQ);

        return Map.of(
                "totalJobs", total,
                "completedJobs", completed,
                "failedJobs", failed,
                "runningJobs", running,
                "pendingJobs", pending,
                "dlqJobs", dlq
        );
    }
}
