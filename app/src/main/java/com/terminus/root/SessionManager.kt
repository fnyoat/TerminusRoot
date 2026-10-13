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
        atomicWrite(file, gson.toJson(sessions))
    }
    
    private fun saveTemplates() {
        atomicWrite(templateFile, gson.toJson(templates))
    }
    
    private fun atomicWrite(target: File, content: String) {
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(target)) {
                target.writeText(content)
                tmp.delete()
            }
        } catch (e: Exception) {
            try {
                File(target.parentFile, "${target.name}.tmp").delete()
            } catch (_: Exception) {
            }
        }
    }
    
    fun getSessions(): List<Session> = sessions.toList()
    fun getTemplates(): List<Template> = buildBuiltInTemplates() + templates

    fun findReusable(match: (Session) -> Boolean): Session? =
        sessions.firstOrNull { !it.detached && match(it) }

    fun resolveDefaultTemplate(context: Context): Template? {
        val id = TermSettings.defaultTemplateId(context)
        if (id != TermSettings.DEFAULT_TEMPLATE_AUTO) {
            getTemplates().firstOrNull { it.id == id }
                ?.takeIf { Access.templateAvailable(it) }
                ?.let { return it }
        }
        val usable = getTemplates().filter { Access.templateAvailable(it) }
        return usable.firstOrNull { it.useSu }
            ?: usable.firstOrNull { it.useShizuku }
            ?: usable.firstOrNull()
    }

    private fun buildBuiltInTemplates(): List<Template> {
        val shell = ShellLocator.findShell()
        val appId = Access.appUid().toString()
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
            ),
            Template(
                id = "builtin-app-uid",
                name = context.getString(R.string.builtin_app_uid),
                pathCwd = "/storage/emulated/0",
                shell = shell,
                uid = appId,
                gid = appId,
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
