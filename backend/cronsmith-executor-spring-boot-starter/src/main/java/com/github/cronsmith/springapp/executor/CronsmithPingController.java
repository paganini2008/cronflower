package com.github.cronsmith.springapp.executor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal liveness probe used only when Spring Boot Actuator is not on the classpath. When
 * actuator is present the server health-checks {@code /actuator/health} instead, and this controller
 * is not registered.
 *
 * @Description: CronsmithPingController
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@RestController
public class CronsmithPingController {

    /** Path this endpoint is mapped to, relative to the dispatcher (context/servlet path aside). */
    public static final String PING_PATH = "/cronsmith/ping";

    @GetMapping(PING_PATH)
    public String ping() {
        return "PONG";
    }

}
