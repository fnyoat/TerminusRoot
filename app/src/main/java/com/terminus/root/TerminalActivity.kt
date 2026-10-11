package com.terminus.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

class TerminalActivity : AppCompatActivity() {
    private lateinit var textOutput: TextView
    private lateinit var editCommand: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var termView: TerminalView
    private lateinit var layoutInput: View
    private var executor: TerminalExecutor? = null
    private lateinit var session: Session
    private var terminalSession: TerminalSession? = null
    private var permissionFlowStarted = false
    private var terminalStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        session = intent.getSerializableExtra("session") as Session
        InsetsUtil.apply(this)

        textOutput = findViewById(R.id.textOutput)
        editCommand = findViewById(R.id.editCommand)
        scrollView = findViewById(R.id.scrollView)
        termView = findViewById(R.id.termView)
        layoutInput = findViewById(R.id.layoutInput)
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        headerTitle.text = session.name
        val color = TermSettings.color(this)
        headerTitle.setTextColor(color)

        if (session.useShizuku) {
            setupShizukuMode(color)
        } else {
            setupTerminalMode(color)
        }
    }

    private fun setupTerminalMode(color: Int) {
        termView.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        layoutInput.visibility = View.GONE

        termView.setTerminalViewClient(viewClient)
        termView.setTextSize(12)

        val launch = buildLaunchCommand()
        val terminal = TerminalSession(
            launch.first, launch.third, launch.second, BASE_ENV, 2000, sessionClient
        )
        terminalSession = terminal
        termView.attachSession(terminal)
        termView.requestFocus()
    }

    private fun buildLaunchCommand(): Triple<String, Array<String>, String> {
        val shell =
            if (session.chroot.isNullOrEmpty()) ShellLocator.resolve(session.shell) else session.shell
        if (session.useSu) {
            val suArgs = mutableListOf<String>()
            val uid = session.uid.trim()
            val gid = session.gid.trim()
            if (uid.isNotEmpty() && uid != "0") {
                suArgs += "--uid"
                suArgs += uid
            }
            if (gid.isNotEmpty() && gid != "0") {
                suArgs += "--gid"
                suArgs += gid
            }
            val inner = mutableListOf<String>()
            if (session.cwd.isNotEmpty() && session.cwd != "/") {
                inner += "cd ${shQuote(session.cwd)} 2>/dev/null"
            }
            if (session.chroot.isNullOrEmpty()) {
                inner += shell
            } else {
                inner += "chroot ${shQuote(session.chroot!!)} $shell"
            }
            if (session.extraArgs.isNotBlank()) {
                inner += session.extraArgs.trim()
            }
            suArgs += "-c"
            suArgs += inner.joinToString(" ")
            val argv = mutableListOf("su")
            argv += suArgs
            return Triple("su", argv.toTypedArray(), "/")
        }
        val argv = mutableListOf<String>()
        if (session.chroot.isNullOrEmpty()) {
            argv += shell
        } else {
            argv += "chroot"
            argv += session.chroot!!
            argv += shell
        }
        argv += splitArgs(session.extraArgs)
        val cwd = if (session.chroot.isNullOrEmpty() && session.cwd.isNotEmpty())
            session.cwd.let { if (File(it).isDirectory) it else "/" } else "/"
        return Triple(argv[0], argv.toTypedArray(), cwd)
    }

    private fun setupShizukuMode(color: Int) {
        termView.visibility = View.GONE
        scrollView.visibility = View.VISIBLE
        layoutInput.visibility = View.VISIBLE
        textOutput.setTextColor(color)
        editCommand.setTextColor(color)
        editCommand.setHintTextColor(color and 0x66FFFFFF)

        ShizukuHelper.init()
        ShizukuHelper.onPermissionResult = { granted ->
            runOnUiThread {
                if (granted) {
                    append("已获得 Shizuku 权限\n")
                    startExecutor()
                } else {
                    append("Shizuku 权限被拒绝\n")
                }
            }
        }
        ShizukuHelper.onBinderStateChanged = { available ->
            runOnUiThread {
                if (available && !terminalStarted) {
                    ensureShizukuReady()
                }
            }
        }

        val exec = TerminalExecutor(this, session)
        exec.onOutput = { output -> append(output) }
        exec.onError = { error -> append(error) }
        executor = exec

        printWelcome()
        ensureShizukuReady()

        editCommand.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val command = editCommand.text.toString()
                if (command.isNotEmpty()) {
                    exec.execute(command)
                    append("$command\n")
                    editCommand.setText("")
                }
                true
            } else {
                false
            }
        }
    }

    private fun printWelcome() {
        append("$TERMINUS_BANNER\n")
        append("会话: ${session.name}\n")
        append("Shell: ${session.shell}\n")
        if (session.uid.isNotBlank() || session.gid.isNotBlank()) {
            append("UID/GID: ${session.uid.ifBlank { "-" }}/${session.gid.ifBlank { "-" }}\n")
        }
        if (session.useSu) append("提权: Root (su)\n")
        if (session.useShizuku) append("提权: Shizuku\n")
        append("输入命令执行，输入 exit 退出会话\n")
        append("$TERMINUS_BANNER\n")
    }

    private fun ensureShizukuReady() {
        if (ShizukuHelper.hasPermission()) {
            startExecutor()
            return
        }
        if (!ShizukuHelper.isAvailable()) {
            append("Shizuku 未运行，请先启动 Shizuku 后返回本页\n")
            return
        }
        if (ShizukuHelper.shouldShowRationale()) {
            append("Shizuku 权限已被拒绝，请在 Shizuku 应用中重新授权\n")
            return
        }
        if (!permissionFlowStarted) {
            permissionFlowStarted = true
            append("正在请求 Shizuku 权限...\n")
            ShizukuHelper.requestPermission()
        }
    }

    private fun startExecutor() {
        if (terminalStarted) return
        terminalStarted = true
        executor?.start()
    }

    private fun append(text: String) {
        runOnUiThread {
            textOutput.append(text)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession?) {}

        override fun onTitleChanged(changedSession: TerminalSession?) {}

        override fun onSessionFinished(finishedSession: TerminalSession?) {
            Log.i(TAG, "terminal session finished")
        }

        override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) {
            if (!text.isNullOrEmpty()) copyToClipboard(text)
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val text = clipText()
            text?.let { terminalSession?.write(it.toByteArray(), 0, it.length) }
        }

        override fun onBell(session: TerminalSession?) {}

        override fun onColorsChanged(session: TerminalSession?) {}

        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun getTerminalCursorStyle(): Int? = null

        override fun logError(tag: String, message: String) { Log.e(tag, message) }

        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }

        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }

        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }

        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stacktrace", e) }
    }

    private val viewClient = object : TerminalViewClient {
        override fun onScale(scale: Float): Float = scale

        override fun onSingleTapUp(e: MotionEvent?) {
            showKeyboard()
        }

        override fun shouldBackButtonBeMappedToEscape(): Boolean = false

        override fun shouldEnforceCharBasedInput(): Boolean = false

        override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

        override fun isTerminalViewSelected(): Boolean = true

        override fun copyModeChanged(copyMode: Boolean) {}

        override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false

        override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

        override fun onLongPress(event: MotionEvent?): Boolean = false

        override fun readControlKey(): Boolean = false

        override fun readAltKey(): Boolean = false

        override fun readShiftKey(): Boolean = false

        override fun readFnKey(): Boolean = false

        override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

        override fun onEmulatorSet() {
            termView.requestFocus()
            showKeyboard()
        }

        override fun logError(tag: String, message: String) { Log.e(tag, message) }

        override fun logWarn(tag: String, message: String) { Log.w(tag, message) }

        override fun logInfo(tag: String, message: String) { Log.i(tag, message) }

        override fun logDebug(tag: String, message: String) { Log.d(tag, message) }

        override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "stacktrace", e) }
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(termView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Terminus", text))
    }

    private fun clipText(): String? =
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.onPermissionResult = null
        ShizukuHelper.onBinderStateChanged = null
        terminalSession?.finishIfRunning()
        terminalSession = null
        executor?.stop()
    }

    private fun splitArgs(args: String): List<String> =
        args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun shQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    companion object {
        private const val TERMINUS_BANNER = "=========================="
        private val BASE_ENV = arrayOf(
            "TERM=xterm-256color",
            "HOME=/",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin"
        )
    }
}