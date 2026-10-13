package com.terminus.root

import android.content.Context
import android.util.Log

object SessionCleaner {
    private const val TAG = "SessionCleaner"
    private const val FRESH_GRACE_MS = 120_000L

    data class Result(val sessions: Int, val processes: Int)

    fun rank(session: Session): Int {
        if (session.detached) {
            when (session.detachedVia) {
                "su" -> return 2
                "shizuku" -> return 1
            }
        }
        return when {
            session.useSu -> 2
            session.useShizuku -> 1
            else -> 0
        }
    }

    private fun keepMinRank(level: String): Int = when (level) {
        TermSettings.CLEAN_PRIV_ADB -> 1
        TermSettings.CLEAN_PRIV_ROOT -> 2
        else -> 0
    }

    fun hasHistory(session: Session): Boolean =
        session.lastUsedAt > 0L ||
            !session.lastCwd.isNullOrBlank() ||
            !session.lastEnv.isNullOrBlank()

    private fun backingAlive(context: Context, session: Session): Boolean {
        if (session.useShizuku || session.detachedVia == "shizuku") {
            return try {
                ShizukuPty.isRunning(context, session)
            } catch (t: Throwable) {
                false
            }
        }
        if (session.detached) return TermBridge.isAlive(context, session.id)
        return false
    }

    fun isDead(context: Context, session: Session): Boolean =
        session.detached && !backingAlive(context, session)

    private fun inUse(session: Session): Boolean =
        synchronized(shizukuPtys) { shizukuPtys.containsKey(session.id) } ||
            synchronized(terminalSessions) { terminalSessions.containsKey(session.id) }

    private fun isFresh(session: Session): Boolean =
        session.createdAt > 0L && System.currentTimeMillis() - session.createdAt < FRESH_GRACE_MS

    private fun shouldClean(context: Context, session: Session): Boolean {
        if (inUse(session)) return false
        if (TermSettings.cleanDead(context) && isDead(context, session)) return true
        if (isFresh(session)) return false
        if (TermSettings.cleanNoHistory(context) && !hasHistory(session)) return true
        val level = TermSettings.cleanPrivLevel(context)
        if (level != TermSettings.CLEAN_PRIV_OFF && rank(session) < keepMinRank(level)) return true
        return false
    }

    private fun killBacking(context: Context, session: Session): Boolean {
        var killed = false
        if (session.useShizuku || session.detachedVia == "shizuku") {
            try {
                ShizukuPty.remove(context, session)
                killed = true
            } catch (t: Throwable) {
                Log.e(TAG, "shizuku remove failed", t)
            }
        } else if (session.detached) {
            try {
                TermBridge.kill(context, session.id)
                killed = true
            } catch (t: Throwable) {
                Log.e(TAG, "termbridge kill failed", t)
            }
        }
        finishTerminalSession(session.id)
        return killed
    }

    fun cleanNow(context: Context, manager: SessionManager): Result {
        val app = context.applicationContext ?: context
        var sessions = 0
        var processes = 0
        for (session in manager.getSessions()) {
            if (!shouldClean(app, session)) continue
            if (backingAlive(app, session) || session.detached) {
                if (killBacking(app, session)) processes++
            }
            manager.removeSession(session)
            sessions++
        }
        return Result(sessions, processes)
    }

    fun maybeAutoClean(context: Context, manager: SessionManager) {
        if (!TermSettings.cleanOnStart(context)) return
        try {
            cleanNow(context, manager)
        } catch (t: Throwable) {
            Log.e(TAG, "auto clean failed", t)
        }
    }
}
