package com.terminus.root

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object TermSettings {
    private const val PREFS = "terminus_settings"
    private const val KEY_COLOR = "term_color"
    private const val KEY_TEMPLATE = "default_template_id"
    private const val KEY_FONT_SIZE = "term_font_size"
    private const val KEY_DEFAULT_PWD = "default_pwd"
    private const val KEY_QUICK = "quick_entries"
    private const val KEY_CURSOR_STYLE = "cursor_style"
    private const val KEY_PERSIST = "session_persist"
    private const val KEY_CLEAN_ON_START = "clean_on_start"
    private const val KEY_CLEAN_DEAD = "clean_dead"
    private const val KEY_CLEAN_NO_HISTORY = "clean_no_history"
    private const val KEY_CLEAN_PRIV = "clean_priv_level"
    private const val KEY_IDLE_MINUTES = "pty_idle_minutes"

    const val DEFAULT_TEMPLATE_AUTO = "auto"

    const val CLEAN_PRIV_OFF = "off"
    const val CLEAN_PRIV_ADB = "adb"
    const val CLEAN_PRIV_ROOT = "root"

    const val DEFAULT_IDLE_MINUTES = 5

    val FONT_SIZES = listOf(18, 22, 26, 30, 34, 40, 48, 56)

    const val CURSOR_BLOCK = 0
    const val CURSOR_UNDERLINE = 1
    const val CURSOR_BAR = 2

    data class Preset(val name: String, val color: Int)

    data class QuickEntry(val label: String, val value: String, val isDir: Boolean)

    val PRESETS = listOf(
        Preset("灰白", Color.rgb(0xE0, 0xE0, 0xE0)),
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
        prefs(context).getInt(KEY_FONT_SIZE, 30)

    fun setFontSize(context: Context, size: Int) {
        prefs(context).edit { putInt(KEY_FONT_SIZE, size) }
    }

    fun cursorStyle(context: Context): Int =
        prefs(context).getInt(KEY_CURSOR_STYLE, CURSOR_BLOCK)

    fun setCursorStyle(context: Context, style: Int) {
        prefs(context).edit { putInt(KEY_CURSOR_STYLE, style) }
    }

    fun sessionPersist(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERSIST, true)

    fun setSessionPersist(context: Context, persist: Boolean) {
        prefs(context).edit { putBoolean(KEY_PERSIST, persist) }
    }

    fun cleanOnStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAN_ON_START, true)

    fun setCleanOnStart(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLEAN_ON_START, value) }
    }

    fun cleanDead(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAN_DEAD, true)

    fun setCleanDead(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLEAN_DEAD, value) }
    }

    fun cleanNoHistory(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLEAN_NO_HISTORY, false)

    fun setCleanNoHistory(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_CLEAN_NO_HISTORY, value) }
    }

    fun cleanPrivLevel(context: Context): String =
        prefs(context).getString(KEY_CLEAN_PRIV, CLEAN_PRIV_OFF) ?: CLEAN_PRIV_OFF

    fun setCleanPrivLevel(context: Context, level: String) {
        prefs(context).edit { putString(KEY_CLEAN_PRIV, level) }
    }

    fun idleMinutes(context: Context): Int =
        prefs(context).getInt(KEY_IDLE_MINUTES, DEFAULT_IDLE_MINUTES).coerceAtLeast(0)

    fun setIdleMinutes(context: Context, minutes: Int) {
        prefs(context).edit { putInt(KEY_IDLE_MINUTES, minutes.coerceAtLeast(0)) }
    }

    fun defaultPwd(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_PWD, "/") ?: "/"

    fun setDefaultPwd(context: Context, pwd: String) {
        prefs(context).edit { putString(KEY_DEFAULT_PWD, pwd) }
    }

    fun quickEntries(context: Context): List<QuickEntry> {
        val json = prefs(context).getString(KEY_QUICK, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<QuickEntry>>() {}.type
            Gson().fromJson<List<QuickEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setQuickEntries(context: Context, entries: List<QuickEntry>) {
        prefs(context).edit { putString(KEY_QUICK, Gson().toJson(entries)) }
    }
}