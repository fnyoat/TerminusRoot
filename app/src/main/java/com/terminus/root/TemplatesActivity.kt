package com.terminus.root

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TemplatesActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var manager: SessionManager
    private lateinit var adapter: TemplateAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_templates)
        title = getString(R.string.templates)
        
        manager = SessionManager(this)
        recycler = findViewById(R.id.recyclerTemplates)
        textEmpty = findViewById(R.id.textEmptyTemplates)
        fab = findViewById(R.id.fabNewTemplate)
        
        adapter = TemplateAdapter(
            mutableListOf(),
            onUse = { template -> useTemplate(template) },
            onEdit = { template -> editTemplate(template) },
            onDelete = { template -> deleteTemplate(template) }
        )
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        
        fab.setOnClickListener { showTemplateDialog(null) }
        updateList()
    }
    
    private fun updateList() {
        val templates = manager.getTemplates()
        adapter.updateTemplates(templates)
        textEmpty.visibility = if (templates.isEmpty()) TextView.VISIBLE else TextView.GONE
    }
    
    private fun showTemplateDialog(template: Template?) {
        val view = layoutInflater.inflate(R.layout.dialog_template, null)
        val editName = view.findViewById<EditText>(R.id.editTemplateName)
        val editGuid = view.findViewById<EditText>(R.id.editGuid)
        val editPath = view.findViewById<EditText>(R.id.editPathCwd)
        val editChroot = view.findViewById<EditText>(R.id.editChrootView)
        val editShell = view.findViewById<EditText>(R.id.editShell)
        val editExtra = view.findViewById<EditText>(R.id.editExtraArgs)
        val checkSu = view.findViewById<android.widget.CheckBox>(R.id.checkUseSu)
        val checkShizuku = view.findViewById<android.widget.CheckBox>(R.id.checkShizuku)
        
        template?.let {
            editName.setText(it.name)
            editGuid.setText(it.guid)
            editPath.setText(it.pathCwd)
            editChroot.setText(it.chrootView)
            editShell.setText(it.shell)
            editExtra.setText(it.extraArgs)
            checkSu.isChecked = it.useSu
            checkShizuku.isChecked = it.useShizuku
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (template == null) R.string.new_template else R.string.edit_template)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTemplate = template?.copy(
                    name = editName.text.toString().ifEmpty { "模板" },
                    guid = editGuid.text.toString().ifEmpty { java.util.UUID.randomUUID().toString() },
                    pathCwd = editPath.text.toString().ifEmpty { "/" },
                    chrootView = editChroot.text.toString().takeIf { it.isNotEmpty() },
                    shell = editShell.text.toString().ifEmpty { "/bin/sh" },
                    useSu = checkSu.isChecked,
                    useShizuku = checkShizuku.isChecked,
                    extraArgs = editExtra.text.toString()
                ) ?: Template(
                    name = editName.text.toString().ifEmpty { "模板" },
                    guid = editGuid.text.toString().ifEmpty { java.util.UUID.randomUUID().toString() },
                    pathCwd = editPath.text.toString().ifEmpty { "/" },
                    chrootView = editChroot.text.toString().takeIf { it.isNotEmpty() },
                    shell = editShell.text.toString().ifEmpty { "/bin/sh" },
                    useSu = checkSu.isChecked,
                    useShizuku = checkShizuku.isChecked,
                    extraArgs = editExtra.text.toString()
                )
                if (template == null) {
                    manager.addTemplate(newTemplate)
                } else {
                    manager.updateTemplate(newTemplate)
                }
                updateList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun useTemplate(template: Template) {
        val session = Session(
            name = template.name,
            shell = template.shell,
            useSu = template.useSu,
            useShizuku = template.useShizuku,
            cwd = template.pathCwd,
            chroot = template.chrootView,
            extraArgs = template.extraArgs,
            guid = template.guid,
            templateId = template.id
        )
        manager.addSession(session)
        val intent = android.content.Intent(this, TerminalActivity::class.java)
        intent.putExtra("session", session)
        startActivity(intent)
        finish()
    }
    
    private fun editTemplate(template: Template) {
        showTemplateDialog(template)
    }
    
    private fun deleteTemplate(template: Template) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_template)
            .setMessage("确定删除模板 ${template.name} 吗？")
            .setPositiveButton(R.string.ok) { _, _ ->
                manager.removeTemplate(template)
                updateList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
