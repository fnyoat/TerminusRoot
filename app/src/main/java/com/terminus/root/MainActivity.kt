package com.terminus.root

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private lateinit var recycler: RecyclerView
    private lateinit var textEmpty: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var manager: SessionManager
    private lateinit var adapter: SessionAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        InsetsUtil.apply(this)
        
        manager = SessionManager(this)
        recycler = findViewById(R.id.recyclerSessions)
        textEmpty = findViewById(R.id.textEmpty)
        fab = findViewById(R.id.fabNewSession)
        val headerLink = findViewById<TextView>(R.id.headerLink)

        ShizukuHelper.init()
        
        adapter = SessionAdapter(
            mutableListOf(),
            onClick = { session ->
                val intent = Intent(this, TerminalActivity::class.java)
                intent.putExtra("session", session)
                startActivity(intent)
            },
            onRename = { session -> renameSession(session) },
            onDuplicate = { session -> 
                val newSession = manager.duplicateSession(session)
                updateList()
                val intent = Intent(this, TerminalActivity::class.java)
                intent.putExtra("session", newSession)
                startActivity(intent)
            },
            onClose = { session -> closeSession(session) }
        )
        
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        
        fab.setOnClickListener { createNewSession() }
        headerLink.setOnClickListener { startActivity(Intent(this, TemplatesActivity::class.java)) }
    }
    
    override fun onResume() {
        super.onResume()
        updateList()
    }
    
    private fun updateList() {
        val sessions = manager.getSessions()
        adapter.updateSessions(sessions)
        textEmpty.visibility = if (sessions.isEmpty()) TextView.VISIBLE else TextView.GONE
    }
    
    private fun createNewSession() {
        val view = layoutInflater.inflate(R.layout.dialog_template, null)
        val editName = view.findViewById<EditText>(R.id.editTemplateName)
        val editGuid = view.findViewById<EditText>(R.id.editGuid)
        val editPath = view.findViewById<EditText>(R.id.editPathCwd)
        val editChroot = view.findViewById<EditText>(R.id.editChrootView)
        val editShell = view.findViewById<android.widget.AutoCompleteTextView>(R.id.editShell)
        val editExtra = view.findViewById<EditText>(R.id.editExtraArgs)
        val checkSu = view.findViewById<android.widget.CheckBox>(R.id.checkUseSu)
        val checkShizuku = view.findViewById<android.widget.CheckBox>(R.id.checkShizuku)
        ShellLocator.bindShellField(editShell)
        editGuid.setText(java.util.UUID.randomUUID().toString())
        editPath.setText("/")
        editShell.setText(ShellLocator.findShell())

        AlertDialog.Builder(this)
            .setTitle(R.string.new_session)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val session = Session(
                    name = editName.text.toString().ifEmpty { "会话-${System.currentTimeMillis() % 10000}" },
                    guid = editGuid.text.toString(),
                    cwd = editPath.text.toString().ifEmpty { "/" },
                    chroot = editChroot.text.toString().takeIf { it.isNotEmpty() },
                    shell = editShell.text.toString().ifEmpty { ShellLocator.findShell() },
                    useSu = checkSu.isChecked,
                    useShizuku = checkShizuku.isChecked,
                    extraArgs = editExtra.text.toString()
                )
                manager.addSession(session)
                val intent = Intent(this, TerminalActivity::class.java)
                intent.putExtra("session", session)
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun renameSession(session: Session) {
        val edit = EditText(this)
        edit.setText(session.name)
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(edit)
            .setPositiveButton(R.string.ok) { _, _ ->
                session.name = edit.text.toString().ifEmpty { session.name }
                manager.updateSession(session)
                updateList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun closeSession(session: Session) {
        AlertDialog.Builder(this)
            .setTitle(R.string.close)
            .setMessage(R.string.close_session_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                manager.removeSession(session)
                updateList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
