package com.github.cronsmith.springapp.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Manual test bed for the cronsmith scheduler starter: a single-node cluster over an embedded H2, which
 * (as the leader) schedules tasks registered by executors and dispatches them when due.
 */
@SpringBootApplication
public class SchedulerExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerExampleApplication.class, args);
    }

}
