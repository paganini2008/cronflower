package com.github.cronflow.springapp.dag;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.ReflectionUtils;
import com.github.cronflow.springapp.scheduler.LeaderSchedulerLifecycle;
import com.github.cronflow.springapp.scheduler.SchedulerLifecycle;
import com.github.cronflow.springapp.scheduler.ShardedSchedulerLifecycle;
import com.github.cronflow.springapp.scheduler.TaskListener;
import com.github.cronflow.springapp.scheduler.TimeWheelScheduler;

/**
 * Covers the reconcile loop that re-attaches the DAG trigger listener to whatever scheduler wheel is
 * current — leader and sharded lifecycles, idempotency, the no-wheel case, and the error branch.
 */
class TaskListenerAttacherTests {

    private final TaskListener listener = mock(TaskListener.class);

    @SuppressWarnings("unchecked")
    private ObjectProvider<SchedulerLifecycle> provider(SchedulerLifecycle lifecycle) {
        ObjectProvider<SchedulerLifecycle> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(lifecycle);
        return provider;
    }

    private void tick(TaskListenerAttacher attacher) {
        Method m = ReflectionUtils.findMethod(TaskListenerAttacher.class, "ensureAttached");
        ReflectionUtils.makeAccessible(m);
        ReflectionUtils.invokeMethod(m, attacher);
    }

    @Test
    void attachesToLeaderWheelOnceAcrossTicks() {
        LeaderSchedulerLifecycle leader = mock(LeaderSchedulerLifecycle.class);
        TimeWheelScheduler wheel = mock(TimeWheelScheduler.class);
        when(leader.currentScheduler()).thenReturn(wheel);
        TaskListenerAttacher attacher = new TaskListenerAttacher(provider(leader), listener);
        tick(attacher);
        tick(attacher); // same wheel -> not attached again
        verify(wheel, times(1)).addTaskListener(listener);
    }

    @Test
    void attachesToShardedWheel() {
        ShardedSchedulerLifecycle sharded = mock(ShardedSchedulerLifecycle.class);
        TimeWheelScheduler wheel = mock(TimeWheelScheduler.class);
        when(sharded.currentScheduler()).thenReturn(wheel);
        TaskListenerAttacher attacher = new TaskListenerAttacher(provider(sharded), listener);
        tick(attacher);
        verify(wheel).addTaskListener(listener);
    }

    @Test
    void noWheelWhenLifecycleIsNullOrPlain() {
        TaskListenerAttacher nullLc = new TaskListenerAttacher(provider(null), listener);
        tick(nullLc); // no exception, nothing to attach
        SchedulerLifecycle plain = mock(SchedulerLifecycle.class);
        TaskListenerAttacher plainLc = new TaskListenerAttacher(provider(plain), listener);
        tick(plainLc);
        verify(listener, never()).onTaskEnded(any(), any(), any(), any());
    }

    @Test
    void errorDuringTickIsSwallowed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SchedulerLifecycle> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenThrow(new RuntimeException("provider down"));
        TaskListenerAttacher attacher = new TaskListenerAttacher(provider, listener);
        tick(attacher); // must not propagate
    }

    @Test
    void lifecycleEventStartsAndDestroyStopsTheLoop() {
        LeaderSchedulerLifecycle leader = mock(LeaderSchedulerLifecycle.class);
        when(leader.currentScheduler()).thenReturn(mock(TimeWheelScheduler.class));
        TaskListenerAttacher attacher = new TaskListenerAttacher(provider(leader), listener);
        attacher.onApplicationEvent(null);
        attacher.destroy();
        // destroy is also safe when never started
        new TaskListenerAttacher(provider(leader), listener).destroy();
    }
}
