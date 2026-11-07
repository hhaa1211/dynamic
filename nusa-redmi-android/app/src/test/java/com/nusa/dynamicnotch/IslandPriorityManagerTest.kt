package com.nusa.dynamicnotch

import com.nusa.dynamicnotch.core.fsm.IslandPriorityManager
import com.nusa.dynamicnotch.models.NotchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IslandPriorityManagerTest {

    private val transitions = mutableListOf<Pair<NotchState, NotchState>>()
    private lateinit var priorityManager: IslandPriorityManager

    @Before
    fun setUp() {
        transitions.clear()
        priorityManager = IslandPriorityManager { from, to ->
            transitions.add(Pair(from, to))
        }
    }

    @Test
    fun testInitialStateIsPet() {
        assertEquals(NotchState.PET, priorityManager.currentState)
        assertEquals(NotchState.PET, priorityManager.baselineState)
    }

    @Test
    fun testPersistentStateTransitions() {
        assertTrue(priorityManager.requestState(NotchState.MUSIC))
        assertEquals(NotchState.MUSIC, priorityManager.currentState)
        assertEquals(NotchState.MUSIC, priorityManager.baselineState)

        assertTrue(priorityManager.requestState(NotchState.POMODORO))
        assertEquals(NotchState.POMODORO, priorityManager.currentState)
        assertEquals(NotchState.POMODORO, priorityManager.baselineState)
    }

    @Test
    fun testTransientStateRestoresBaseline() {
        priorityManager.requestState(NotchState.MUSIC)
        assertEquals(NotchState.MUSIC, priorityManager.baselineState)

        // Incoming Notification preempts Music
        assertTrue(priorityManager.requestState(NotchState.NOTIFICATION))
        assertEquals(NotchState.NOTIFICATION, priorityManager.currentState)
        assertEquals(NotchState.MUSIC, priorityManager.baselineState)

        // Dismiss Notification restores Music
        val restored = priorityManager.dismissTransient(NotchState.NOTIFICATION)
        assertEquals(NotchState.MUSIC, restored)
        assertEquals(NotchState.MUSIC, priorityManager.currentState)
    }

    @Test
    fun testCallPreemptsAllAndRestores() {
        priorityManager.requestState(NotchState.MUSIC)
        priorityManager.requestState(NotchState.NOTIFICATION)

        // Incoming phone call has highest priority (100)
        assertTrue(priorityManager.requestState(NotchState.CALL))
        assertEquals(NotchState.CALL, priorityManager.currentState)

        // Lower priority event cannot preempt Call
        assertFalse(priorityManager.requestState(NotchState.CHARGING))
        assertEquals(NotchState.CALL, priorityManager.currentState)
    }

    @Test
    fun testSameStateTransitionRejected() {
        priorityManager.requestState(NotchState.MUSIC)
        val initialSize = transitions.size
        assertFalse(priorityManager.requestState(NotchState.MUSIC))
        assertEquals(initialSize, transitions.size)
    }
}
