package com.terminus.root

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CustomizeActivity : AppCompatActivity() {
    private lateinit var textFontSize: TextView
    private lateinit var textCursorStyle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customize)
        InsetsUtil.apply(this)

        textFontSize = findViewById(R.id.textFontSize)
        textCursorStyle = findViewById(R.id.textCursorStyle)
        findViewById<TextView>(R.id.headerTitle).setTextColor(TermSettings.color(this))

        findViewById<View>(R.id.rowFontSize).setOnClickListener { showFontSizeDialog() }
        findViewById<View>(R.id.rowCursorStyle).setOnClickListener { showCursorStyleDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        textFontSize.text = getString(R.string.font_size_value, TermSettings.fontSize(this))
        textCursorStyle.text = cursorName(TermSettings.cursorStyle(this))
    }

    private fun cursorName(style: Int): String = getString(
        when (style) {
            TermSettings.CURSOR_UNDERLINE -> R.string.cursor_underline
            TermSettings.CURSOR_BAR -> R.string.cursor_bar
            else -> R.string.cursor_block
        }
    )

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

    private fun showCursorStyleDialog() {
        val styles = intArrayOf(
            TermSettings.CURSOR_BLOCK,
            TermSettings.CURSOR_UNDERLINE,
            TermSettings.CURSOR_BAR
        )
        val names = styles.map { cursorName(it) }.toTypedArray()
        val current = TermSettings.cursorStyle(this)
        val selectedIndex = styles.indexOfFirst { it == current }.let { if (it < 0) 0 else it }
        AlertDialog.Builder(this)
            .setTitle(R.string.select_cursor_style)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                TermSettings.setCursorStyle(this, styles[which])
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
