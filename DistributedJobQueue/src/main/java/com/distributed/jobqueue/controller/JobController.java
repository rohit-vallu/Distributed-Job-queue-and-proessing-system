package com.distributed.jobqueue.controller;

import com.distributed.jobqueue.dto.CreateJobRequest;
import com.distributed.jobqueue.dto.JobResponse;
import com.distributed.jobqueue.model.Job;
import com.distributed.jobqueue.model.JobStatus;
import com.distributed.jobqueue.repository.JobRepository;
import com.distributed.jobqueue.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for job submission, status lookup, and dashboard queries.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobRepository jobRepository;

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @PostMapping
    public ResponseEntity<?> createJob(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @RequestBody CreateJobRequest request
    ) {
        try {
            return ResponseEntity.ok(jobService.submitJob(tenantId, request));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429).body(e.getMessage()); // Too Many Requests
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getJob(
            @RequestHeader(TENANT_HEADER) String tenantId,
            @PathVariable Long id
    ) {
        try {
            // For simplicity we don't enforce tenant isolation on read here,
            // but we can easily check job.tenantId.equals(tenantId).
            return ResponseEntity.ok(jobService.getJob(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<?> listJobs(
            @RequestHeader(value = TENANT_HEADER, required = false) String tenantId,
            @RequestParam(required = false) String status
    ) {
        List<Job> jobs;

        // Tenant-specific fetch if tenant header present
        if (tenantId != null && !tenantId.isBlank()) {
            if (status != null) {
                JobStatus st = JobStatus.valueOf(status.toUpperCase());
                jobs = jobRepository.findByTenantIdAndStatus(tenantId, st);
            } else {
                jobs = jobRepository.findByTenantId(tenantId);
            }
        } else {
            // GLOBAL FETCH when no tenant header — UI filter OFF
            if (status != null) {
                JobStatus st = JobStatus.valueOf(status.toUpperCase());
                jobs = jobRepository.findByStatus(st);
            } else {
                jobs = jobRepository.findAll();
            }
        }

        List<JobResponse> responses = jobs.stream()
                .map(jobService::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }


    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestParam String tenantId
    ) {
        long total = jobRepository.countByTenantId(tenantId);
        long pending = jobRepository.countByTenantIdAndStatusIn(tenantId, List.of(JobStatus.PENDING));
        long running = jobRepository.countByTenantIdAndStatusIn(tenantId, List.of(JobStatus.RUNNING));
        long completed = jobRepository.countByTenantIdAndStatusIn(tenantId, List.of(JobStatus.COMPLETED));
        long failed = jobRepository.countByTenantIdAndStatusIn(tenantId, List.of(JobStatus.FAILED));
        long dlq = jobRepository.countByTenantIdAndStatusIn(tenantId, List.of(JobStatus.DLQ));

        Map<String, Long> summary = Map.of(
                "total", total,
                "pending", pending,
                "running", running,
                "completed", completed,
                "failed", failed,
                "dlq", dlq
        );
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/summary/global")
    public ResponseEntity<?> globalSummary() {

        long total = jobRepository.count();
        long pending = jobRepository.countByStatus(JobStatus.PENDING);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long dlq = jobRepository.countByStatus(JobStatus.DLQ);

        return ResponseEntity.ok(Map.of(
                "pending", pending,
                "running", running,
                "completed", completed,
                "failed", failed,
                "dlq", dlq,
                "total", total
        ));
    }

}
