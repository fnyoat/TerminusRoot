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
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.io.File

private const val TAG = "TerminalActivity"
private val terminalSessions = HashMap<String, TerminalSession>()
private var currentTerminal: TerminalActivity? = null

class TerminalActivity : AppCompatActivity() {
    private lateinit var textOutput: TextView
    private lateinit var editCommand: EditText
    private lateinit var scrollView: ScrollView
    lateinit var termView: TerminalView
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
        termView.setTextSize(TermSettings.fontSize(this))

        val launch = buildLaunchCommand()
        val existing = terminalSessions[session.id]
        val terminal = if (existing != null && existing.isRunning) existing else {
            TerminalSession(launch.first, launch.third, launch.second, buildEnv(), 2000, sessionClient)
                .also { terminalSessions[session.id] = it }
        }
        terminalSession = terminal
        termView.attachSession(terminal)
        applyTerminalColor(terminal)
        termView.onScreenUpdated()
        termView.requestFocus()
    }

    private fun applyTerminalColor(terminal: TerminalSession) {
        val fg = TermSettings.color(this)
        val defaults = TerminalColors.COLOR_SCHEME.mDefaultColors
        defaults[TextStyle.COLOR_INDEX_FOREGROUND] = fg
        defaults[TextStyle.COLOR_INDEX_CURSOR] = fg
        val colors = terminal.emulator?.mColors ?: return
        colors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = fg
        colors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = fg
    }

    private fun buildLaunchCommand(): Triple<String, Array<String>, String> {
        val effCwd = session.cwd.ifEmpty { TermSettings.defaultPwd(this) }
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
            if (effCwd.isNotEmpty() && effCwd != "/") {
                inner += "cd ${shQuote(effCwd)} 2>/dev/null"
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
        val cwd = if (session.chroot.isNullOrEmpty() && effCwd.isNotEmpty())
            effCwd.let { if (File(it).isDirectory) it else "/" } else "/"
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

    private val sessionClient = SessionClient

    private val viewClient = ViewClient

    internal fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(termView, InputMethodManager.SHOW_IMPLICIT)
    }

    internal fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Terminus", text))
    }

    internal fun clipText(): String? =
        (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    override fun onResume() {
        super.onResume()
        currentTerminal = this
        if (!session.useShizuku) termView.onScreenUpdated()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.onPermissionResult = null
        ShizukuHelper.onBinderStateChanged = null
        if (currentTerminal === this) currentTerminal = null
        terminalSession?.let { if (!it.isRunning) terminalSessions.remove(session.id) }
        terminalSession = null
        executor?.stop()
    }

    private fun buildEnv(): Array<String> {
        val env = mutableListOf(
            "TERM=xterm-256color",
            "HOME=/",
            "PATH=/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin",
            "CLICOLOR=1",
            "LS_COLORS=$LS_COLORS"
        )
        if (session.chroot.isNullOrEmpty()) {
            ensureShellRc()?.let { env += "ENV=$it" }
        }
        return env.toTypedArray()
    }

    private fun ensureShellRc(): String? = try {
        val file = File(filesDir, "shellrc")
        file.writeText(SHELL_RC)
        file.setReadable(true, false)
        file.absolutePath
    } catch (e: Exception) {
        null
    }

    private fun splitArgs(args: String): List<String> =
        args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun shQuote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    companion object {
        private const val TERMINUS_BANNER = "=========================="
        private const val SHELL_RC = """
[ -r /system/etc/mkshrc ] && . /system/etc/mkshrc
alias ls='ls --color=auto'
alias ll='ls -l'
alias la='ls -a'
alias l='ls -CF'
alias grep='grep --color=auto'
alias egrep='grep -E --color=auto'
alias fgrep='grep -F --color=auto'
"""
        private const val LS_COLORS =
            "rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:" +
            "bd=40;33;01:cd=40;33;01:or=40;31;01:mi=00:su=37;41:sg=30;43:" +
            "ca=30;41:tw=30;42:ow=34;42:st=37;44:ex=01;32:" +
            "*.tar=01;31:*.tgz=01;31:*.arc=01;31:*.arj=01;31:*.taz=01;31:" +
            "*.lha=01;31:*.lz4=01;31:*.lzh=01;31:*.lzma=01;31:*.tlz=01;31:" +
            "*.txz=01;31:*.tzo=01;31:*.t7z=01;31:*.zip=01;31:*.z=01;31:" +
            "*.dz=01;31:*.gz=01;31:*.lrz=01;31:*.lz=01;31:*.lzo=01;31:" +
            "*.xz=01;31:*.zst=01;31:*.tzst=01;31:*.bz2=01;31:*.bz=01;31:" +
            "*.tbz=01;31:*.tbz2=01;31:*.tz=01;31:*.deb=01;31:*.rpm=01;31:" +
            "*.jar=01;31:*.war=01;31:*.ear=01;31:*.sar=01;31:*.rar=01;31:" +
            "*.alz=01;31:*.ace=01;31:*.zoo=01;31:*.cpio=01;31:*.7z=01;31:" +
            "*.rz=01;31:*.cab=01;31:*.wim=01;31:*.swm=01;31:*.dwm=01;31:" +
            "*.esd=01;31:*.jpg=01;35:*.jpeg=01;35:*.mjpg=01;35:*.mjpeg=01;35:" +
            "*.gif=01;35:*.bmp=01;35:*.pbm=01;35:*.pgm=01;35:*.ppm=01;35:" +
            "*.tga=01;35:*.xbm=01;35:*.xpm=01;35:*.tif=01;35:*.tiff=01;35:" +
            "*.png=01;35:*.svg=01;35:*.svgz=01;35:*.mng=01;35:*.pcx=01;35:" +
            "*.mov=01;35:*.mpg=01;35:*.mpeg=01;35:*.m2v=01;35:*.mkv=01;35:" +
            "*.webm=01;35:*.webp=01;35:*.ogm=01;35:*.mp4=01;35:*.m4v=01;35:" +
            "*.mp4v=01;35:*.vob=01;35:*.qt=01;35:*.nuv=01;35:*.wmv=01;35:" +
            "*.asf=01;35:*.rm=01;35:*.rmvb=01;35:*.flc=01;35:*.avi=01;35:" +
            "*.fli=01;35:*.flv=01;35:*.gl=01;35:*.dl=01;35:*.xcf=01;35:" +
            "*.xwd=01;35:*.yuv=01;35:*.cgm=01;35:*.emf=01;35:*.ogv=01;35:" +
            "*.ogx=01;35:*.aac=00;36:*.au=00;36:*.flac=00;36:*.m4a=00;36:" +
            "*.mid=00;36:*.midi=00;36:*.mka=00;36:*.mp3=00;36:*.mpc=00;36:" +
            "*.ogg=00;36:*.ra=00;36:*.wav=00;36:*.oga=00;36:*.opus=00;36:" +
            "*.spx=00;36:*.xspf=00;36:*.sh=01;32:*.bash=01;32:*.zsh=01;32:" +
            "*.run=01;32:*.py=01;33:*.pl=01;33:*.pm=01;33:*.rb=01;33"
    }
}

private object SessionClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession?) {
        currentTerminal?.termView?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession?) {}

    override fun onSessionFinished(finishedSession: TerminalSession?) {
        Log.i(TAG, "terminal session finished")
    }

    override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) {
        if (!text.isNullOrEmpty()) currentTerminal?.copyToClipboard(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        currentTerminal?.let { act ->
            act.clipText()?.let { txt -> session?.write(txt.toByteArray(), 0, txt.length) }
        }
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

private object ViewClient : TerminalViewClient {
    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent?) {
        currentTerminal?.showKeyboard()
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
        currentTerminal?.let { it.termView.requestFocus(); it.showKeyboard() }
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