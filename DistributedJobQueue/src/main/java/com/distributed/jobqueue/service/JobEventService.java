package com.distributed.jobqueue.service;

import com.distributed.jobqueue.model.JobEvent;
import com.distributed.jobqueue.repository.JobEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JobEventService {

    private final JobEventRepository repo;

    public void logEvent(Long jobId, String tenantId, String type, String message) {

        JobEvent evt = JobEvent.builder()
                .jobId(jobId)
                .tenantId(tenantId)
                .eventType(type)
                .message(message)
                .timestamp(Instant.now())
                .build();

        repo.save(evt);
    }
}
