package com.encore.chunkoid.ui.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.encore.chunkoid.R
import com.encore.chunkoid.core.leveldb.LevelDbCategory
import com.encore.chunkoid.core.leveldb.LevelDbRecord
import com.encore.chunkoid.utils.BackgroundUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

class LevelDbRecordAdapter(
    private val context: Context,
    private val onRecordClicked: (LevelDbRecord) -> Unit
) : RecyclerView.Adapter<LevelDbRecordAdapter.ViewHolder>() {

    private val allRecords = mutableListOf<LevelDbRecord>()
    private val filteredRecords = mutableListOf<LevelDbRecord>()

    private var currentCategory: LevelDbCategory = LevelDbCategory.ALL
    private var currentQuery: String = ""

    fun setData(records: List<LevelDbRecord>) {
        allRecords.clear()
        allRecords.addAll(records)
        applyFilter()
    }

    fun setCategory(category: LevelDbCategory) {
        this.currentCategory = category
        applyFilter()
    }

    fun filter(query: String) {
        this.currentQuery = query.trim().lowercase()
        applyFilter()
    }

    fun updateRecord(updated: LevelDbRecord) {
        val allIndex = allRecords.indexOfFirst { it.key.contentEquals(updated.key) }
        if (allIndex >= 0) {
            allRecords[allIndex] = updated
        }
        val filterIndex = filteredRecords.indexOfFirst { it.key.contentEquals(updated.key) }
        if (filterIndex >= 0) {
            filteredRecords[filterIndex] = updated
            notifyItemChanged(filterIndex)
        }
    }

    fun getFilteredCount(): Int = filteredRecords.size
    fun getAllCount(): Int = allRecords.size

    private fun applyFilter() {
        filteredRecords.clear()
        val q = currentQuery
        val cat = currentCategory

        filteredRecords.addAll(allRecords.filter { record ->
            val matchCategory = (cat == LevelDbCategory.ALL || record.category == cat)
            if (!matchCategory) return@filter false

            if (q.isEmpty()) return@filter true

            val parts = q.replace(" ", "").split(",", "，", "_")
            if (parts.size >= 2) {
                val qX = parts[0].toIntOrNull()
                val qZ = parts[1].toIntOrNull()
                if (qX != null && qZ != null && record.chunkX == qX && record.chunkZ == qZ) {
                    return@filter true
                }
            }

            record.displayName.lowercase().contains(q) ||
            record.keyString.lowercase().contains(q) ||
            record.keyToHex().contains(q) ||
            (record.chunkX != null && record.chunkX.toString() == q) ||
            (record.chunkZ != null && record.chunkZ.toString() == q)
        })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_leveldb_record, parent, false)
        val card = view as? MaterialCardView
        if (card != null && BackgroundUtils.isCustomBackgroundEnabled(context)) {
            BackgroundUtils.applyGlassToCard(card, context)
            val isDark = BackgroundUtils.isSystemInDarkTheme(context)
            BackgroundUtils.applyUnifiedMonetTheme(card, isDark, context)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = filteredRecords[position]

        holder.tvTitle.text = record.displayName

        val sizeStr = formatBytes(record.valueSize.toLong())
        val keyDisplay = if (record.keyString.isNotEmpty() && record.keyString.all { it.isLetterOrDigit() || it in "_-~.: " }) {
            record.keyString
        } else {
            "0x" + record.keyToHex()
        }
        holder.tvSubtitle.text = "大小: $sizeStr | Key: $keyDisplay"

        holder.chipNbt.visibility = if (record.isNbt) View.VISIBLE else View.GONE
        holder.chipCategory.text = record.category.title

        val categoryColor = when (record.category) {
            LevelDbCategory.PLAYER -> Color.parseColor("#FF9100")
            LevelDbCategory.ENTITY -> Color.parseColor("#F50057")
            LevelDbCategory.BLOCK_ENTITY -> Color.parseColor("#7C4DFF")
            LevelDbCategory.WORLD -> Color.parseColor("#00B0FF")
            LevelDbCategory.CHUNK -> Color.parseColor("#00C853")
            else -> ContextCompat.getColor(context, R.color.md_theme_outline)
        }

        (holder.vIndicator.background as? GradientDrawable)?.setColor(categoryColor)
            ?: holder.vIndicator.setBackgroundColor(categoryColor)

        val isCustomBg = BackgroundUtils.isCustomBackgroundEnabled(context)
        if (isCustomBg) {
            val primaryColor = if (BackgroundUtils.isColorExtractionEnabled(context)) {
                BackgroundUtils.getExtractedPrimaryColor(context) ?: ContextCompat.getColor(context, R.color.md_theme_primary)
            } else {
                ContextCompat.getColor(context, R.color.md_theme_primary)
            }

            holder.chipNbt.chipBackgroundColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 40))
            holder.chipNbt.setTextColor(primaryColor)
            holder.chipNbt.chipStrokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primaryColor, 80))
            holder.chipNbt.chipStrokeWidth = (1 * context.resources.displayMetrics.density).coerceAtLeast(1f)

            holder.chipCategory.chipBackgroundColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(categoryColor, 40))
            holder.chipCategory.setTextColor(categoryColor)
            holder.chipCategory.chipStrokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(categoryColor, 80))
            holder.chipCategory.chipStrokeWidth = (1 * context.resources.displayMetrics.density).coerceAtLeast(1f)
        }

        holder.itemView.setOnClickListener {
            onRecordClicked(record)
        }
    }

    override fun getItemCount(): Int = filteredRecords.size

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val vIndicator: View = itemView.findViewById(R.id.v_category_indicator)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_record_title)
        val tvSubtitle: TextView = itemView.findViewById(R.id.tv_record_subtitle)
        val chipNbt: Chip = itemView.findViewById(R.id.chip_nbt_badge)
        val chipCategory: Chip = itemView.findViewById(R.id.chip_category_badge)
    }
}
