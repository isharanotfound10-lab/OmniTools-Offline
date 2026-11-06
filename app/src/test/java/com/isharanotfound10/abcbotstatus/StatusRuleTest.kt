package com.isharanotfound10.abcbotstatus

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
}
