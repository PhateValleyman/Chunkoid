package com.encore.chunkoid.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.com.gamemods.nbtmanipulator.AnvilRegionFile
import com.encore.chunkoid.R
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChunkListAdapter(
    private val context: Context,
    private val regionX: Int?,
    private val regionZ: Int?,
    private val onChunkClicked: (AnvilRegionFile.ChunkInfo) -> Unit,
    private val onChunkDeleteClicked: ((AnvilRegionFile.ChunkInfo) -> Unit)? = null
) : RecyclerView.Adapter<ChunkListAdapter.ViewHolder>() {

    private val allChunks = mutableListOf<AnvilRegionFile.ChunkInfo>()
    private val filteredChunks = mutableListOf<AnvilRegionFile.ChunkInfo>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun setData(chunks: List<AnvilRegionFile.ChunkInfo>) {
        allChunks.clear()
        allChunks.addAll(chunks)
        filter("")
    }

    fun removeChunk(localX: Int, localZ: Int) {
        val allIdx = allChunks.indexOfFirst { it.localX == localX && it.localZ == localZ }
        if (allIdx >= 0) {
            allChunks[allIdx] = AnvilRegionFile.ChunkInfo(localX, localZ, 0, 0, 0)
        }
        val filterIdx = filteredChunks.indexOfFirst { it.localX == localX && it.localZ == localZ }
        if (filterIdx >= 0) {
            filteredChunks.removeAt(filterIdx)
            notifyItemRemoved(filterIdx)
        }
    }

    fun filter(query: String) {
        filteredChunks.clear()
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            filteredChunks.addAll(allChunks.filter { it.exists })
        } else {
            val parts = q.replace(" ", "").split(",", "，", "_")
            if (parts.size >= 2) {
                val qX = parts[0].toIntOrNull()
                val qZ = parts[1].toIntOrNull()
                filteredChunks.addAll(allChunks.filter { chunk ->
                    val lx = chunk.localX
                    val lz = chunk.localZ
                    val gx = regionX?.let { chunk.getGlobalX(it) } ?: lx
                    val gz = regionZ?.let { chunk.getGlobalZ(it) } ?: lz
                    val bx = regionX?.let { chunk.getBlockStartX(it) } ?: (lx * 16)
                    val bz = regionZ?.let { chunk.getBlockStartZ(it) } ?: (lz * 16)

                    (qX != null && qZ != null && (
                        (lx == qX && lz == qZ) ||
                        (gx == qX && gz == qZ) ||
                        (qX in bx until bx + 16 && qZ in bz until bz + 16)
                    ))
                })
            } else {
                val num = q.toIntOrNull()
                filteredChunks.addAll(allChunks.filter { chunk ->
                    val gx = regionX?.let { chunk.getGlobalX(it) } ?: chunk.localX
                    val gz = regionZ?.let { chunk.getGlobalZ(it) } ?: chunk.localZ
                    chunk.exists && (
                        chunk.localX.toString() == q ||
                        chunk.localZ.toString() == q ||
                        gx.toString() == q ||
                        gz.toString() == q ||
                        (num != null && (chunk.localX == num || chunk.localZ == num))
                    )
                })
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_chunk_entry, parent, false)
        val card = view as? com.google.android.material.card.MaterialCardView
        if (card != null && com.encore.chunkoid.utils.BackgroundUtils.isCustomBackgroundEnabled(context)) {
            com.encore.chunkoid.utils.BackgroundUtils.applyGlassToCard(card, context)
            val isDark = com.encore.chunkoid.utils.BackgroundUtils.isSystemInDarkTheme(context)
            com.encore.chunkoid.utils.BackgroundUtils.applyUnifiedMonetTheme(card, isDark, context)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chunk = filteredChunks[position]
        val rx = regionX ?: 0
        val rz = regionZ ?: 0
        val gx = if (regionX != null) chunk.getGlobalX(rx) else chunk.localX
        val gz = if (regionZ != null) chunk.getGlobalZ(rz) else chunk.localZ
        val bx = if (regionX != null) chunk.getBlockStartX(rx) else chunk.localX * 16
        val bz = if (regionZ != null) chunk.getBlockStartZ(rz) else chunk.localZ * 16

        holder.tvChunkCoord.text = if (regionX != null && regionZ != null) {
            "全局区块: [$gx, $gz] (局部: [${chunk.localX}, ${chunk.localZ}])"
        } else {
            "区块: [${chunk.localX}, ${chunk.localZ}]"
        }

        holder.tvBlockCoord.text = "方块范围: [X: $bx..${bx + 15}, Z: $bz..${bz + 15}]"

        if (chunk.timestamp > 0) {
            val dateStr = dateFormat.format(Date(chunk.timestamp.toLong() * 1000L))
            holder.tvChunkTimestamp.text = "最后修改: $dateStr"
            holder.tvChunkTimestamp.visibility = View.VISIBLE
        } else {
            holder.tvChunkTimestamp.visibility = View.GONE
        }

        if (chunk.exists) {
            holder.chipStatus.text = "已生成 (${chunk.sectorCount} 扇区)"
            holder.chipStatus.setChipBackgroundColorResource(R.color.card_subtle_bg)
            holder.chipStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                onChunkDeleteClicked?.invoke(chunk)
            }
        } else {
            holder.chipStatus.text = "未生成"
            holder.chipStatus.setTextColor(ContextCompat.getColor(context, R.color.md_theme_outline))
            holder.btnDelete.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onChunkClicked(chunk)
        }
    }

    override fun getItemCount(): Int = filteredChunks.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvChunkCoord: TextView = itemView.findViewById(R.id.tv_chunk_coord)
        val tvBlockCoord: TextView = itemView.findViewById(R.id.tv_block_coord)
        val tvChunkTimestamp: TextView = itemView.findViewById(R.id.tv_chunk_timestamp)
        val chipStatus: Chip = itemView.findViewById(R.id.chip_chunk_status)
        val btnDelete: View = itemView.findViewById(R.id.btn_delete_chunk_item)
    }
}
