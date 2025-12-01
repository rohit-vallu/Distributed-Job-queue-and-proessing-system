package com.distributed.jobqueue.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * This table is the SHARED QUEUE used by ALL worker instances.
 */
@Entity
@Table(
        name = "jobs",
        indexes = {
                @Index(name = "idx_tenant_status", columnList = "tenantId,status"),
                @Index(name = "idx_idempotency", columnList = "tenantId,idempotencyKey", unique = true)
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tenant that owns the job.
     */
    @Column(nullable = false)
    private String tenantId;

    /**
     * Optional idempotency key to avoid duplicate jobs.
     */
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;


    @Lob
    private String payload;

    /**
     * How many attempts made so far.
     */
    @Column(nullable = false)
    private int attemptCount;

    /**
     * Maximum retries allowed before going to DLQ.
     */
    @Column(nullable = false)
    private int maxRetries;

    /**
     * Time until which this job is leased to a worker.
     * If leasedUntil < now, another worker can pick it up.
     */
    private Instant leasedUntil;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    private String lastError;
}
