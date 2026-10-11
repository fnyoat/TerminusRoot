package com.terminus.root

import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import java.io.File

object ShellLocator {
    private val SH_CANDIDATES = listOf(
        "/system/bin/sh",
        "/system/xbin/sh",
        "/vendor/bin/sh",
        "/sbin/sh",
        "/bin/sh",
        "/usr/bin/sh",
        "/data/data/com.termux/files/usr/bin/sh"
    )

    private val BASH_CANDIDATES = listOf(
        "/system/bin/bash",
        "/system/xbin/bash",
        "/vendor/bin/bash",
        "/sbin/bash",
        "/bin/bash",
        "/usr/bin/bash",
        "/data/data/com.termux/files/usr/bin/bash"
    )

    fun findShell(): String = SH_CANDIDATES.firstOrNull { isExecutable(it) } ?: "/system/bin/sh"

    fun findBash(): String? = BASH_CANDIDATES.firstOrNull { isExecutable(it) }

    fun availableShells(): List<String> =
        (SH_CANDIDATES + BASH_CANDIDATES).filter { isExecutable(it) }.distinct()

    fun resolve(preferred: String): String {
        if (preferred.isNotEmpty() && isExecutable(preferred)) return preferred
        return findShell()
    }

    fun bindShellField(field: AutoCompleteTextView) {
        val shells = availableShells().ifEmpty { listOf(findShell()) }
        field.setAdapter(
            ArrayAdapter(field.context, android.R.layout.simple_dropdown_item_1line, shells)
        )
        field.threshold = 1
        field.setOnClickListener { field.showDropDown() }
    }

    private fun isExecutable(path: String): Boolean = try {
        val file = File(path)
        file.exists() && file.canExecute()
    } catch (t: Throwable) {
        false
    }
}
