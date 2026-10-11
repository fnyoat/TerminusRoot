package com.terminus.root

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TerminalActivity : AppCompatActivity() {
    private lateinit var textOutput: TextView
    private lateinit var editCommand: EditText
    private lateinit var scrollView: ScrollView
    private lateinit var executor: TerminalExecutor
    private lateinit var session: Session
    private var permissionFlowStarted = false
    private var terminalStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
setContentView(R.layout.activity_terminal)

        session = intent.getSerializableExtra("session") as Session
        title = session.name
        InsetsUtil.apply(this)

        textOutput = findViewById(R.id.textOutput)
        editCommand = findViewById(R.id.editCommand)
        scrollView = findViewById(R.id.scrollView)

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
                if (available && session.useShizuku && !terminalStarted) {
                    ensureShizukuReady()
                }
            }
        }

        executor = TerminalExecutor(this, session)
        executor.onOutput = { output -> append(output) }
        executor.onError = { error -> append(error) }

        if (session.useShizuku) {
            ensureShizukuReady()
        } else {
            executor.start()
        }

        editCommand.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val command = editCommand.text.toString()
                if (command.isNotEmpty()) {
                    executor.execute(command)
                    append("$command\n")
                    editCommand.setText("")
                }
                true
            } else {
                false
            }
        }
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
        executor.start()
    }

    private fun append(text: String) {
        runOnUiThread {
            textOutput.append(text)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuHelper.onPermissionResult = null
        ShizukuHelper.onBinderStateChanged = null
        executor.stop()
    }
}
