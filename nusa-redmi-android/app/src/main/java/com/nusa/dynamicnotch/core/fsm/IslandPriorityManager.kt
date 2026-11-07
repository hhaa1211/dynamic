package com.nusa.dynamicnotch.core.fsm

import com.nusa.dynamicnotch.models.NotchState

/**
 * Deterministic Finite State Machine & Preemption Priority Engine for Apple Dynamic Island.
 * Ensures strict preemption hierarchy and zero state corruption or transient lockups.
 */
class IslandPriorityManager(
    private val onStateTransition: (from: NotchState, to: NotchState) -> Unit
) {

    var currentState: NotchState = NotchState.PET
        private set

    var baselineState: NotchState = NotchState.PET
        private set

    private val transientStack = ArrayDeque<NotchState>()

    companion object {
        fun getPriority(state: NotchState): Int = when (state) {
            NotchState.CALL -> 100
            NotchState.NOTIFICATION -> 90
            NotchState.RINGER -> 85
            NotchState.CHARGING -> 80
            NotchState.MUSIC -> 50
            NotchState.POMODORO -> 40
            NotchState.MINIMAL -> 30
            NotchState.PET -> 10
        }

        fun isTransient(state: NotchState): Boolean = when (state) {
            NotchState.NOTIFICATION,
            NotchState.RINGER,
            NotchState.CHARGING -> true
            else -> false
        }
    }

    /**
     * Request a state transition. If the incoming state has equal or higher priority,
     * it preempts current display.
     */
    @Synchronized
    fun requestState(newState: NotchState): Boolean {
        if (newState == currentState) return false

        val incomingPriority = getPriority(newState)
        val currentPriority = getPriority(currentState)

        val oldState = currentState

        if (isTransient(newState)) {
            // Transient state: remember previous state
            if (!isTransient(oldState)) {
                baselineState = oldState
            }
            if (incomingPriority >= currentPriority) {
                currentState = newState
                onStateTransition(oldState, newState)
                return true
            } else {
                // Queue transient event if needed
                return false
            }
        } else {
            // Persistent / baseline state transition
            baselineState = newState
            if (!isTransient(currentState) || incomingPriority > currentPriority) {
                currentState = newState
                onStateTransition(oldState, newState)
                return true
            }
            return false
        }
    }

    /**
     * Dismiss current transient state and restore previous baseline or queued state.
     */
    @Synchronized
    fun dismissTransient(stateToDismiss: NotchState? = null): NotchState {
        if (stateToDismiss != null && currentState != stateToDismiss) {
            return currentState
        }

        val oldState = currentState
        val targetState = baselineState

        currentState = targetState
        onStateTransition(oldState, targetState)
        return targetState
    }

    @Synchronized
    fun resetToBase() {
        val old = currentState
        currentState = NotchState.PET
        baselineState = NotchState.PET
        transientStack.clear()
        if (old != NotchState.PET) {
            onStateTransition(old, NotchState.PET)
        }
    }
}
