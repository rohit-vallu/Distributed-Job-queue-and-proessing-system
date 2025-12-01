package com.distributed.jobqueue.repository;

import com.distributed.jobqueue.model.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobEventRepository extends JpaRepository<JobEvent, Long> {

    List<JobEvent> findTop50ByOrderByTimestampDesc();
}

