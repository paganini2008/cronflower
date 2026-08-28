package com.github.cronsmith.springapp.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Covers reflective invocation, SpEL vs constant parameters, and the completeTask callback on both
 * the success and the failure path.
 */
class DefaultTaskExecutionServiceTests {

    private GenericApplicationContext ctx;
    private CronsmithServerClient client;
    private DefaultTaskExecutionService service;

    @BeforeEach
    void setUp() {
        ctx = new GenericApplicationContext();
        ctx.registerBean("greeter", Greeter.class);
        ctx.registerBean("valueBean", ValueBean.class);
        ctx.refresh();
        client = Mockito.mock(CronsmithServerClient.class);
        when(client.complete(any())).thenReturn(true);
        service = new DefaultTaskExecutionService(ctx, client, new ExecutorIdentity(), 2);
    }

    @AfterEach
    void tearDown() {
        service.destroy();
        ctx.close();
    }

    private RunRequest run(String method, String param) {
        return new RunRequest("exec-1", "grp", "task", Greeter.class.getName(), "greeter", method,
                param, 0, -1L, null);
    }

    private CompleteRequest await() {
        ArgumentCaptor<CompleteRequest> captor = ArgumentCaptor.forClass(CompleteRequest.class);
        verify(client, timeout(3000)).complete(captor.capture());
        return captor.getValue();
    }

    @Test
    void invokesNoArgMethodAndReportsSuccess() {
        service.dispatch(run("hello", null));
        CompleteRequest done = await();
        assertThat(done.success()).isTrue();
        assertThat(done.returnValue()).isEqualTo("hi");
        assertThat(done.errorDetail()).isNull();
        assertThat(done.executionId()).isEqualTo("exec-1");
    }

    @Test
    void passesConstantParameterToStringMethod() {
        service.dispatch(run("echo", "world"));
        CompleteRequest done = await();
        assertThat(done.success()).isTrue();
        assertThat(done.returnValue()).isEqualTo("echo:world");
    }

    @Test
    void evaluatesSpelParameterAgainstBeans() {
        service.dispatch(run("echo", "#{@valueBean.value}"));
        CompleteRequest done = await();
        assertThat(done.success()).isTrue();
        assertThat(done.returnValue()).isEqualTo("echo:42");
    }

    @Test
    void reportsFailureWithStackTrace() {
        service.dispatch(run("boom", null));
        CompleteRequest done = await();
        assertThat(done.success()).isFalse();
        assertThat(done.returnValue()).isNull();
        assertThat(done.errorDetail()).contains("boom");
    }

    // ---- fixtures ----

    static class Greeter {
        public String hello() {
            return "hi";
        }

        public String echo(String s) {
            return "echo:" + s;
        }

        public void boom() {
            throw new IllegalStateException("boom");
        }
    }

    static class ValueBean {
        public String getValue() {
            return "42";
        }
    }

}
