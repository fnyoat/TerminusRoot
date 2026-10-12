package com.terminus.root

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private val sessions: MutableList<Session>,
    private val onClick: (Session) -> Unit,
    private val onRename: (Session) -> Unit,
    private val onDuplicate: (Session) -> Unit,
    private val onClose: (Session) -> Unit,
    private val blocked: (Session) -> Boolean = { false },
    private val isRunning: (Session) -> Boolean = { false },
    private val templateName: (Session) -> String = { "" }
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {
    
    class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textSessionName)
        val textInfo: TextView = view.findViewById(R.id.textSessionInfo)
        val textTemplate: TextView = view.findViewById(R.id.textTemplate)
        val imageLock: ImageView = view.findViewById(R.id.imageLock)
        val buttonMenu: ImageButton = view.findViewById(R.id.buttonMenu)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        val isBlocked = blocked(session)
        holder.textName.text = session.name
        val tpl = templateName(session)
        holder.textTemplate.text = tpl
        holder.textTemplate.visibility = if (tpl.isEmpty()) View.GONE else View.VISIBLE
        val info = if (session.detached) {
            holder.textInfo.context.getString(
                if (isRunning(session)) R.string.status_running else R.string.status_dead
            )
        } else {
            ""
        }
        holder.textInfo.text = info
        holder.textInfo.visibility = if (info.isEmpty()) View.GONE else View.VISIBLE
        val color = if (isBlocked) {
            holder.textName.context.getColor(R.color.terminal_red)
        } else {
            TermSettings.color(holder.textName.context)
        }
        holder.textName.setTextColor(color)
        holder.textInfo.setTextColor(color)
        holder.imageLock.visibility =
            if (session.detached && isRunning(session)) View.VISIBLE else View.GONE
        holder.imageLock.imageTintList =
            android.content.res.ColorStateList.valueOf(color)
        holder.itemView.setOnClickListener { onClick(session) }
        holder.buttonMenu.setOnClickListener {
            val popup = PopupMenu(holder.itemView.context, holder.buttonMenu)
            popup.menuInflater.inflate(R.menu.session_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_rename -> {
                        onRename(session)
                        true
                    }
                    R.id.menu_duplicate -> {
                        onDuplicate(session)
                        true
                    }
                    R.id.menu_close -> {
                        onClose(session)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
    
    override fun getItemCount() = sessions.size
    
    fun updateSessions(newSessions: List<Session>) {
        sessions.clear()
        sessions.addAll(newSessions)
        notifyDataSetChanged()
    }
}
