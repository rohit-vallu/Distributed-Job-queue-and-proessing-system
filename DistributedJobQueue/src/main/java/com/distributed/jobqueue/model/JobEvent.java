package com.distributed.jobqueue.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "job_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("jobId")
    private Long jobId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("eventType")
    private String eventType;

    @Lob
    private String message;

    private Instant timestamp;
}