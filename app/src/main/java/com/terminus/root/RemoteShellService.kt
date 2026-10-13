package com.terminus.root

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Binder
import android.os.IBinder
import android.system.Os
import android.util.Log
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class RemoteShellService : IShellService.Stub {
    @Volatile
    private var appContext: Context? = null

    constructor() : super()

    constructor(context: Context) : super() {
        appContext = context.applicationContext ?: context
    }

    private var process: Process? = null
    private var outputStream: DataOutputStream? = null
    private var callback: IShellCallback? = null
    private val running = AtomicBoolean(false)

    @Volatile
    private var detached = false

    @Volatile
    private var deathRecipient: IBinder.DeathRecipient? = null

    @Volatile
    private var deathBinder: IBinder? = null

    private val ptyLock = Any()
    private var ptyMode = false
    private var ptyEnded = false
    private var ctrlPath: String? = null
    private var binPath: String? = null
    private var ptyBuffer = ByteArrayOutputStream()

    private fun ensureTrusted(): Boolean {
        val caller = Binder.getCallingUid()
        if (caller == android.os.Process.myUid()) return true
        val ctx = appContext
        if (ctx == null) {
            Log.w(TAG, "no context; cannot verify caller uid $caller")
            return true
        }
        return try {
            val pkgs = ctx.packageManager.getPackagesForUid(caller)
            val ok = pkgs != null && pkgs.contains(ctx.packageName)
            if (!ok) Log.e(TAG, "rejected binder call from uid $caller")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "caller check failed", t)
            false
        }
    }

    override fun start(
        shell: String?,
        args: Array<String>?,
        env: Array<String>?,
        dir: String?,
        chroot: String?,
        cb: IShellCallback?
    ) {
        if (!ensureTrusted()) return
        destroyInternal()
        ptyMode = false
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

    override fun startPty(
        bin: ByteArray?,
        cwd: String?,
        rows: Int,
        cols: Int,
        shell: String?,
        args: Array<String>?,
        env: Array<String>?,
        cb: IShellCallback?
    ) {
        if (!ensureTrusted()) return
        synchronized(ptyLock) {
            if (ptyMode && running.get() && process != null) {
                callback = cb
                linkCallbackDeath(cb)
                val replay = ptyBuffer.toByteArray()
                if (replay.isNotEmpty()) {
                    try {
                        cb?.onData(replay)
                    } catch (_: Throwable) {
                    }
                }
                if (ptyEnded) {
                    try {
                        cb?.onExit(0)
                    } catch (_: Throwable) {
                    }
                }
                return
            }
        }

        val keepDetached = detached
        destroyInternal()
        detached = keepDetached
        synchronized(ptyLock) {
            ptyMode = true
            ptyEnded = false
            ptyBuffer = ByteArrayOutputStream()
        }
        callback = cb
        linkCallbackDeath(cb)

        val exePath: String
        try {
            val f = File("/data/local/tmp", "termbridge_pty_${android.os.Process.myPid()}_${System.currentTimeMillis()}.bin")
            f.writeBytes(bin ?: ByteArray(0))
            f.setExecutable(true, false)
            f.setReadable(true, false)
            try {
                Os.chmod(f.absolutePath, MODE_PRIVATE)
            } catch (_: Throwable) {
            }
            synchronized(ptyLock) { binPath = f.absolutePath }
            exePath = f.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "write bin failed", t)
            try {
                cb?.onExit(-1)
            } catch (_: Throwable) {
            }
            running.set(false)
            return
        }

        val ctrl = "/data/local/tmp/termbridge_ctrl_${android.os.Process.myPid()}_${System.currentTimeMillis()}"
        ctrlPath = ctrl

        val shellRcPath = try {
            val rc = File("/data/local/tmp", "terminus_shellrc")
            rc.writeBytes(SHELL_RC.toByteArray())
            rc.setReadable(true, false)
            try {
                Os.chmod(rc.absolutePath, MODE_PRIVATE)
            } catch (_: Throwable) {
            }
            rc.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "write shellrc failed", t)
            null
        }

        val command = mutableListOf(
            exePath, "pty", cwd ?: "/", rows.toString(), cols.toString(),
            shell ?: "/system/bin/sh"
        )
        args?.forEach { command.add(it) }

        val pb = ProcessBuilder(command)
        pb.environment()["TERMBRIDGE_CTRL"] = ctrl
        env?.forEach { entry ->
            val index = entry.indexOf('=')
            if (index > 0) {
                pb.environment()[entry.substring(0, index)] = entry.substring(index + 1)
            }
        }
        pb.environment()["TERM"] = "xterm-256color"
        pb.environment()["CLICOLOR"] = "1"
        if (shellRcPath != null) pb.environment()["ENV"] = shellRcPath
        pb.redirectErrorStream(true)

        try {
            val proc = pb.start()
            process = proc
            outputStream = DataOutputStream(proc.outputStream)
            running.set(true)
            startByteReader(proc)
        } catch (t: Throwable) {
            Log.e(TAG, "startPty failed", t)
            try {
                cb?.onExit(-1)
            } catch (_: Throwable) {
            }
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

    private fun startByteReader(proc: Process) {
        Thread {
            val buf = ByteArray(8192)
            try {
                val ins = proc.inputStream
                while (running.get()) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    synchronized(ptyLock) {
                        if (ptyBuffer.size() > MAX_BUFFER) {
                            val keep = ptyBuffer.toByteArray()
                            val from = keep.size - MAX_BUFFER / 2
                            ptyBuffer = ByteArrayOutputStream(MAX_BUFFER)
                            ptyBuffer.write(keep, from, keep.size - from)
                        }
                        ptyBuffer.write(buf, 0, n)
                    }
                    try {
                        callback?.onData(buf.copyOf(n))
                    } catch (_: Throwable) {
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "pty read failed", t)
            }
            running.set(false)
            synchronized(ptyLock) {
                ptyEnded = true
            }
            try {
                callback?.onExit(0)
            } catch (_: Throwable) {
            }
        }.start()
    }

    override fun write(data: String?) {
        if (!ensureTrusted()) return
        if (data.isNullOrEmpty()) return
        try {
            outputStream?.write(data.toByteArray())
            outputStream?.flush()
        } catch (t: Throwable) {
            Log.e(TAG, "write failed", t)
        }
    }

    override fun writeData(data: ByteArray?) {
        if (!ensureTrusted()) return
        if (data == null || data.isEmpty()) return
        try {
            outputStream?.write(data)
            outputStream?.flush()
        } catch (t: Throwable) {
            Log.e(TAG, "writeData failed", t)
        }
    }

    override fun resize(rows: Int, cols: Int) {
        if (!ensureTrusted()) return
        val path = ctrlPath ?: return
        var attempt = 0
        while (attempt < 40) {
            try {
                val s = LocalSocket()
                s.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                s.outputStream.write("$rows $cols\n".toByteArray())
                s.outputStream.flush()
                s.close()
                return
            } catch (t: Throwable) {
                attempt++
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                }
            }
        }
        Log.e(TAG, "resize failed after retries")
    }

    override fun waitFor(): Int {
        if (!ensureTrusted()) return -1
        val proc = process ?: return -1
        return try {
            proc.waitFor()
        } catch (t: InterruptedException) {
            -1
        }
    }

    override fun exit() {
        if (!ensureTrusted()) return
        destroyInternal()
    }

    override fun destroy() {
        if (!ensureTrusted()) return
        destroyInternal()
    }

    override fun setDetached(detached: Boolean) {
        if (!ensureTrusted()) return
        this.detached = detached
    }

    private fun linkCallbackDeath(cb: IShellCallback?) {
        unlinkCallbackDeath()
        val binder = cb?.asBinder() ?: return
        val recipient = IBinder.DeathRecipient {
            if (!detached) {
                destroyInternal()
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                callback = null
            }
        }
        try {
            binder.linkToDeath(recipient, 0)
            deathRecipient = recipient
            deathBinder = binder
        } catch (t: Throwable) {
            Log.e(TAG, "linkToDeath failed", t)
        }
    }

    private fun unlinkCallbackDeath() {
        val binder = deathBinder
        val recipient = deathRecipient
        if (binder != null && recipient != null) {
            try {
                binder.unlinkToDeath(recipient, 0)
            } catch (_: Throwable) {
            }
        }
        deathRecipient = null
        deathBinder = null
    }

    private fun destroyInternal() {
        unlinkCallbackDeath()
        val wasPty = ptyMode
        running.set(false)
        if (wasPty) {
            ctrlPath?.let { path ->
                try {
                    val s = LocalSocket()
                    s.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                    s.outputStream.write("kill\n".toByteArray())
                    s.outputStream.flush()
                    s.close()
                } catch (_: Throwable) {
                }
            }
        } else {
            try {
                outputStream?.write("exit\n".toByteArray())
                outputStream?.flush()
            } catch (_: Throwable) {
            }
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
        detached = false
        synchronized(ptyLock) {
            ptyMode = false
            ptyEnded = false
            ctrlPath = null
            binPath?.let { path ->
                try {
                    File(path).delete()
                } catch (_: Throwable) {
                }
            }
            binPath = null
            ptyBuffer = ByteArrayOutputStream()
        }
    }

    companion object {
        private const val TAG = "RemoteShellService"
        private const val MAX_BUFFER = 256 * 1024
        private const val MODE_PRIVATE = 448
        private const val SHELL_RC = """
[ -r /system/etc/mkshrc ] && . /system/etc/mkshrc
alias ls='ls --color=auto'
alias ll='ls -l'
alias la='ls -a'
alias l='ls -CF'
alias grep='grep --color=auto'
alias egrep='grep -E --color=auto'
alias fgrep='grep -F --color=auto'
export CLICOLOR=1
"""
    }
}
