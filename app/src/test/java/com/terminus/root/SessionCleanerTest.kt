package com.terminus.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCleanerTest {

    @Test
    fun rank_detachedViaSu_isHighest() {
        assertEquals(2, SessionCleaner.rank(Session(detached = true, detachedVia = "su")))
    }

    @Test
    fun rank_detachedViaShizuku_isMiddle() {
        assertEquals(1, SessionCleaner.rank(Session(detached = true, detachedVia = "shizuku")))
    }

    @Test
    fun rank_notDetached_usesTemplateFlags() {
        assertEquals(2, SessionCleaner.rank(Session(useSu = true)))
        assertEquals(1, SessionCleaner.rank(Session(useShizuku = true)))
        assertEquals(0, SessionCleaner.rank(Session()))
    }

    @Test
    fun rank_detachedWithoutVia_fallsBackToFlags() {
        assertEquals(2, SessionCleaner.rank(Session(detached = true, useSu = true)))
        assertEquals(0, SessionCleaner.rank(Session(detached = true)))
    }

    @Test
    fun hasHistory_falseForPristineSession() {
        assertFalse(SessionCleaner.hasHistory(Session()))
    }

    @Test
    fun hasHistory_trueWhenUsed() {
        assertTrue(SessionCleaner.hasHistory(Session(lastUsedAt = 1L)))
    }

    @Test
    fun hasHistory_trueWhenCwdCaptured() {
        assertTrue(SessionCleaner.hasHistory(Session(lastCwd = "/data")))
    }

    @Test
    fun hasHistory_trueWhenEnvCaptured() {
        assertTrue(SessionCleaner.hasHistory(Session(lastEnv = "PATH=/bin")))
    }

    @Test
    fun hasHistory_ignoresBlankCaptures() {
        assertFalse(SessionCleaner.hasHistory(Session(lastCwd = "  ", lastEnv = "")))
    }
}
