package com.distributed.jobqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * - This acts as both API server and worker process.
 * - Every instance of this app includes WorkerService scheduled tasks.
 * - Multiple instances => multiple workers sharing the same Postgres queue.
 */
@SpringBootApplication
@EnableScheduling
public class DistributedJobQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedJobQueueApplication.class, args);
    }
}
