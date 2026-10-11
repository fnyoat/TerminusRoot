package com.terminus.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class RemoteShellService : IShellService.Stub() {
    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private var callback: IShellCallback? = null
    private val running = AtomicBoolean(false)

    override fun start(
        shell: String?,
        args: Array<String>?,
        env: Array<String>?,
        dir: String?,
        chroot: String?,
        cb: IShellCallback?
    ) {
        destroyInternal()
        callback = cb

        val command = mutableListOf<String>()
        if (!chroot.isNullOrEmpty()) {
            command.add("chroot")
            command.add(chroot)
        }
        command.add(shell ?: "/system/bin/sh")
        args?.forEach { command.add(it) }

        val pb = ProcessBuilder(command)
        env?.forEach { entry ->
            val index = entry.indexOf('=')
            if (index > 0) {
                pb.environment()[entry.substring(0, index)] = entry.substring(index + 1)
            }
        }
        if (!dir.isNullOrEmpty()) {
            val dirFile = File(dir)
            if (dirFile.isDirectory) {
                pb.directory(dirFile)
            }
        }
        pb.redirectErrorStream(true)

        try {
            val proc = pb.start()
            process = proc
            outputStream = DataOutputStream(proc.outputStream)
            running.set(true)
            startReader(proc)
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            cb?.onOutput("启动 Shell 失败: ${t.message}\n")
            cb?.onExit(-1)
            running.set(false)
        }
    }

    private fun startReader(proc: Process) {
        Thread {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    val buffer = CharArray(4096)
                    while (running.get()) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        callback?.onOutput(String(buffer, 0, count))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "read failed", t)
            }
            running.set(false)
            val code = try {
                proc.waitFor()
            } catch (t: InterruptedException) {
                -1
            }
            try {
                callback?.onExit(code)
            } catch (t: Throwable) {
                Log.e(TAG, "onExit failed", t)
            }
        }.start()
    }

    override fun write(data: String?) {
        if (data.isNullOrEmpty()) return
        try {
            outputStream?.write(data.toByteArray())
            outputStream?.flush()
        } catch (t: Throwable) {
            Log.e(TAG, "write failed", t)
        }
    }

    override fun waitFor(): Int {
        val proc = process ?: return -1
        return try {
            proc.waitFor()
        } catch (t: InterruptedException) {
            -1
        }
    }

    override fun exit() {
        destroyInternal()
    }

    override fun destroy() {
        destroyInternal()
    }

    private fun destroyInternal() {
        running.set(false)
        try {
            outputStream?.write("exit\n".toByteArray())
            outputStream?.flush()
        } catch (_: Throwable) {
        }
        try {
            outputStream?.close()
        } catch (_: Throwable) {
        }
        try {
            process?.destroy()
        } catch (_: Throwable) {
        }
        process = null
        outputStream = null
        callback = null
    }

    companion object {
        private const val TAG = "RemoteShellService"
    }
}
