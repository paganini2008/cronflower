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
package com.github.cronflow.springapp.server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import com.github.cronsmith.springapp.scheduler.LeaderSchedulerLifecycle;
import com.github.cronsmith.springapp.scheduler.SchedulerLifecycle;
import com.github.cronsmith.springapp.scheduler.ShardedSchedulerLifecycle;
import com.github.cronsmith.springapp.scheduler.TaskListener;
import com.github.cronsmith.springapp.scheduler.TimeWheelScheduler;

/**
 * Attaches {@link DagTriggerTaskListener} to the running cronsmith scheduler using only cronsmith's
 * public API — no changes to cronsmith. The scheduler's timing wheel is created privately and rebuilt
 * on every leadership change, so this idempotently re-attaches the listener to whatever wheel is
 * current, on a short reconcile loop.
 *
 * <p>
 * TODO: react to gossip/leadership events instead of polling, once a public hook is available.
 *
 * @Description: TaskListenerAttacher
 * @Author: Fred Feng
 * @Version 1.0.0
 */
public class TaskListenerAttacher
        implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TaskListenerAttacher.class);

    private final ObjectProvider<SchedulerLifecycle> lifecycleProvider;
    private final TaskListener listener;

    private ScheduledExecutorService scheduler;
    private final Set<Integer> attachedWheels = ConcurrentHashMap.newKeySet();

    public TaskListenerAttacher(ObjectProvider<SchedulerLifecycle> lifecycleProvider,
            TaskListener listener) {
        this.lifecycleProvider = lifecycleProvider;
        this.listener = listener;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cronflow-listener-attacher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::ensureAttached, 0, 5, TimeUnit.SECONDS);
    }

    private void ensureAttached() {
        try {
            SchedulerLifecycle lifecycle = lifecycleProvider.getIfAvailable();
            TimeWheelScheduler wheel = currentWheel(lifecycle);
            if (wheel != null && attachedWheels.add(System.identityHashCode(wheel))) {
                wheel.addTaskListener(listener);
                log.info("cronflow: DAG trigger listener attached to the current scheduler wheel");
            }
        } catch (RuntimeException e) {
            log.debug("cronflow: listener attach tick failed: {}", e.toString());
        }
    }

    private TimeWheelScheduler currentWheel(SchedulerLifecycle lifecycle) {
        if (lifecycle instanceof LeaderSchedulerLifecycle leader) {
            return leader.currentScheduler();
        }
        if (lifecycle instanceof ShardedSchedulerLifecycle sharded) {
            return sharded.currentScheduler();
        }
        return null;
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

}
