package com.terminus.root

import android.content.Context
import java.io.File

object TermBridge {
    fun binPath(context: Context): String =
        File(context.applicationInfo.nativeLibraryDir, "libtermbridge.so").absolutePath

    fun available(context: Context): Boolean = File(binPath(context)).exists()

    fun sockPath(context: Context, sessionId: String): String {
        val dir = File(context.filesDir, "sock")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$sessionId.sock").absolutePath
    }

    fun isAlive(context: Context, sessionId: String): Boolean {
        if (!available(context)) return false
        return try {
            val p = ProcessBuilder(binPath(context), "probe", sockPath(context, sessionId))
                .redirectErrorStream(true)
                .start()
            p.inputStream.readBytes()
            p.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun kill(context: Context, sessionId: String) {
        if (!available(context)) return
        try {
            val p = ProcessBuilder(binPath(context), "kill", sockPath(context, sessionId))
                .redirectErrorStream(true)
                .start()
            p.inputStream.readBytes()
            p.waitFor()
        } catch (e: Exception) {
        }
    }

    fun launchDaemon(
        context: Context,
        sessionId: String,
        argv: Array<String>,
        cwd: String,
        env: Array<String>,
        useRoot: Boolean
    ): Boolean {
        if (!available(context)) return false
        val bin = binPath(context)
        val sock = sockPath(context, sessionId)
        val daemonArgs = mutableListOf(bin, "daemon", sock, cwd, "24", "80")
        daemonArgs.addAll(argv)
        val cmd: List<String>
        if (useRoot) {
            val sb = StringBuilder()
            for (e in env) {
                val idx = e.indexOf('=')
                if (idx > 0) {
                    sb.append(e.substring(0, idx))
                    sb.append('=')
                    sb.append(shQuote(e.substring(idx + 1)))
                    sb.append(' ')
                }
            }
            sb.append("setsid")
            for (a in daemonArgs) {
                sb.append(' ')
                sb.append(shQuote(a))
            }
            cmd = listOf(Access.suPath(), "-c", sb.toString())
        } else {
            cmd = mutableListOf("/system/bin/setsid").apply { addAll(daemonArgs) }
        }
        return try {
            val pb = ProcessBuilder(cmd)
            val penv = pb.environment()
            penv.clear()
            for (e in env) {
                val idx = e.indexOf('=')
                if (idx > 0) penv[e.substring(0, idx)] = e.substring(idx + 1)
            }
            pb.redirectErrorStream(true)
            pb.redirectOutput(File(context.filesDir, "daemon-$sessionId.log"))
            pb.start()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun attachCommand(context: Context, sessionId: String): Array<String> =
        arrayOf(binPath(context), "attach", sockPath(context, sessionId))
}
