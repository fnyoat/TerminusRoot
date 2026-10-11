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

    fun showManager(activity: AppCompatActivity) {
        val entries = TermSettings.quickEntries(activity)
        val items = mutableListOf<String>()
        if (entries.isEmpty()) items.add(activity.getString(R.string.quick_empty))
        else entries.forEach { items.add(it.label.ifEmpty { it.value }) }
        items.add(activity.getString(R.string.quick_add))
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.quick_collection)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == items.size - 1) showAddDialog(activity) { showManager(activity) }
            }
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemLongClickListener { _, _, position, _ ->
                if (position < entries.size) {
                    confirmDelete(activity, entries[position])
                    true
                } else {
                    false
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(activity: AppCompatActivity, entry: TermSettings.QuickEntry) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.quick_delete_confirm)
            .setMessage(entry.label.ifEmpty { entry.value })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val updated = TermSettings.quickEntries(activity).toMutableList()
                updated.remove(entry)
                TermSettings.setQuickEntries(activity, updated)
                showManager(activity)
            }
            .show()
    }
}
