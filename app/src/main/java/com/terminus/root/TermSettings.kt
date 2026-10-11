package com.terminus.root

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit

object TermSettings {
    private const val PREFS = "terminus_settings"
    private const val KEY_COLOR = "term_color"
    private const val KEY_TEMPLATE = "default_template_id"
    private const val KEY_FONT_SIZE = "term_font_size"

    const val DEFAULT_TEMPLATE_AUTO = "auto"

    val FONT_SIZES = listOf(9, 10, 11, 12, 14, 16, 18, 22, 26, 32)

    data class Preset(val name: String, val color: Int)

    val PRESETS = listOf(
        Preset("绿色", Color.rgb(0x33, 0xFF, 0x33)),
        Preset("琥珀", Color.rgb(0xFF, 0xC1, 0x07)),
        Preset("青色", Color.rgb(0x00, 0xE5, 0xFF)),
        Preset("蓝色", Color.rgb(0x42, 0xA5, 0xF5)),
        Preset("红色", Color.rgb(0xFF, 0x52, 0x52)),
        Preset("白色", Color.rgb(0xFF, 0xFF, 0xFF))
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun color(context: Context): Int {
        val stored = prefs(context).getInt(KEY_COLOR, -1)
        return if (stored != -1) stored else PRESETS[0].color
    }

    fun setColor(context: Context, color: Int) {
        prefs(context).edit { putInt(KEY_COLOR, color) }
    }

    fun defaultTemplateId(context: Context): String =
        prefs(context).getString(KEY_TEMPLATE, DEFAULT_TEMPLATE_AUTO) ?: DEFAULT_TEMPLATE_AUTO

    fun setDefaultTemplateId(context: Context, id: String) {
        prefs(context).edit { putString(KEY_TEMPLATE, id) }
    }

    fun fontSize(context: Context): Int =
        prefs(context).getInt(KEY_FONT_SIZE, FONT_SIZES[2])

    fun setFontSize(context: Context, size: Int) {
        prefs(context).edit { putInt(KEY_FONT_SIZE, size) }
    }
}