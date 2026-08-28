package com.encore.chunkoid.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import br.com.gamemods.nbtmanipulator.AnvilRegionFile
import com.encore.chunkoid.R
import com.encore.chunkoid.utils.BackgroundUtils
import kotlin.math.min

class ChunkGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val populatedChunks = BooleanArray(1024)
    var selectedX: Int = -1
        private set
    var selectedZ: Int = -1
        private set

    var onChunkSelectedListener: ((localX: Int, localZ: Int, exists: Boolean) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val emptyCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val populatedCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val majorGridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val selectionBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val cellRect = RectF()

    init {
        updateColors()
    }

    fun updateColors() {
        val isDark = BackgroundUtils.isSystemInDarkTheme(context)
        val primary = BackgroundUtils.getExtractedPrimaryColor(context)
            ?: ContextCompat.getColor(context, R.color.md_theme_primary)

        bgPaint.color = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(25, 0, 0, 0)
        emptyCellPaint.color = if (isDark) Color.argb(20, 255, 255, 255) else Color.argb(12, 0, 0, 0)
        populatedCellPaint.color = primary
        gridLinePaint.color = if (isDark) Color.argb(30, 255, 255, 255) else Color.argb(20, 0, 0, 0)
        majorGridLinePaint.color = if (isDark) Color.argb(70, 255, 255, 255) else Color.argb(50, 0, 0, 0)
        selectionBorderPaint.color = if (isDark) Color.WHITE else Color.BLACK
        invalidate()
    }

    fun setChunkData(chunks: List<AnvilRegionFile.ChunkInfo>) {
        populatedChunks.fill(false)
        for (chunk in chunks) {
            if (chunk.exists) {
                val idx = (chunk.localX and 31) + (chunk.localZ and 31) * 32
                populatedChunks[idx] = true
            }
        }

        if (selectedX == -1 || selectedZ == -1) {
            val first = chunks.firstOrNull { it.exists }
            if (first != null) {
                selectChunk(first.localX, first.localZ, notify = true)
            } else {
                selectChunk(0, 0, notify = true)
            }
        } else {
            invalidate()
        }
    }

    fun setChunkPopulated(localX: Int, localZ: Int, populated: Boolean) {
        if (localX in 0..31 && localZ in 0..31) {
            val idx = (localX and 31) + (localZ and 31) * 32
            populatedChunks[idx] = populated
            invalidate()
            if (selectedX == localX && selectedZ == localZ) {
                onChunkSelectedListener?.invoke(localX, localZ, populated)
            }
        }
    }

    fun refreshWithData(chunks: List<AnvilRegionFile.ChunkInfo>) {
        setChunkData(chunks)
        val exists = isChunkPopulated(selectedX, selectedZ)
        onChunkSelectedListener?.invoke(selectedX, selectedZ, exists)
    }

    fun selectChunk(localX: Int, localZ: Int, notify: Boolean = true) {
        val clampedX = localX.coerceIn(0, 31)
        val clampedZ = localZ.coerceIn(0, 31)
        if (selectedX != clampedX || selectedZ != clampedZ) {
            selectedX = clampedX
            selectedZ = clampedZ
            invalidate()
            if (notify) {
                val exists = isChunkPopulated(selectedX, selectedZ)
                onChunkSelectedListener?.invoke(selectedX, selectedZ, exists)
            }
        }
    }

    fun isChunkPopulated(localX: Int, localZ: Int): Boolean {
        if (localX !in 0..31 || localZ !in 0..31) return false
        return populatedChunks[(localX and 31) + (localZ and 31) * 32]
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width in 1 until height) width else if (height > 0) height else width
        val finalSize = if (size > 0) size else (300 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(finalSize, finalSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cellWidth = size / 32f
        val cellHeight = size / 32f
        val cornerRadius = 4f * resources.displayMetrics.density

        cellRect.set(0f, 0f, size, size)
        canvas.drawRoundRect(cellRect, cornerRadius * 2, cornerRadius * 2, bgPaint)

        for (z in 0 until 32) {
            for (x in 0 until 32) {
                val left = x * cellWidth + 1f
                val top = z * cellHeight + 1f
                val right = (x + 1) * cellWidth - 1f
                val bottom = (z + 1) * cellHeight - 1f

                val isPopulated = populatedChunks[x + z * 32]
                cellRect.set(left, top, right, bottom)

                if (isPopulated) {
                    canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, populatedCellPaint)
                } else {
                    canvas.drawRect(cellRect, emptyCellPaint)
                }
            }
        }

        for (i in 1..3) {
            val pos = i * 8 * cellWidth
            canvas.drawLine(pos, 0f, pos, size, majorGridLinePaint)
            canvas.drawLine(0f, pos, size, pos, majorGridLinePaint)
        }

        if (selectedX in 0..31 && selectedZ in 0..31) {
            val left = selectedX * cellWidth
            val top = selectedZ * cellHeight
            val right = (selectedX + 1) * cellWidth
            val bottom = (selectedZ + 1) * cellHeight

            cellRect.set(left, top, right, bottom)
            canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, selectionBorderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val size = min(width, height).toFloat()
                if (size > 0) {
                    val cellWidth = size / 32f
                    val cellHeight = size / 32f
                    val touchX = (event.x / cellWidth).toInt().coerceIn(0, 31)
                    val touchZ = (event.y / cellHeight).toInt().coerceIn(0, 31)
                    selectChunk(touchX, touchZ, notify = true)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
