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
    private lateinit var headerTitle: TextView
    private lateinit var manager: SessionManager
    private lateinit var adapter: TemplateAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_templates)
        InsetsUtil.apply(this)
        
        manager = SessionManager(this)
        recycler = findViewById(R.id.recyclerTemplates)
        textEmpty = findViewById(R.id.textEmptyTemplates)
        fab = findViewById(R.id.fabNewTemplate)
        headerTitle = findViewById(R.id.headerTitle)
        
        adapter = TemplateAdapter(
            mutableListOf(),
            onUse = { template -> useTemplate(template) },
            onEdit = { template -> editTemplate(template) },
            onDelete = { template -> deleteTemplate(template) },
            available = { template -> Access.templateAvailable(template) },
            blocked = { template -> Access.templateProfileBlocked(template) }
        )
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        
        fab.setOnClickListener { showTemplateDialog(null) }
        updateList()
    }

    override fun onResume() {
        super.onResume()
        headerTitle.setTextColor(TermSettings.color(this))
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
        val editUid = view.findViewById<EditText>(R.id.editUid)
        val editGid = view.findViewById<EditText>(R.id.editGid)
        val editPath = view.findViewById<EditText>(R.id.editPathCwd)
        val editChroot = view.findViewById<EditText>(R.id.editChrootView)
        val editShell = view.findViewById<android.widget.AutoCompleteTextView>(R.id.editShell)
        val editExtra = view.findViewById<EditText>(R.id.editExtraArgs)
        val checkSu = view.findViewById<android.widget.CheckBox>(R.id.checkUseSu)
        val checkShizuku = view.findViewById<android.widget.CheckBox>(R.id.checkShizuku)
        ShellLocator.bindShellField(editShell)

        template?.let {
            editName.setText(it.name)
            editUid.setText(it.uid)
            editGid.setText(it.gid)
            editPath.setText(it.pathCwd)
            editChroot.setText(it.chrootView)
            editShell.setText(it.shell)
            editExtra.setText(it.extraArgs)
            checkSu.isChecked = it.useSu
            checkShizuku.isChecked = it.useShizuku
        }
        if (editShell.text.isNullOrEmpty()) {
            editShell.setText(ShellLocator.findShell())
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (template == null) R.string.new_template else R.string.edit_template)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTemplate = template?.copy(
                    name = editName.text.toString().ifEmpty { "模板" },
                    pathCwd = editPath.text.toString().ifEmpty { "/" },
                    chrootView = editChroot.text.toString().takeIf { it.isNotEmpty() }.orEmpty(),
                    shell = editShell.text.toString().ifEmpty { ShellLocator.findShell() },
                    useSu = checkSu.isChecked,
                    useShizuku = checkShizuku.isChecked,
                    uid = editUid.text.toString().trim().takeIf { it.isNotEmpty() }.orEmpty(),
                    gid = editGid.text.toString().trim().takeIf { it.isNotEmpty() }.orEmpty(),
                    extraArgs = editExtra.text.toString()
                ) ?: Template(
                    name = editName.text.toString().ifEmpty { "模板" },
                    pathCwd = editPath.text.toString().ifEmpty { "/" },
                    chrootView = editChroot.text.toString().takeIf { it.isNotEmpty() }.orEmpty(),
                    shell = editShell.text.toString().ifEmpty { ShellLocator.findShell() },
                    useSu = checkSu.isChecked,
                    useShizuku = checkShizuku.isChecked,
                    uid = editUid.text.toString().trim().takeIf { it.isNotEmpty() }.orEmpty(),
                    gid = editGid.text.toString().trim().takeIf { it.isNotEmpty() }.orEmpty(),
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
            uid = template.uid,
            gid = template.gid,
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
