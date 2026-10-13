package com.terminus.root

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var manager: SessionManager
    private lateinit var textDefaultTemplate: TextView
    private lateinit var textColorSummary: TextView
    private lateinit var viewColorSwatch: View
    private lateinit var textDefaultPwd: TextView
    private lateinit var textPersist: TextView
    private lateinit var textAllFiles: TextView
    private lateinit var textClean: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        InsetsUtil.apply(this)

        manager = SessionManager(this)
        textDefaultTemplate = findViewById(R.id.textDefaultTemplate)
        textColorSummary = findViewById(R.id.textColorSummary)
        viewColorSwatch = findViewById(R.id.viewColorSwatch)
        textDefaultPwd = findViewById(R.id.textDefaultPwd)
        textPersist = findViewById(R.id.textPersist)
        textAllFiles = findViewById(R.id.textAllFiles)
        textClean = findViewById(R.id.textClean)
        findViewById<TextView>(R.id.headerTitle).setTextColor(TermSettings.color(this))

        findViewById<View>(R.id.rowTemplates).setOnClickListener {
            startActivity(Intent(this, TemplatesActivity::class.java))
        }
        findViewById<View>(R.id.rowDefaultTemplate).setOnClickListener { showDefaultTemplateDialog() }
        findViewById<View>(R.id.rowCustomize).setOnClickListener {
            startActivity(Intent(this, CustomizeActivity::class.java))
        }
        findViewById<View>(R.id.rowColor).setOnClickListener { showColorDialog() }
        findViewById<View>(R.id.rowDefaultPwd).setOnClickListener { showDefaultPwdDialog() }
        findViewById<View>(R.id.rowPersist).setOnClickListener { showPersistDialog() }
        findViewById<View>(R.id.rowAllFiles).setOnClickListener { requestAllFilesAccess() }
        findViewById<View>(R.id.rowClean).setOnClickListener { showCleanDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val current = TermSettings.color(this)
        val preset = TermSettings.PRESETS.firstOrNull { it.color == current }
        textColorSummary.text = preset?.name ?: "#%08X".format(current)
        (viewColorSwatch.background as GradientDrawable).setColor(current)
        textDefaultTemplate.text = defaultTemplateSummary()
        textDefaultPwd.text = TermSettings.defaultPwd(this)
            .ifEmpty { getString(R.string.pwd_system_default) }
        textPersist.text = getString(
            if (TermSettings.sessionPersist(this)) R.string.persist_keep else R.string.persist_follow
        )
        textAllFiles.text = getString(
            if (hasAllFilesAccess()) R.string.all_files_granted else R.string.all_files_denied
        )
        textClean.text = cleanSummary()
    }

    private fun cleanSummary(): String {
        val parts = mutableListOf<String>()
        if (TermSettings.cleanOnStart(this)) parts.add("启动")
        when (TermSettings.cleanPrivLevel(this)) {
            TermSettings.CLEAN_PRIV_ADB -> parts.add("清理<adb")
            TermSettings.CLEAN_PRIV_ROOT -> parts.add("清理<root")
        }
        val idle = TermSettings.idleMinutes(this)
        if (idle > 0) parts.add("空闲${idle}分")
        return if (parts.isEmpty()) getString(R.string.clean_summary_off) else parts.joinToString(" · ")
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQ_STORAGE
            )
        }
    }

    private fun defaultTemplateSummary(): String {
        val id = TermSettings.defaultTemplateId(this)
        if (id == TermSettings.DEFAULT_TEMPLATE_AUTO) return getString(R.string.auto_default_template)
        val template = manager.getTemplates().firstOrNull { it.id == id }
        return template?.name ?: getString(R.string.auto_default_template)
    }

    private fun showDefaultTemplateDialog() {
        val templates = manager.getTemplates()
        val names = mutableListOf(getString(R.string.auto_default_template))
        names.addAll(templates.map { it.name })
        val selectedIndex = when (val id = TermSettings.defaultTemplateId(this)) {
            TermSettings.DEFAULT_TEMPLATE_AUTO -> 0
            else -> templates.indexOfFirst { it.id == id }.let { if (it < 0) 0 else it + 1 }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_default_template)
            .setSingleChoiceItems(names.toTypedArray(), selectedIndex) { dialog, which ->
                TermSettings.setDefaultTemplateId(
                    this,
                    if (which == 0) TermSettings.DEFAULT_TEMPLATE_AUTO else templates[which - 1].id
                )
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showColorDialog() {
        val names = TermSettings.PRESETS.map { it.name }.toTypedArray()
        val current = TermSettings.color(this)
        val selectedIndex = TermSettings.PRESETS.indexOfFirst { it.color == current }.let { if (it < 0) 0 else it }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_color)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                TermSettings.setColor(this, TermSettings.PRESETS[which].color)
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPersistDialog() {
        val options = arrayOf(
            getString(R.string.persist_keep),
            getString(R.string.persist_follow)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.select_session_persist)
            .setSingleChoiceItems(options, if (TermSettings.sessionPersist(this)) 0 else 1) { dialog, which ->
                TermSettings.setSessionPersist(this, which == 0)
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDefaultPwdDialog() {
        val input = EditText(this)
        input.setText(TermSettings.defaultPwd(this))
        input.hint = getString(R.string.default_pwd_hint)
        input.setTextColor(TermSettings.color(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.default_pwd)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                TermSettings.setDefaultPwd(this, input.text.toString().trim())
                refresh()
            }
            .show()
    }

    private fun showCleanDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_clean, null)
        val checkOnStart = view.findViewById<CheckBox>(R.id.checkCleanOnStart)
        val checkDead = view.findViewById<CheckBox>(R.id.checkCleanDead)
        val checkNoHistory = view.findViewById<CheckBox>(R.id.checkCleanNoHistory)
        val textPriv = view.findViewById<TextView>(R.id.textCleanPriv)
        val editIdle = view.findViewById<EditText>(R.id.editIdleMinutes)

        checkOnStart.isChecked = TermSettings.cleanOnStart(this)
        checkDead.isChecked = TermSettings.cleanDead(this)
        checkNoHistory.isChecked = TermSettings.cleanNoHistory(this)
        editIdle.setText(TermSettings.idleMinutes(this).toString())

        val values = arrayOf(
            TermSettings.CLEAN_PRIV_OFF,
            TermSettings.CLEAN_PRIV_ADB,
            TermSettings.CLEAN_PRIV_ROOT
        )
        val labels = arrayOf(
            getString(R.string.clean_priv_off),
            getString(R.string.clean_priv_adb),
            getString(R.string.clean_priv_root)
        )
        var privLevel = TermSettings.cleanPrivLevel(this)
        fun privLabel(level: String): String =
            labels[values.indexOf(level).let { if (it < 0) 0 else it }]
        textPriv.text = privLabel(privLevel)

        view.findViewById<View>(R.id.rowCleanPriv).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clean_priv)
                .setSingleChoiceItems(labels, values.indexOf(privLevel).coerceAtLeast(0)) { d, which ->
                    privLevel = values[which]
                    textPriv.text = labels[which]
                    d.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        fun persist() {
            TermSettings.setCleanOnStart(this, checkOnStart.isChecked)
            TermSettings.setCleanDead(this, checkDead.isChecked)
            TermSettings.setCleanNoHistory(this, checkNoHistory.isChecked)
            TermSettings.setCleanPrivLevel(this, privLevel)
            TermSettings.setIdleMinutes(
                this,
                editIdle.text.toString().toIntOrNull() ?: TermSettings.DEFAULT_IDLE_MINUTES
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.clean_sessions)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                persist()
                refresh()
            }
            .setNeutralButton(R.string.clean_now, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                persist()
                runCleanNow()
            }
        }
        dialog.show()
    }

    private fun runCleanNow() {
        val app = applicationContext
        val mgr = manager
        Thread {
            val result = try {
                SessionCleaner.cleanNow(app, mgr)
            } catch (t: Throwable) {
                SessionCleaner.Result(0, 0)
            }
            runOnUiThread {
                val msg = if (result.sessions == 0 && result.processes == 0) {
                    getString(R.string.clean_none)
                } else {
                    getString(R.string.clean_done, result.sessions, result.processes)
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                refresh()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) refresh()
    }

    companion object {
        private const val REQ_STORAGE = 1001
    }
}