package com.github.cronsmith.springapp.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.TaskDetail;
import com.github.cronsmith.springapp.scheduler.TaskId;
import com.github.cronsmith.springapp.scheduler.TaskManager;
import com.github.cronsmith.springapp.scheduler.TaskRestoreHandler;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * The sharding decorator only feeds a node its own groups: the claim, restore and RUNNING-recovery
 * paths are filtered to owned groups, and the fire transition is refused for a group no longer owned.
 * Everything else passes straight through.
 *
 * @Description: ShardingTaskManagerTests
 * @Author: Fred Feng
 * @Date: 26/08/2026
 * @Version 1.0.0
 */
class ShardingTaskManagerTests {

    private final TaskManager delegate = mock(TaskManager.class);
    private final GroupShardingStrategy sharding = mock(GroupShardingStrategy.class);
    private final ShardingTaskManager manager = new ShardingTaskManager(delegate, sharding);

    private static final TaskId MINE = TaskId.of("mine", "t1");
    private static final TaskId THEIRS = TaskId.of("theirs", "t2");

    private void ownOnlyMine() {
        when(sharding.owns(MINE)).thenReturn(true);
        when(sharding.owns(THEIRS)).thenReturn(false);
    }

    @Test
    void findUpcomingKeepsOnlyOwnedGroups() {
        ownOnlyMine();
        LocalDateTime s = LocalDateTime.now();
        LocalDateTime e = s.plusMinutes(5);
        when(delegate.findUpcomingTasksBetween(s, e)).thenReturn(List.of(MINE, THEIRS));

        assertThat(manager.findUpcomingTasksBetween(s, e)).containsExactly(MINE);
    }

    @Test
    void restoreOnlyReplaysOwnedGroups() {
        ownOnlyMine();
        doAnswer(inv -> {
            TaskRestoreHandler h = inv.getArgument(0);
            h.onRestore(MINE, null);
            h.onRestore(THEIRS, null);
            return null;
        }).when(delegate).restoreTasks(any());

        List<TaskId> restored = new ArrayList<>();
        manager.restoreTasks((id, next) -> restored.add(id));
        assertThat(restored).containsExactly(MINE);
    }

    @Test
    void findTaskDetailsKeepsOnlyOwnedGroups() {
        ownOnlyMine();
        TaskDetail mine = mock(TaskDetail.class);
        TaskDetail theirs = mock(TaskDetail.class);
        when(mine.getTaskId()).thenReturn(MINE);
        when(theirs.getTaskId()).thenReturn(THEIRS);
        when(delegate.findTaskDetails(any())).thenReturn(List.of(mine, theirs));

        assertThat(manager.findTaskDetails(null)).containsExactly(mine);
    }

    @Test
    void fireIsRefusedForAGroupNoLongerOwned() {
        ownOnlyMine();
        // Not owned: the SCHEDULED->RUNNING transition is refused without touching the store.
        assertThat(manager.compareAndSetTaskStatus(THEIRS, TaskStatus.SCHEDULED, TaskStatus.RUNNING))
                .isFalse();
        verify(delegate, never()).compareAndSetTaskStatus(eq(THEIRS), any(), any());
    }

    @Test
    void fireIsAllowedForAnOwnedGroup() {
        ownOnlyMine();
        when(delegate.compareAndSetTaskStatus(MINE, TaskStatus.SCHEDULED, TaskStatus.RUNNING))
                .thenReturn(true);
        assertThat(manager.compareAndSetTaskStatus(MINE, TaskStatus.SCHEDULED, TaskStatus.RUNNING))
                .isTrue();
    }

    @Test
    void nonFireTransitionsAreNeverGuarded() {
        // A transition that is not the fire transition is delegated regardless of ownership.
        when(delegate.compareAndSetTaskStatus(THEIRS, TaskStatus.RUNNING, TaskStatus.STANDBY))
                .thenReturn(true);
        assertThat(manager.compareAndSetTaskStatus(THEIRS, TaskStatus.RUNNING, TaskStatus.STANDBY))
                .isTrue();
        verify(delegate).compareAndSetTaskStatus(THEIRS, TaskStatus.RUNNING, TaskStatus.STANDBY);
    }

    @Test
    void otherCallsPassStraightThrough() {
        manager.setTaskStatus(MINE, TaskStatus.SCHEDULED);
        verify(delegate).setTaskStatus(MINE, TaskStatus.SCHEDULED);
        manager.getTaskDetail(MINE, false);
        verify(delegate).getTaskDetail(MINE, false);
    }
}
