package com.terminus.root

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.IBinder
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import rikka.shizuku.Shizuku

class ShizukuPty(context: Context, private val session: Session) {
    private val appContext = context.applicationContext
    private val lock = Any()

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var running = false

    @Volatile
    private var client: LocalSocket? = null

    @Volatile
    private var pendingRows = 0

    @Volatile
    private var pendingCols = 0

    private var server: LocalServerSocket? = null
    private var acceptThread: Thread? = null
    private var outBuf = java.io.ByteArrayOutputStream()

    val socketName: String

    init {
        socketName = "terminus_pty_${session.id.filter { it.isLetterOrDigit() }.take(24)}"
    }

    private val callback = object : IShellCallback.Stub() {
        override fun onOutput(data: String?) {}

        override fun onExit(exitCode: Int) {}

        override fun onData(data: ByteArray?) {
            val d = data ?: return
            try {
                synchronized(lock) {
                    val c = client
                    if (c == null) {
                        if (outBuf.size() > MAX_OUT) {
                            val keep = outBuf.toByteArray()
                            val from = keep.size - MAX_OUT / 2
                            outBuf = java.io.ByteArrayOutputStream(MAX_OUT)
                            outBuf.write(keep, from, keep.size - from)
                        }
                        outBuf.write(d)
                        return
                    }
                    c.outputStream.write(d)
                    c.outputStream.flush()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IShellService.Stub.asInterface(binder)
            service = svc
            try {
                val bin = File(TermBridge.binPath(appContext)).readBytes()
                val argv = buildArgv()
                svc.startPty(
                    bin,
                    session.lastCwd ?: session.cwd.ifEmpty { "/" },
                    24,
                    80,
                    argv.first(),
                    argv.drop(1).toTypedArray(),
                    buildEnv(),
                    callback
                )
                svc.setDetached(session.detached || TermSettings.sessionPersist(appContext))
                if (pendingRows > 0 && pendingCols > 0) {
                    svc.resize(pendingRows, pendingCols)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "startPty failed", t)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val userServiceArgs = argsFor(appContext, session)

    fun start() {
        try {
            val s = LocalServerSocket(socketName)
            server = s
            running = true
            acceptThread = Thread {
                while (running) {
                    val c = try {
                        s.accept()
                    } catch (t: Throwable) {
                        break
                    }
                    synchronized(lock) {
                        try {
                            client?.close()
                        } catch (_: Throwable) {
                        }
                        client = c
                        if (outBuf.size() > 0) {
                            try {
                                c.outputStream.write(outBuf.toByteArray())
                                c.outputStream.flush()
                            } catch (_: Throwable) {
                            }
                            outBuf = java.io.ByteArrayOutputStream()
                        }
                    }
                    relay(c)
                    synchronized(lock) {
                        if (client === c) client = null
                    }
                }
            }.also { it.start() }
        } catch (t: Throwable) {
            Log.e(TAG, "create server failed", t)
        }
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
        }
    }

    private fun relay(c: LocalSocket) {
        try {
            val ins = BufferedInputStream(c.inputStream)
            val header = ByteArray(5)
            while (running) {
                if (!readFully(ins, header, 5)) break
                when (header[0].toInt() and 0xff) {
                    0 -> {
                        val len = (header[1].toInt() and 0xff) or
                            ((header[2].toInt() and 0xff) shl 8) or
                            ((header[3].toInt() and 0xff) shl 16) or
                            ((header[4].toInt() and 0xff) shl 24)
                        if (len <= 0 || len > (1 shl 20)) break
                        val payload = ByteArray(len)
                        if (!readFully(ins, payload, len)) break
                        service?.writeData(payload)
                    }
                    1 -> {
                        val rows = (header[1].toInt() and 0xff) or ((header[2].toInt() and 0xff) shl 8)
                        val cols = (header[3].toInt() and 0xff) or ((header[4].toInt() and 0xff) shl 8)
                        if (rows > 0 && cols > 0) {
                            pendingRows = rows
                            pendingCols = cols
                            service?.resize(rows, cols)
                        }
                    }
                    else -> break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "relay ended", t)
        }
    }

    private fun readFully(ins: java.io.InputStream, buf: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val n = ins.read(buf, off, len - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun buildArgv(): List<String> {
        val list = mutableListOf<String>()
        if (session.chroot.isNullOrEmpty()) {
            list += ShellLocator.resolve(session.shell)
        } else {
            list += "chroot"
            list += session.chroot!!
            list += session.shell
        }
        list += session.extraArgs.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return list
    }

    private fun buildEnv(): Array<String> {
        val map = LinkedHashMap<String, String>()
        map["TERM"] = "xterm-256color"
        map["HOME"] = "/"
        map["PATH"] = "/sbin:/system/sbin:/system/bin:/system/xbin"
        session.lastEnv?.split('\n')?.forEach { entry ->
            val i = entry.indexOf('=')
            if (i > 0) map[entry.substring(0, i)] = entry.substring(i + 1)
        }
        return map.map { "${it.key}=${it.value}" }.toTypedArray()
    }

    fun markDetached() {
        try {
            service?.setDetached(true)
        } catch (_: Throwable) {
        }
    }

    fun close() {
        running = false
        synchronized(lock) {
            try {
                client?.close()
            } catch (_: Throwable) {
            }
            client = null
        }
        try {
            server?.close()
        } catch (_: Throwable) {
        }
        try {
            service?.exit()
        } catch (_: Throwable) {
        }
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (_: Throwable) {
        }
        service = null
    }

    companion object {
        private const val TAG = "ShizukuPty"
        private const val MAX_OUT = 256 * 1024

        private fun argsFor(context: Context, session: Session): Shizuku.UserServiceArgs =
            Shizuku.UserServiceArgs(ComponentName(context.applicationContext, RemoteShellService::class.java))
                .daemon(true)
                .version(3)
                .tag("pty_${session.id}")
                .processNameSuffix("pty_${session.id.filter { it.isLetterOrDigit() }.take(20)}")

        private val probeConn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        fun isRunning(context: Context, session: Session): Boolean {
            if (!Shizuku.pingBinder()) return false
            return try {
                Shizuku.peekUserService(argsFor(context, session), probeConn) >= 0
            } catch (_: Throwable) {
                false
            }
        }

        fun remove(context: Context, session: Session) {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, RemoteShellService::class.java)
            )
                .daemon(true)
                .version(3)
                .tag("pty_${session.id}")
                .processNameSuffix("pty_${session.id.filter { it.isLetterOrDigit() }.take(20)}")
            try {
                Shizuku.unbindUserService(args, null, true)
            } catch (_: Throwable) {
            }
        }
    }
}
