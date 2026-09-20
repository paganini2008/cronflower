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
package com.github.cronsmith.springapp.scheduler.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.github.cronsmith.springapp.scheduler.TaskDetailNotFoundException;
import com.github.cronsmith.springapp.scheduler.TaskException;
import com.github.cronsmith.utils.ExceptionUtils;

/**
 * One place the server turns exceptions into HTTP responses, so every endpoint degrades the same
 * way. The full cause is logged through {@code ExceptionUtils}; the client sees a short message.
 *
 * @Description: ServerExceptionHandler
 * @Author: Fred Feng
 * @Date: 25/08/2026
 * @Version 1.0.0
 */
@RestControllerAdvice
public class ServerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ServerExceptionHandler.class);

    public record ErrorResponse(int status, String error, String message) {
    }

    @ExceptionHandler(TaskDetailNotFoundException.class)
    public ResponseEntity<ErrorResponse> onNotFound(TaskDetailNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> onBadRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(TaskException.class)
    public ResponseEntity<ErrorResponse> onTaskException(TaskException e) {
        log.warn("Task operation failed: {}", ExceptionUtils.toString(e));
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onOther(Exception e) {
        log.error("Unhandled server error: {}", ExceptionUtils.toString(e));
        return build(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, Throwable e) {
        Throwable original = ExceptionUtils.getOriginalException(e);
        String message = original != null && original.getMessage() != null ? original.getMessage()
                : String.valueOf(e.getMessage());
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }

}
