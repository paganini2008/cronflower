/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
