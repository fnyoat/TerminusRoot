package com.terminus.root

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class SessionManager(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "sessions.json")
    private val templateFile = File(context.filesDir, "templates.json")
    
    private var sessions: MutableList<Session> = mutableListOf()
    private var templates: MutableList<Template> = mutableListOf()
    
    init {
        loadSessions()
        loadTemplates()
    }
    
    private fun loadSessions() {
        if (file.exists()) {
            try {
                val content = file.readText()
                val type = object : TypeToken<List<Session>>() {}.type
                sessions = gson.fromJson(content, type) ?: mutableListOf()
            } catch (e: Exception) {
                sessions = mutableListOf()
            }
        }
    }
    
    private fun loadTemplates() {
        if (templateFile.exists()) {
            try {
                val content = templateFile.readText()
                val type = object : TypeToken<List<Template>>() {}.type
                templates = gson.fromJson(content, type) ?: mutableListOf()
            } catch (e: Exception) {
                templates = mutableListOf()
            }
        }
    }
    
    private fun saveSessions() {
        try {
            file.writeText(gson.toJson(sessions))
        } catch (e: Exception) {
        }
    }
    
    private fun saveTemplates() {
        try {
            templateFile.writeText(gson.toJson(templates))
        } catch (e: Exception) {
        }
    }
    
    fun getSessions(): List<Session> = sessions.toList()
    fun getTemplates(): List<Template> = buildBuiltInTemplates() + templates

    private fun buildBuiltInTemplates(): List<Template> {
        val shell = ShellLocator.findShell()
        return listOf(
            Template(
                id = "builtin-root-shell",
                name = context.getString(R.string.builtin_root_shell),
                pathCwd = "/",
                shell = shell,
                useSu = true,
                builtin = true
            ),
            Template(
                id = "builtin-adb-shell",
                name = context.getString(R.string.builtin_adb_shell),
                pathCwd = "/",
                shell = shell,
                useShizuku = true,
                builtin = true
            )
        )
    }
    
    fun addSession(session: Session) {
        sessions.add(session)
        saveSessions()
    }
    
    fun addTemplate(template: Template) {
        templates.add(template)
        saveTemplates()
    }
    
    fun updateSession(session: Session) {
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = session
            saveSessions()
        }
    }
    
    fun updateTemplate(template: Template) {
        val index = templates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            templates[index] = template
            saveTemplates()
        }
    }
    
    fun removeSession(session: Session) {
        sessions.removeAll { it.id == session.id }
        saveSessions()
    }
    
    fun removeTemplate(template: Template) {
        templates.removeAll { it.id == template.id }
        saveTemplates()
    }
    
    fun duplicateSession(session: Session): Session {
        val newSession = session.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${session.name} (副本)"
        )
        addSession(newSession)
        return newSession
    }
}
