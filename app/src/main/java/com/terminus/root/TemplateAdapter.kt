package com.terminus.root

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TemplateAdapter(
    private val templates: MutableList<Template>,
    private val onUse: (Template) -> Unit,
    private val onEdit: (Template) -> Unit,
    private val onDelete: (Template) -> Unit,
    private val available: (Template) -> Boolean = { true },
    private val blocked: (Template) -> Boolean = { false }
) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

    class TemplateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textName: TextView = view.findViewById(R.id.textTemplateName)
        val textInfo: TextView = view.findViewById(R.id.textTemplateInfo)
        val buttonMenu: ImageButton = view.findViewById(R.id.buttonMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_template, parent, false)
        return TemplateViewHolder(view)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        val template = templates[position]
        val accessible = available(template)
        val isBlocked = blocked(template)
        holder.textName.text = template.name
        val info = buildString {
            if (template.uid.isNotBlank() || template.gid.isNotBlank()) {
                append("uid:${template.uid.ifBlank { "-" }} gid:${template.gid.ifBlank { "-" }}")
            }
            if (template.pathCwd.isNotEmpty()) append(" | ${template.pathCwd}")
            if (!template.chrootView.isNullOrEmpty()) append(" | chroot:${template.chrootView}")
            append(
                " | " + when {
                    template.useShizuku -> "shizuku"
                    template.useSu -> "su"
                    else -> template.shell
                }
            )
        }
        holder.textInfo.text = info
        if (!accessible) {
            holder.textInfo.text = "$info | 无权限"
        }
        val context = holder.textName.context
        val textColor = if (isBlocked) {
            context.getColor(R.color.terminal_red)
        } else {
            TermSettings.color(context)
        }
        holder.textName.setTextColor(textColor)
        holder.textInfo.setTextColor(textColor)
        holder.itemView.alpha = if (isBlocked || accessible) 1f else 0.4f
        holder.itemView.isEnabled = accessible
        holder.buttonMenu.setOnClickListener {
            val popup = PopupMenu(holder.itemView.context, holder.buttonMenu)
            popup.menuInflater.inflate(R.menu.template_menu, popup.menu)
            popup.menu.findItem(R.id.menu_use_template).isEnabled = accessible
            if (template.builtin) {
                popup.menu.findItem(R.id.menu_edit_template).isVisible = false
                popup.menu.findItem(R.id.menu_delete_template).isVisible = false
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_use_template -> {
                        if (accessible) onUse(template)
                        true
                    }
                    R.id.menu_edit_template -> {
                        onEdit(template)
                        true
                    }
                    R.id.menu_delete_template -> {
                        onDelete(template)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount() = templates.size

    fun updateTemplates(newTemplates: List<Template>) {
        templates.clear()
        templates.addAll(newTemplates)
        notifyDataSetChanged()
    }
}