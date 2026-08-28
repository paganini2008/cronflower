package com.github.cronsmith.springapp.executor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives dispatches from the server. This is a plain annotated controller, so it is served by
 * Spring MVC or Spring WebFlux, whichever the host application uses.
 *
 * <p>
 * The handler returns as soon as the run is accepted; the task runs on a pool thread and its result
 * is reported back separately. Nothing here blocks the calling thread, so it is safe on a reactive
 * event loop too.
 *
 * @Description: CronsmithClientController
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@RestController
public class CronsmithClientController {

    /** Path this endpoint is mapped to, relative to the dispatcher (context/servlet path aside). */
    public static final String RUN_PATH = "/cronsmith/run";

    private final TaskExecutionService taskExecutionService;

    public CronsmithClientController(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @PostMapping(RUN_PATH)
    public ResponseEntity<Void> run(@RequestBody RunRequest request) {
        taskExecutionService.dispatch(request);
        return ResponseEntity.accepted().build();
    }

}
