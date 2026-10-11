package com.terminus.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

class TerminalExecutor(private val session: Session) {
    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private val isRunning = AtomicBoolean(false)
    var onOutput: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start() {
        if (isRunning.get()) return
        val proc: Process
        try {
            proc = createProcess()
        } catch (e: Exception) {
            onError?.invoke("启动终端失败: ${e.message}\n")
            return
        }
        process = proc
        isRunning.set(true)
        outputStream = DataOutputStream(proc.outputStream)
        startReader(proc)
        postStart()
    }

    private fun createProcess(): Process {
        return when {
            session.useShizuku -> createShizukuProcess()
            session.useSu -> {
                if (!isSuAvailable()) {
                    onError?.invoke("未找到 su 命令，已回退为普通 Shell\n")
                    createDirectProcess()
                } else {
                    val pb = ProcessBuilder("su")
                    pb.environment()["TERM"] = "xterm-256color"
                    pb.redirectErrorStream(true)
                    pb.start()
                }
            }
            else -> createDirectProcess()
        }
    }

    private fun createDirectProcess(): Process {
        val cmd = mutableListOf(session.shell)
        cmd.addAll(splitArgs(session.extraArgs))
        if (!session.chroot.isNullOrEmpty()) {
            cmd.add(0, session.chroot!!)
            cmd.add(0, "chroot")
        }
        val pb = ProcessBuilder(cmd)
        applyDirectory(pb)
        pb.environment()["TERM"] = "xterm-256color"
        pb.redirectErrorStream(true)
        return pb.start()
    }

    private fun createShizukuProcess(): Process {
        if (!ShizukuHelper.isAvailable()) {
            throw IllegalStateException("Shizuku 未运行，请先启动 Shizuku")
        }
        if (!ShizukuHelper.hasPermission()) {
            throw SecurityException("未授予 Shizuku 权限")
        }
        val cmd = mutableListOf<String>()
        if (!session.chroot.isNullOrEmpty()) {
            cmd.add("chroot")
            cmd.add(session.chroot!!)
        }
        cmd.add(session.shell)
        cmd.addAll(splitArgs(session.extraArgs))
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=/",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin"
        )
        val dir = session.cwd.takeIf { it.isNotEmpty() }
        return Shizuku.newProcess(cmd.toTypedArray(), env, dir)
    }

    private fun applyDirectory(pb: ProcessBuilder) {
        if (session.cwd.isNotEmpty()) {
            val cwdFile = File(session.cwd)
            if (cwdFile.exists() && cwdFile.isDirectory) {
                pb.directory(cwdFile)
            }
        }
    }

    private fun postStart() {
        if (session.useSu && !session.useShizuku) {
            when {
                !session.chroot.isNullOrEmpty() ->
                    execute("chroot ${escapePath(session.chroot!!)} ${session.shell}")
                session.cwd.isNotEmpty() ->
                    execute("cd ${escapePath(session.cwd)} 2>/dev/null")
            }
        }
    }

    private fun startReader(proc: Process) {
        Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null && isRunning.get()) {
                        onOutput?.invoke("$line\n")
                    }
                }
            } catch (e: Exception) {
                Log.e("TerminalExecutor", "Error reading output", e)
            }
        }.start()
    }

    private fun isSuAvailable(): Boolean {
        return try {
            ProcessBuilder("which", "su").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun execute(command: String) {
        if (!isRunning.get() || outputStream == null) return
        try {
            outputStream!!.writeBytes("$command\n")
            outputStream!!.flush()
        } catch (e: Exception) {
            Log.e("TerminalExecutor", "Error executing command", e)
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            outputStream?.writeBytes("exit\n")
            outputStream?.flush()
        } catch (e: Exception) {
        }
        process?.destroy()
        outputStream?.close()
    }

    fun isRunning(): Boolean = isRunning.get()

    private fun splitArgs(args: String): List<String> =
        args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun escapePath(path: String): String =
        path.replace("\\", "\\\\").replace(" ", "\\ ")
}
