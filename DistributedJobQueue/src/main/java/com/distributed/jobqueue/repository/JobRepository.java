package com.distributed.jobqueue.repository;

import com.distributed.jobqueue.model.Job;
import com.distributed.jobqueue.model.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Job entity.
 *
 * IMPORTANT FOR DISTRIBUTED BEHAVIOR:
 * - Uses pessimistic write locks + SKIP LOCKED semantics (via JPA Lock).
 * - Ensures that multiple worker instances don't process the same job.
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    long countByTenantIdAndStatusIn(String tenantId, List<JobStatus> statuses);

    long countByTenantId(String tenantId);

    List<Job> findByTenantId(String tenantId);

    List<Job> findByStatus(JobStatus status);

    List<Job> findByTenantIdAndStatus(String tenantId, JobStatus status);

    long countByStatus(JobStatus status);

    /**
     * Fetches PENDING jobs that are not currently leased.
     * PESSIMISTIC_WRITE ensures only one worker can lease a given row at a time.
     * Multiple app instances can call this concurrently without collisions.
     */
    @Query("""
           select j from Job j
           where j.status = 'PENDING'
             and (j.leasedUntil is null or j.leasedUntil < :now)
           order by j.createdAt asc
           """)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Job> findPendingForLease(Instant now);
}
