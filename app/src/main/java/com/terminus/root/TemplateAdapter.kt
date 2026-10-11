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
    private val onDelete: (Template) -> Unit
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
        holder.textName.text = template.name
        val info = buildString {
            append("GUID:${template.guid}")
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
        holder.buttonMenu.setOnClickListener {
            val popup = PopupMenu(holder.itemView.context, holder.buttonMenu)
            popup.menuInflater.inflate(R.menu.template_menu, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_use_template -> {
                        onUse(template)
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
