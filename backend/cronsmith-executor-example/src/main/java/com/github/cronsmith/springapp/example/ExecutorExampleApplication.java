package com.github.cronsmith.springapp.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Manual test bed for cronsmith-executor-spring-boot-starter. It carries a @Task bean and a small mock
 * of the server endpoints, and points the client at itself, so the whole loop — register, dispatch,
 * complete — can be exercised on one process.
 */
@SpringBootApplication
public class ExecutorExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorExampleApplication.class, args);
    }

}
