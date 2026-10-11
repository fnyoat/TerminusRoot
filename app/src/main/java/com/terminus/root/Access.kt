package com.terminus.root

import java.io.File

object Access {
    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/system/bin/.ext/.su",
        "/magisk/.core/bin/su",
        "/data/adb/magisk/busybox",
        "/su/bin/su"
    )

    fun suAvailable(): Boolean {
        if (SU_PATHS.any { File(it).exists() }) return true
        if (testCommand("which", "su")) return true
        return testShell("su 0 id")
    }

    fun shizukuReady(): Boolean = try {
        ShizukuHelper.isAvailable() && ShizukuHelper.hasPermission()
    } catch (t: Throwable) {
        false
    }

    fun needsProfile(uid: String, gid: String): Boolean =
        (uid.isNotBlank() && uid.trim() != "0") || gid.isNotBlank()

    fun canUseProfile(uid: String, gid: String): Boolean =
        !needsProfile(uid, gid) || suAvailable()

    fun templateAvailable(template: Template): Boolean = when {
        template.useShizuku -> shizukuReady() && canUseProfile(template.uid, template.gid)
        template.useSu -> suAvailable() && canUseProfile(template.uid, template.gid)
        else -> canUseProfile(template.uid, template.gid)
    }

    fun sessionProfileBlocked(session: Session): Boolean =
        needsProfile(session.uid, session.gid) && !canUseProfile(session.uid, session.gid)

    fun templateProfileBlocked(template: Template): Boolean =
        needsProfile(template.uid, template.gid) && !canUseProfile(template.uid, template.gid)

    private fun testCommand(vararg cmd: String): Boolean = try {
        ProcessBuilder(*cmd).start().waitFor() == 0
    } catch (t: Throwable) {
        false
    }

    private fun testShell(script: String): Boolean = try {
        val pb = ProcessBuilder("/system/bin/sh", "-c", script)
        pb.redirectErrorStream(true)
        pb.start().waitFor() == 0
    } catch (t: Throwable) {
        false
    }
}