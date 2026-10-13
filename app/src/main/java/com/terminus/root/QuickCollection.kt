package com.terminus.root

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

object QuickCollection {

    fun showAddDialog(activity: AppCompatActivity, onSaved: () -> Unit = {}) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_quick, null)
        val editName = view.findViewById<EditText>(R.id.editQuickName)
        val editValue = view.findViewById<EditText>(R.id.editQuickValue)
        val checkDir = view.findViewById<android.widget.CheckBox>(R.id.checkQuickDir)
        AlertDialog.Builder(activity)
            .setTitle(R.string.quick_add)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val value = editValue.text.toString().trim()
                if (value.isNotEmpty()) {
                    val entry = TermSettings.QuickEntry(
                        editName.text.toString().trim(),
                        value,
                        checkDir.isChecked
                    )
                    TermSettings.setQuickEntries(activity, TermSettings.quickEntries(activity) + entry)
                }
                onSaved()
            }
            .show()
    }

}
