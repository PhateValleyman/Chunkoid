package com.encore.chunkoid.ui.nbt

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import br.com.gamemods.nbtmanipulator.*
import com.encore.chunkoid.R
import com.encore.chunkoid.databinding.ItemNbtNodeBinding

class NbtTreeAdapter(
    private val context: Context,
    private val onNodeEdit: (NbtTreeNode) -> Unit,
    private val onNodeAddChild: (NbtTreeNode) -> Unit,
    private val onNodeRename: (NbtTreeNode) -> Unit,
    private val onNodeDelete: (NbtTreeNode) -> Unit,
    private val onNodeCopyPath: (NbtTreeNode) -> Unit,
    private val onNodeCopyValue: (NbtTreeNode) -> Unit
) : RecyclerView.Adapter<NbtTreeAdapter.ViewHolder>() {

    private var rootTag: NbtTag? = null
    private var rootKey: String = "Root"
    private val expandedPaths = mutableSetOf<String>()
    private var visibleNodes = mutableListOf<NbtTreeNode>()
    private var searchQuery: String = ""

    fun setRoot(tag: NbtTag, key: String = "Root") {
        this.rootTag = tag
        this.rootKey = key
        this.expandedPaths.clear()
        this.expandedPaths.add(key)
        rebuildVisibleNodes()
    }

    fun getRootTag(): NbtTag? = rootTag

    fun setSearchQuery(query: String) {
        this.searchQuery = query.trim()
        rebuildVisibleNodes()
    }

    fun expandAll() {
        val root = rootTag ?: return
        fun collectPaths(tag: NbtTag, path: String) {
            expandedPaths.add(path)
            when (tag) {
                is NbtCompound -> {
                    tag.forEach { (k, child) ->
                        collectPaths(child, "$path/$k")
                    }
                }
                is NbtList<*> -> {
                    tag.forEachIndexed { i, child ->
                        collectPaths(child, "$path/[$i]")
                    }
                }
                is NbtByteArray -> {
                    tag.value.forEachIndexed { i, b ->
                        collectPaths(NbtByte(b), "$path/[$i]")
                    }
                }
                is NbtIntArray -> {
                    tag.value.forEachIndexed { i, intVal ->
                        collectPaths(NbtInt(intVal), "$path/[$i]")
                    }
                }
                is NbtLongArray -> {
                    tag.value.forEachIndexed { i, l ->
                        collectPaths(NbtLong(l), "$path/[$i]")
                    }
                }
                else -> {}
            }
        }
        collectPaths(root, rootKey)
        rebuildVisibleNodes()
    }

    fun collapseAll() {
        expandedPaths.clear()
        expandedPaths.add(rootKey)
        rebuildVisibleNodes()
    }

    fun refreshTree() {
        rebuildVisibleNodes()
    }

    private fun rebuildVisibleNodes() {
        val root = rootTag ?: run {
            visibleNodes.clear()
            notifyDataSetChanged()
            return
        }

        val result = mutableListOf<NbtTreeNode>()
        val query = searchQuery.lowercase()

        if (query.isNotEmpty()) {

            fun searchTraverse(tag: NbtTag, key: String, parent: NbtTag?, depth: Int, path: String, index: Int) {
                val node = NbtTreeNode(key, tag, parent, depth, false, path, index)
                val matches = key.lowercase().contains(query) ||
                        path.lowercase().contains(query) ||
                        node.displayValue.lowercase().contains(query)
                if (matches) {
                    result.add(node)
                }
                when (tag) {
                    is NbtCompound -> {
                        tag.forEach { (k, child) ->
                            searchTraverse(child, k, tag, depth + 1, "$path/$k", -1)
                        }
                    }
                    is NbtList<*> -> {
                        tag.forEachIndexed { i, child ->
                            searchTraverse(child, "[$i]", tag, depth + 1, "$path/[$i]", i)
                        }
                    }
                    is NbtByteArray -> {
                        tag.value.forEachIndexed { i, b ->
                            searchTraverse(NbtByte(b), "[$i]", tag, depth + 1, "$path/[$i]", i)
                        }
                    }
                    is NbtIntArray -> {
                        tag.value.forEachIndexed { i, intVal ->
                            searchTraverse(NbtInt(intVal), "[$i]", tag, depth + 1, "$path/[$i]", i)
                        }
                    }
                    is NbtLongArray -> {
                        tag.value.forEachIndexed { i, l ->
                            searchTraverse(NbtLong(l), "[$i]", tag, depth + 1, "$path/[$i]", i)
                        }
                    }
                    else -> {}
                }
            }
            searchTraverse(root, rootKey, null, 0, rootKey, -1)
        } else {

            fun traverse(tag: NbtTag, key: String, parent: NbtTag?, depth: Int, path: String, index: Int) {
                val isExpanded = expandedPaths.contains(path)
                val node = NbtTreeNode(key, tag, parent, depth, isExpanded, path, index)
                result.add(node)

                if (isExpanded) {
                    when (tag) {
                        is NbtCompound -> {
                            tag.forEach { (k, child) ->
                                traverse(child, k, tag, depth + 1, "$path/$k", -1)
                            }
                        }
                        is NbtList<*> -> {
                            tag.forEachIndexed { i, child ->
                                traverse(child, "[$i]", tag, depth + 1, "$path/[$i]", i)
                            }
                        }
                        is NbtByteArray -> {
                            tag.value.forEachIndexed { i, b ->
                                traverse(NbtByte(b), "[$i]", tag, depth + 1, "$path/[$i]", i)
                            }
                        }
                        is NbtIntArray -> {
                            tag.value.forEachIndexed { i, intVal ->
                                traverse(NbtInt(intVal), "[$i]", tag, depth + 1, "$path/[$i]", i)
                            }
                        }
                        is NbtLongArray -> {
                            tag.value.forEachIndexed { i, l ->
                                traverse(NbtLong(l), "[$i]", tag, depth + 1, "$path/[$i]", i)
                            }
                        }
                        else -> {}
                    }
                }
            }
            traverse(root, rootKey, null, 0, rootKey, -1)
        }

        visibleNodes = result
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNbtNodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = visibleNodes[position]
        holder.bind(node)
    }

    override fun getItemCount(): Int = visibleNodes.size

    inner class ViewHolder(private val binding: ItemNbtNodeBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(node: NbtTreeNode) {
            val indentPx = (node.depth * 18 * context.resources.displayMetrics.density).toInt()
            binding.viewIndent.layoutParams.width = indentPx
            binding.viewIndent.requestLayout()

            if (node.isContainer) {
                binding.ivExpand.visibility = View.VISIBLE
                binding.ivExpand.rotation = if (node.isExpanded) 0f else -90f
                binding.ivExpand.setOnClickListener {
                    toggleExpand(node)
                }
            } else {
                binding.ivExpand.visibility = View.INVISIBLE
                binding.ivExpand.setOnClickListener(null)
            }

            binding.tvTypeBadge.text = node.tagTypeName
            bindTypeBadgeColor(node.tag)

            binding.tvTagKey.text = node.key
            binding.tvTagValue.text = node.displayValue

            binding.root.setOnClickListener {
                if (node.isContainer) {
                    toggleExpand(node)
                } else {
                    onNodeEdit(node)
                }
            }

            binding.btnNodeMenu.setOnClickListener { v ->
                showPopupMenu(v, node)
            }
        }

        private fun toggleExpand(node: NbtTreeNode) {
            if (searchQuery.isNotEmpty()) return
            if (expandedPaths.contains(node.path)) {
                expandedPaths.remove(node.path)
            } else {
                expandedPaths.add(node.path)
            }
            rebuildVisibleNodes()
        }

        private fun bindTypeBadgeColor(tag: NbtTag) {
            val color = when (tag) {
                is NbtCompound -> Color.parseColor("#7C4DFF")
                is NbtList<*> -> Color.parseColor("#00B0FF")
                is NbtString -> Color.parseColor("#00C853")
                is NbtByte, is NbtShort, is NbtInt, is NbtLong -> Color.parseColor("#FF9100")
                is NbtFloat, is NbtDouble -> Color.parseColor("#2979FF")
                is NbtByteArray, is NbtIntArray, is NbtLongArray -> Color.parseColor("#F50057")
                else -> Color.parseColor("#888888")
            }
            binding.tvTypeBadge.setTextColor(color)
        }

        private fun showPopupMenu(anchor: View, node: NbtTreeNode) {
            val popup = PopupMenu(context, anchor)
            val menu = popup.menu

            if (node.tag is NbtCompound || node.tag is NbtList<*> || node.tag is NbtByteArray || node.tag is NbtIntArray || node.tag is NbtLongArray) {
                menu.add(0, 1, 0, context.getString(R.string.nbt_add_tag))
            }
            if (!node.isContainer) {
                menu.add(0, 2, 1, context.getString(R.string.nbt_edit_value))
            }
            if (node.parentTag is NbtCompound) {
                menu.add(0, 3, 2, context.getString(R.string.nbt_rename_key))
            }
            if (node.parentTag != null) {
                menu.add(0, 4, 3, context.getString(R.string.nbt_delete_tag))
            }
            menu.add(0, 5, 4, context.getString(R.string.nbt_copy_path))
            if (!node.isContainer) {
                menu.add(0, 6, 5, context.getString(R.string.nbt_copy_value))
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onNodeAddChild(node)
                    2 -> onNodeEdit(node)
                    3 -> onNodeRename(node)
                    4 -> onNodeDelete(node)
                    5 -> onNodeCopyPath(node)
                    6 -> onNodeCopyValue(node)
                }
                true
            }
            popup.show()
        }
    }
}
