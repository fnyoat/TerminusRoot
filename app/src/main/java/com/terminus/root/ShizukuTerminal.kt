package com.terminus.root

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

class ShizukuTerminal(
    context: Context,
    private val session: Session,
    private val onOutput: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var service: IShellService? = null

    private val callback = object : IShellCallback.Stub() {
        override fun onOutput(data: String?) {
            data?.let(onOutput)
        }

        override fun onExit(exitCode: Int) {
            onOutput("\n[进程已退出，状态码 $exitCode]\n")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val shell = IShellService.Stub.asInterface(binder)
            service = shell
            try {
                shell.start(
                    if (session.chroot.isNullOrEmpty()) {
                        ShellLocator.resolve(session.shell)
                    } else {
                        session.shell
                    },
                    splitArgs(session.extraArgs).toTypedArray(),
                    ENVIRONMENT,
                    session.cwd.takeIf { it.isNotEmpty() },
                    session.chroot.takeIf { it?.isNotEmpty() == true },
                    callback
                )
            } catch (t: Throwable) {
                Log.e(TAG, "start shell failed", t)
                onError("启动 Shizuku Shell 失败: ${t.message}\n")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            onOutput("\n[Shizuku 用户服务已断开]\n")
        }
    }

    private val userServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(context, RemoteShellService::class.java))
            .daemon(false)
            .version(1)
            .processNameSuffix("shell")

    fun start() {
        if (!ShizukuHelper.isAvailable()) {
            onError("Shizuku 未运行，请先启动 Shizuku\n")
            return
        }
        if (!ShizukuHelper.hasPermission()) {
            onError("未授予 Shizuku 权限\n")
            return
        }
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed", t)
            onError("绑定 Shizuku 用户服务失败: ${t.message}\n")
        }
    }

    fun execute(command: String) {
        val shell = service
        if (shell == null) {
            onError("Shizuku Shell 尚未就绪\n")
            return
        }
        try {
            shell.write("$command\n")
        } catch (t: Throwable) {
            Log.e(TAG, "write failed", t)
            onError("发送命令失败: ${t.message}\n")
        }
    }

    fun stop() {
        try {
            service?.exit()
        } catch (t: Throwable) {
            Log.e(TAG, "exit failed", t)
        }
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (t: Throwable) {
            Log.e(TAG, "unbindUserService failed", t)
        }
        service = null
    }

    fun isRunning(): Boolean = service != null

    private fun splitArgs(args: String): List<String> =
        args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    companion object {
        private const val TAG = "ShizukuTerminal"
        private val ENVIRONMENT = arrayOf(
            "TERM=xterm-256color",
            "HOME=/",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin"
        )
    }
}
