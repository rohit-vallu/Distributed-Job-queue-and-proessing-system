package com.distributed.jobqueue.controller;

import com.distributed.jobqueue.model.JobEvent;
import com.distributed.jobqueue.repository.JobEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class JobEventController {

    private final JobEventRepository repo;

    @GetMapping
    public List<JobEvent> getEvents() {
        return repo.findTop50ByOrderByTimestampDesc();
    }
}
