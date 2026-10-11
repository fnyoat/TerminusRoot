package com.terminus.root

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private val sessions: MutableList<Session>,
    private val onClick: (Session) -> Unit,
    private val onRename: (Session) -> Unit,
    private val onDuplicate: (Session) -> Unit,
    private val onClose: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {
    
    class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textSessionName)
        val textInfo: TextView = view.findViewById(R.id.textSessionInfo)
        val buttonMenu: ImageButton = view.findViewById(R.id.buttonMenu)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.textName.text = session.name
        val info = buildString {
            append(
                when {
                    session.useShizuku -> "shizuku"
                    session.useSu -> "su"
                    else -> session.shell
                }
            )
            if (session.cwd.isNotEmpty()) append(" | $session.cwd")
            if (!session.chroot.isNullOrEmpty()) append(" | chroot:$session.chroot")
        }
        holder.textInfo.text = info
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
