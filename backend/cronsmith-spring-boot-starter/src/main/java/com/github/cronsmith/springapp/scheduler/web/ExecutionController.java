package com.github.cronsmith.springapp.scheduler.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronsmith.springapp.scheduler.CompleteRequest;
import com.github.cronsmith.springapp.scheduler.TaskDispatcher;

/**
 * Where executors report a finished run. The dispatcher matches it to the pending run by
 * {@code executionId} — forwarding to the leader if this node was not the one that dispatched.
 *
 * @Description: ExecutionController
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@RestController
public class ExecutionController {

    private final TaskDispatcher dispatcher;

    public ExecutionController(TaskDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/executions/complete")
    public void complete(@RequestBody CompleteRequest request) {
        dispatcher.complete(request);
    }

}
