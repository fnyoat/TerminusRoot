package com.terminus.root

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {
    private lateinit var manager: SessionManager
    private lateinit var textDefaultTemplate: TextView
    private lateinit var textColorSummary: TextView
    private lateinit var viewColorSwatch: View
    private lateinit var textFontSize: TextView
    private lateinit var textDefaultPwd: TextView
    private lateinit var textAllFiles: TextView
    private lateinit var textQuickCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        InsetsUtil.apply(this)

        manager = SessionManager(this)
        textDefaultTemplate = findViewById(R.id.textDefaultTemplate)
        textColorSummary = findViewById(R.id.textColorSummary)
        viewColorSwatch = findViewById(R.id.viewColorSwatch)
        textFontSize = findViewById(R.id.textFontSize)
        textDefaultPwd = findViewById(R.id.textDefaultPwd)
        textAllFiles = findViewById(R.id.textAllFiles)
        textQuickCount = findViewById(R.id.textQuickCount)
        findViewById<TextView>(R.id.headerTitle).setTextColor(TermSettings.color(this))

        findViewById<View>(R.id.rowTemplates).setOnClickListener {
            startActivity(Intent(this, TemplatesActivity::class.java))
        }
        findViewById<View>(R.id.rowDefaultTemplate).setOnClickListener { showDefaultTemplateDialog() }
        findViewById<View>(R.id.rowColor).setOnClickListener { showColorDialog() }
        findViewById<View>(R.id.rowFontSize).setOnClickListener { showFontSizeDialog() }
        findViewById<View>(R.id.rowDefaultPwd).setOnClickListener { showDefaultPwdDialog() }
        findViewById<View>(R.id.rowAllFiles).setOnClickListener { requestAllFilesAccess() }
        findViewById<View>(R.id.rowQuick).setOnClickListener { QuickCollection.showManager(this) }
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
        textFontSize.text = getString(R.string.font_size_value, TermSettings.fontSize(this))
        textDefaultPwd.text = TermSettings.defaultPwd(this)
            .ifEmpty { getString(R.string.pwd_system_default) }
        textAllFiles.text = getString(
            if (hasAllFilesAccess()) R.string.all_files_granted else R.string.all_files_denied
        )
        textQuickCount.text = TermSettings.quickEntries(this).size.toString()
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

    private fun showFontSizeDialog() {
        val sizes = TermSettings.FONT_SIZES
        val names = sizes.map { getString(R.string.font_size_value, it) }.toTypedArray()
        val current = TermSettings.fontSize(this)
        val selectedIndex = sizes.indexOfFirst { it == current }.let { if (it < 0) sizes.indexOf(30) else it }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_font_size)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                TermSettings.setFontSize(this, sizes[which])
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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