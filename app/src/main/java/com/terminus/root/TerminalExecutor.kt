package com.terminus.root

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class TerminalExecutor(private val context: Context, private val session: Session) {
    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private val isRunning = AtomicBoolean(false)
    private var shizukuTerminal: ShizukuTerminal? = null
    var onOutput: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun start() {
        if (session.useShizuku) {
            startShizuku()
            return
        }
        if (isRunning.get()) return
        val proc: Process
        try {
            proc = createLocalProcess()
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

    private fun startShizuku() {
        if (shizukuTerminal != null) return
        val terminal = ShizukuTerminal(
            context,
            session,
            onOutput = { output -> onOutput?.invoke(output) },
            onError = { error -> onError?.invoke(error) }
        )
        shizukuTerminal = terminal
        terminal.start()
    }

    private fun createLocalProcess(): Process {
        if (session.useSu) {
            if (!isSuAvailable()) {
                onError?.invoke("未找到 su 命令，已回退为普通 Shell\n")
            } else {
                val cmd = mutableListOf("su")
                val uid = session.uid.trim()
                val gid = session.gid.trim()
                if (uid.isNotEmpty() && uid != "0") {
                    cmd += "--uid"
                    cmd += uid
                }
                if (gid.isNotEmpty()) {
                    cmd += "--gid"
                    cmd += gid
                }
                val pb = ProcessBuilder(cmd)
                pb.environment()["TERM"] = "xterm-256color"
                pb.redirectErrorStream(true)
                return pb.start()
            }
        }
        if (Access.needsProfile(session.uid, session.gid)) {
            onError?.invoke("指定了 UID/GID，但非 Root Shell，无法切换用户\n")
        }
        val shell = if (session.chroot.isNullOrEmpty()) ShellLocator.resolve(session.shell) else session.shell
        val cmd = mutableListOf(shell)
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
                Log.e(TAG, "Error reading output", e)
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
        val shizuku = shizukuTerminal
        if (shizuku != null) {
            shizuku.execute(command)
            return
        }
        if (!isRunning.get() || outputStream == null) return
        try {
            outputStream!!.writeBytes("$command\n")
            outputStream!!.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command", e)
        }
    }

    fun stop() {
        shizukuTerminal?.stop()
        shizukuTerminal = null
        isRunning.set(false)
        try {
            outputStream?.writeBytes("exit\n")
            outputStream?.flush()
        } catch (e: Exception) {
        }
        process?.destroy()
        outputStream?.close()
    }

    fun isRunning(): Boolean = isRunning.get() || shizukuTerminal?.isRunning() == true

    private fun splitArgs(args: String): List<String> =
        args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun escapePath(path: String): String =
        path.replace("\\", "\\\\").replace(" ", "\\ ")

    companion object {
        private const val TAG = "TerminalExecutor"
    }
}
