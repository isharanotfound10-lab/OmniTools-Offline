package com.isharanotfound10.abcbotstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusRuleTest {
    private fun showRerun(status: String) = status == "completed"
    @Test fun rerunHiddenWhileActiveOrQueued() {
        assertFalse(showRerun("in_progress"))
        assertFalse(showRerun("queued"))
        assertFalse(showRerun("waiting"))
    }
    @Test fun rerunVisibleAfterCompletion() {
        assertTrue(showRerun("completed"))
    }

    @Test fun activeStepDisplaysRunning() {
        assertEquals("Running", stepStatusLabel("in_progress"))
    }

    @Test fun queuedStepDisplaysQueued() {
        assertEquals("Queued", stepStatusLabel("queued"))
        assertEquals("Queued", stepStatusLabel("waiting"))
    }

    @Test fun completedStepDisplaysConclusion() {
        assertEquals("Success", stepStatusLabel("success"))
        assertEquals("Failed", stepStatusLabel("failure"))
    }

    @Test fun missingStepStateDoesNotDisplayNull() {
        assertEquals("Waiting", stepStatusLabel("null"))
        assertEquals("Waiting", stepStatusLabel("unknown"))
    }
}
