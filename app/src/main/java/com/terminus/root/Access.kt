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

    fun templateAvailable(template: Template): Boolean = when {
        template.useShizuku -> shizukuReady()
        template.useSu -> suAvailable()
        else -> true
    }

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