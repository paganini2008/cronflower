package com.github.cronsmith.springapp.scheduler.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.github.cronsmith.springapp.scheduler.TaskStatus;

/**
 * 
 * Pins down the lifecycle transition table: the piece that keeps two threads from driving one task
 * into inconsistent states.
 * 
 * @Description: TaskStatusTests
 * @Author: Fred Feng
 * @Date: 24/08/2026
 * @Version 1.0.0
 */
public class TaskStatusTests {

    @Test
    public void testStayingPutIsAlwaysAllowed() {
        for (TaskStatus status : TaskStatus.values()) {
            assertTrue(status.canTransitionTo(status), status + " to itself");
        }
    }

    @Test
    public void testTerminalStatesGoNowhere() {
        assertTrue(TaskStatus.FINISHED.isTerminal());
        assertTrue(TaskStatus.CANCELED.isTerminal());
        assertFalse(TaskStatus.FINISHED.canTransitionTo(TaskStatus.SCHEDULED));
        assertFalse(TaskStatus.CANCELED.canTransitionTo(TaskStatus.STANDBY));
    }

    @Test
    public void testAnyLiveStateCanBeCanceled() {
        assertTrue(TaskStatus.STANDBY.canTransitionTo(TaskStatus.CANCELED));
        assertTrue(TaskStatus.SCHEDULED.canTransitionTo(TaskStatus.CANCELED));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.CANCELED));
        assertTrue(TaskStatus.PAUSED.canTransitionTo(TaskStatus.CANCELED));
    }

    @Test
    public void testNormalCycle() {
        assertTrue(TaskStatus.NONE.canTransitionTo(TaskStatus.STANDBY));
        assertTrue(TaskStatus.STANDBY.canTransitionTo(TaskStatus.SCHEDULED));
        assertTrue(TaskStatus.SCHEDULED.canTransitionTo(TaskStatus.RUNNING));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.STANDBY));
        assertTrue(TaskStatus.RUNNING.canTransitionTo(TaskStatus.FINISHED));
    }

    @Test
    public void testIllegalJumps() {
        assertFalse(TaskStatus.NONE.canTransitionTo(TaskStatus.RUNNING));
        assertFalse(TaskStatus.STANDBY.canTransitionTo(TaskStatus.RUNNING));
        assertFalse(TaskStatus.SCHEDULED.canTransitionTo(TaskStatus.NONE));
        assertFalse(TaskStatus.PAUSED.canTransitionTo(TaskStatus.RUNNING));
    }

    @Test
    public void testPauseAndResume() {
        assertTrue(TaskStatus.SCHEDULED.canTransitionTo(TaskStatus.PAUSED));
        assertTrue(TaskStatus.PAUSED.canTransitionTo(TaskStatus.STANDBY));
        assertTrue(TaskStatus.PAUSED.canTransitionTo(TaskStatus.SCHEDULED));
    }

    @Test
    public void testUnavailable() {
        assertTrue(TaskStatus.FINISHED.isUnavailable());
        assertTrue(TaskStatus.CANCELED.isUnavailable());
        assertTrue(TaskStatus.PAUSED.isUnavailable());
        assertFalse(TaskStatus.SCHEDULED.isUnavailable());
        assertFalse(TaskStatus.STANDBY.isUnavailable());
    }

    @Test
    public void testForNameAcceptsEitherCase() {
        assertEquals(TaskStatus.SCHEDULED, TaskStatus.forName("scheduled"));
        assertEquals(TaskStatus.SCHEDULED, TaskStatus.forName(" SCHEDULED "));
        org.junit.jupiter.api.Assertions.assertNull(TaskStatus.forName(null));
    }

    @Test
    public void testStoredValueIsTheName() {
        assertEquals("RUNNING", TaskStatus.RUNNING.getValue());
        assertEquals("RUNNING", TaskStatus.RUNNING.getRepr());
    }

    @Test
    public void testNullTargetIsRejected() {
        assertFalse(TaskStatus.STANDBY.canTransitionTo(null));
    }

}
