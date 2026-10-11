package com.terminus.root

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var manager: SessionManager
    private lateinit var textDefaultTemplate: TextView
    private lateinit var textColorSummary: TextView
    private lateinit var viewColorSwatch: View
    private lateinit var textFontSize: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        InsetsUtil.apply(this)

        manager = SessionManager(this)
        textDefaultTemplate = findViewById(R.id.textDefaultTemplate)
        textColorSummary = findViewById(R.id.textColorSummary)
        viewColorSwatch = findViewById(R.id.viewColorSwatch)
        textFontSize = findViewById(R.id.textFontSize)
        findViewById<TextView>(R.id.headerTitle).setTextColor(TermSettings.color(this))

        findViewById<View>(R.id.rowDefaultTemplate).setOnClickListener { showDefaultTemplateDialog() }
        findViewById<View>(R.id.rowColor).setOnClickListener { showColorDialog() }
        findViewById<View>(R.id.rowFontSize).setOnClickListener { showFontSizeDialog() }
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

    private fun showFontSizeDialog() {
        val sizes = TermSettings.FONT_SIZES
        val names = sizes.map { getString(R.string.font_size_value, it) }.toTypedArray()
        val current = TermSettings.fontSize(this)
        val selectedIndex = sizes.indexOfFirst { it == current }.let { if (it < 0) sizes.indexOf(12) else it }
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
}