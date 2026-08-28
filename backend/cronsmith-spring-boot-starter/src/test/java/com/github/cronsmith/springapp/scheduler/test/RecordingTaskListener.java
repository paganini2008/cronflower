package com.github.cronsmith.springapp.scheduler.test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskListener;

/**
 * 
 * A listener that counts every callback, so a test can assert the scheduler drove a task through
 * the lifecycle it expected rather than only that the body ran.
 * 
 * @Description: RecordingTaskListener
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
class RecordingTaskListener implements TaskListener {

    final AtomicInteger scheduled = new AtomicInteger();
    final AtomicInteger triggered = new AtomicInteger();
    final AtomicInteger began = new AtomicInteger();
    final AtomicInteger ended = new AtomicInteger();
    final AtomicInteger misfired = new AtomicInteger();
    final AtomicInteger canceled = new AtomicInteger();
    final AtomicInteger finished = new AtomicInteger();
    final List<Throwable> endErrors = new CopyOnWriteArrayList<>();

    @Override
    public void onTaskScheduled(LocalDateTime scheduledDateTime, TaskDetail taskDetail) {
        scheduled.incrementAndGet();
    }

    @Override
    public void onTaskTriggered(LocalDateTime firedDateTime, TaskDetail taskDetail) {
        triggered.incrementAndGet();
    }

    @Override
    public void onTaskBegan(LocalDateTime firedDateTime, TaskDetail taskDetail) {
        began.incrementAndGet();
    }

    @Override
    public void onTaskEnded(LocalDateTime firedDateTime, TaskDetail taskDetail, Object returnValue,
            Throwable e) {
        ended.incrementAndGet();
        if (e != null) {
            endErrors.add(e);
        }
    }

    @Override
    public void onTaskMisfired(LocalDateTime missedDateTime, TaskDetail taskDetail) {
        misfired.incrementAndGet();
    }

    @Override
    public void onTaskCanceled(TaskDetail taskDetail) {
        canceled.incrementAndGet();
    }

    @Override
    public void onTaskFinished(TaskDetail taskDetail) {
        finished.incrementAndGet();
    }

}
