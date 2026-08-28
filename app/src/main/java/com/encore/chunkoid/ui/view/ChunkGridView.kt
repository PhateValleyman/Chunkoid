package com.encore.chunkoid.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import br.com.gamemods.nbtmanipulator.AnvilRegionFile
import com.encore.chunkoid.R
import com.encore.chunkoid.utils.BackgroundUtils
import kotlin.math.max
import kotlin.math.min

class ChunkGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class SelectMode {
        TAP,
        BOX
    }

    var selectMode: SelectMode = SelectMode.TAP
        set(value) {
            field = value
            isDraggingBox = false
            invalidate()
        }

    var isWorldMode: Boolean = false
        private set

    var minX: Int = 0
        private set
    var maxX: Int = 31
        private set
    var minZ: Int = 0
        private set
    var maxZ: Int = 31
        private set

    val spanX: Int get() = max(1, maxX - minX + 1)
    val spanZ: Int get() = max(1, maxZ - minZ + 1)

    private val populatedChunkSet = mutableSetOf<Pair<Int, Int>>()
    private val blockEntityChunkSet = mutableSetOf<Pair<Int, Int>>()

    val selectedChunks = mutableSetOf<Pair<Int, Int>>()

    var selectedX: Int = -1
        private set
    var selectedZ: Int = -1
        private set

    var spawnChunkX: Int? = null
    var spawnChunkZ: Int? = null

    private var isDraggingBox = false
    private var dragStartCoordX: Int = 0
    private var dragStartCoordZ: Int = 0
    private var dragCurrentCoordX: Int = 0
    private var dragCurrentCoordZ: Int = 0

    var onChunkSelectedListener: ((chunkX: Int, chunkZ: Int, exists: Boolean) -> Unit)? = null
    var onSelectionChangedListener: ((selectedCount: Int, selectedChunks: Set<Pair<Int, Int>>) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val emptyCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val populatedCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val blockEntityDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFD54F")
    }
    private val selectedCellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(160, 244, 67, 54)
    }
    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#EF5350")
    }
    private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val majorGridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val focusBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val dragBoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 33, 150, 243)
    }
    private val dragBoxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#2196F3")
        pathEffect = DashPathEffect(floatArrayOf(12f, 6f), 0f)
    }
    private val spawnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#00E676")
    }

    private val cellRect = RectF()
    private val dragRect = RectF()

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
        focusBorderPaint.color = if (isDark) Color.WHITE else Color.BLACK
        invalidate()
    }

    fun setChunkData(chunks: List<AnvilRegionFile.ChunkInfo>) {
        isWorldMode = false
        minX = 0
        maxX = 31
        minZ = 0
        maxZ = 31
        populatedChunkSet.clear()
        blockEntityChunkSet.clear()
        for (chunk in chunks) {
            if (chunk.exists) {
                populatedChunkSet.add(Pair(chunk.localX, chunk.localZ))
            }
        }

        if (selectedX == -1 || selectedZ == -1) {
            val first = chunks.firstOrNull { it.exists }
            if (first != null) {
                selectSingleChunk(first.localX, first.localZ, notify = true)
            } else {
                selectSingleChunk(0, 0, notify = true)
            }
        } else {
            invalidate()
        }
    }

    fun setChunkPopulated(chunkX: Int, chunkZ: Int, populated: Boolean) {
        val coord = Pair(chunkX, chunkZ)
        if (populated) {
            populatedChunkSet.add(coord)
        } else {
            populatedChunkSet.remove(coord)
            selectedChunks.remove(coord)
        }
        invalidate()
    }

    fun refreshWithData(chunks: List<AnvilRegionFile.ChunkInfo>) {
        setChunkData(chunks)
    }

    fun setWorldChunkData(
        chunkCoords: Collection<Pair<Int, Int>>,
        blockEntityCoords: Collection<Pair<Int, Int>> = emptyList(),
        spawnX: Int? = null,
        spawnZ: Int? = null
    ) {
        isWorldMode = true
        populatedChunkSet.clear()
        populatedChunkSet.addAll(chunkCoords)
        blockEntityChunkSet.clear()
        blockEntityChunkSet.addAll(blockEntityCoords)
        spawnChunkX = spawnX
        spawnChunkZ = spawnZ

        if (chunkCoords.isNotEmpty()) {
            val minCalculatedX = chunkCoords.minOf { it.first }
            val maxCalculatedX = chunkCoords.maxOf { it.first }
            val minCalculatedZ = chunkCoords.minOf { it.second }
            val maxCalculatedZ = chunkCoords.maxOf { it.second }

            val padX = max(1, (maxCalculatedX - minCalculatedX) / 10)
            val padZ = max(1, (maxCalculatedZ - minCalculatedZ) / 10)

            minX = minCalculatedX - padX
            maxX = maxCalculatedX + padX
            minZ = minCalculatedZ - padZ
            maxZ = maxCalculatedZ + padZ
        } else {
            minX = -16
            maxX = 16
            minZ = -16
            maxZ = 16
        }

        selectedChunks.clear()
        val first = chunkCoords.firstOrNull()
        if (first != null) {
            selectSingleChunk(first.first, first.second, notify = true)
        } else {
            selectSingleChunk(0, 0, notify = true)
        }
        invalidate()
    }

    fun selectSingleChunk(cx: Int, cz: Int, notify: Boolean = true) {
        val clampedX = cx.coerceIn(minX, maxX)
        val clampedZ = cz.coerceIn(minZ, maxZ)
        selectedX = clampedX
        selectedZ = clampedZ
        invalidate()
        if (notify) {
            val exists = isChunkPopulated(selectedX, selectedZ)
            onChunkSelectedListener?.invoke(selectedX, selectedZ, exists)
        }
    }

    fun isChunkPopulated(cx: Int, cz: Int): Boolean {
        return populatedChunkSet.contains(Pair(cx, cz))
    }

    fun toggleChunkSelection(cx: Int, cz: Int) {
        val coord = Pair(cx, cz)
        if (selectedChunks.contains(coord)) {
            selectedChunks.remove(coord)
        } else {
            selectedChunks.add(coord)
        }
        selectSingleChunk(cx, cz, notify = true)
        onSelectionChangedListener?.invoke(selectedChunks.size, selectedChunks)
        invalidate()
    }

    fun selectAllPopulated() {
        selectedChunks.clear()
        selectedChunks.addAll(populatedChunkSet)
        onSelectionChangedListener?.invoke(selectedChunks.size, selectedChunks)
        invalidate()
    }

    fun clearSelection() {
        selectedChunks.clear()
        onSelectionChangedListener?.invoke(0, selectedChunks)
        invalidate()
    }

    fun invertSelection() {
        val newSelection = populatedChunkSet.filter { !selectedChunks.contains(it) }.toMutableSet()
        selectedChunks.clear()
        selectedChunks.addAll(newSelection)
        onSelectionChangedListener?.invoke(selectedChunks.size, selectedChunks)
        invalidate()
    }

    fun selectBox(startX: Int, startZ: Int, endX: Int, endZ: Int, toggle: Boolean = false) {
        val x0 = min(startX, endX)
        val x1 = max(startX, endX)
        val z0 = min(startZ, endZ)
        val z1 = max(startZ, endZ)

        val boxArea = (x1 - x0 + 1L) * (z1 - z0 + 1L)
        if (boxArea < populatedChunkSet.size) {
            for (cz in z0..z1) {
                for (cx in x0..x1) {
                    val coord = Pair(cx, cz)
                    if (populatedChunkSet.contains(coord)) {
                        if (toggle) {
                            if (selectedChunks.contains(coord)) selectedChunks.remove(coord)
                            else selectedChunks.add(coord)
                        } else {
                            selectedChunks.add(coord)
                        }
                    }
                }
            }
        } else {
            for (coord in populatedChunkSet) {
                if (coord.first in x0..x1 && coord.second in z0..z1) {
                    if (toggle) {
                        if (selectedChunks.contains(coord)) selectedChunks.remove(coord)
                        else selectedChunks.add(coord)
                    } else {
                        selectedChunks.add(coord)
                    }
                }
            }
        }
        onSelectionChangedListener?.invoke(selectedChunks.size, selectedChunks)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width in 1 until height) width else if (height > 0) height else width
        val finalSize = if (size > 0) size else (320 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(finalSize, finalSize)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cellsX = spanX
        val cellsZ = spanZ
        val cellWidth = size / cellsX.toFloat()
        val cellHeight = size / cellsZ.toFloat()
        val cornerRadius = 4f * resources.displayMetrics.density
        val useRounded = cellWidth >= 14f && cellHeight >= 14f

        cellRect.set(0f, 0f, size, size)
        canvas.drawRoundRect(cellRect, cornerRadius * 2, cornerRadius * 2, bgPaint)

        if (!isWorldMode && cellsX == 32 && cellsZ == 32) {
            for (cz in minZ..maxZ) {
                val row = cz - minZ
                val top = row * cellHeight + 0.8f
                val bottom = (row + 1) * cellHeight - 0.8f
                for (cx in minX..maxX) {
                    val col = cx - minX
                    val left = col * cellWidth + 0.8f
                    val right = (col + 1) * cellWidth - 0.8f

                    val coord = Pair(cx, cz)
                    val isPopulated = populatedChunkSet.contains(coord)
                    val isSelected = selectedChunks.contains(coord)

                    cellRect.set(left, top, right, bottom)
                    if (isSelected) {
                        if (useRounded) {
                            canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, selectedCellPaint)
                            canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, selectedBorderPaint)
                        } else {
                            canvas.drawRect(cellRect, selectedCellPaint)
                            canvas.drawRect(cellRect, selectedBorderPaint)
                        }
                    } else if (isPopulated) {
                        if (useRounded) {
                            canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, populatedCellPaint)
                        } else {
                            canvas.drawRect(cellRect, populatedCellPaint)
                        }
                    } else {
                        canvas.drawRect(cellRect, emptyCellPaint)
                    }

                    if (blockEntityChunkSet.contains(coord)) {
                        val dotRadius = min(cellWidth, cellHeight) * 0.2f
                        canvas.drawCircle(cellRect.centerX(), cellRect.centerY(), dotRadius, blockEntityDotPaint)
                    }

                    if (spawnChunkX == cx && spawnChunkZ == cz) {
                        val spawnRadius = min(cellWidth, cellHeight) * 0.35f
                        canvas.drawCircle(cellRect.centerX(), cellRect.centerY(), spawnRadius, spawnPaint)
                    }
                }
            }

            for (i in 1..3) {
                val pos = i * 8 * cellWidth
                canvas.drawLine(pos, 0f, pos, size, majorGridLinePaint)
                canvas.drawLine(0f, pos, size, pos, majorGridLinePaint)
            }
        } else {
            val columns = spanX
            val rows = spanZ

            if (cellWidth >= 2f && cellHeight >= 2f) {
                for (i in 0..columns) {
                    val pos = i * cellWidth
                    canvas.drawLine(pos, 0f, pos, size, gridLinePaint)
                }
                for (i in 0..rows) {
                    val pos = i * cellHeight
                    canvas.drawLine(0f, pos, size, pos, gridLinePaint)
                }
            }

            for (coord in populatedChunkSet) {
                val cx = coord.first
                val cz = coord.second
                if (cx !in minX..maxX || cz !in minZ..maxZ) continue
                val col = cx - minX
                val row = cz - minZ
                val left = col * cellWidth + (if (useRounded) 0.8f else 0f)
                val top = row * cellHeight + (if (useRounded) 0.8f else 0f)
                val right = (col + 1) * cellWidth - (if (useRounded) 0.8f else 0f)
                val bottom = (row + 1) * cellHeight - (if (useRounded) 0.8f else 0f)
                cellRect.set(left, top, right, bottom)
                if (useRounded) {
                    canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, populatedCellPaint)
                } else {
                    canvas.drawRect(cellRect, populatedCellPaint)
                }
            }

            for (coord in selectedChunks) {
                val cx = coord.first
                val cz = coord.second
                if (cx !in minX..maxX || cz !in minZ..maxZ) continue
                val col = cx - minX
                val row = cz - minZ
                val left = col * cellWidth + (if (useRounded) 0.8f else 0f)
                val top = row * cellHeight + (if (useRounded) 0.8f else 0f)
                val right = (col + 1) * cellWidth - (if (useRounded) 0.8f else 0f)
                val bottom = (row + 1) * cellHeight - (if (useRounded) 0.8f else 0f)
                cellRect.set(left, top, right, bottom)
                if (useRounded) {
                    canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, selectedCellPaint)
                    canvas.drawRoundRect(cellRect, cornerRadius * 0.5f, cornerRadius * 0.5f, selectedBorderPaint)
                } else {
                    canvas.drawRect(cellRect, selectedCellPaint)
                    canvas.drawRect(cellRect, selectedBorderPaint)
                }
            }

            if (cellWidth >= 4f) {
                val dotRadius = max(1.5f, min(cellWidth, cellHeight) * 0.2f)
                for (coord in blockEntityChunkSet) {
                    val cx = coord.first
                    val cz = coord.second
                    if (cx !in minX..maxX || cz !in minZ..maxZ) continue
                    val col = cx - minX
                    val row = cz - minZ
                    val centerX = (col + 0.5f) * cellWidth
                    val centerY = (row + 0.5f) * cellHeight
                    canvas.drawCircle(centerX, centerY, dotRadius, blockEntityDotPaint)
                }
            }

            val sx = spawnChunkX
            val sz = spawnChunkZ
            if (sx != null && sz != null && sx in minX..maxX && sz in minZ..maxZ) {
                val col = sx - minX
                val row = sz - minZ
                val centerX = (col + 0.5f) * cellWidth
                val centerY = (row + 0.5f) * cellHeight
                val spawnRadius = max(3f, min(cellWidth, cellHeight) * 0.35f)
                canvas.drawCircle(centerX, centerY, spawnRadius, spawnPaint)
            }
        }

        if (selectedX in minX..maxX && selectedZ in minZ..maxZ) {
            val col = selectedX - minX
            val row = selectedZ - minZ
            val left = col * cellWidth
            val top = row * cellHeight
            val right = (col + 1) * cellWidth
            val bottom = (row + 1) * cellHeight

            cellRect.set(left, top, right, bottom)
            canvas.drawRoundRect(cellRect, cornerRadius, cornerRadius, focusBorderPaint)
        }

        if (isDraggingBox) {
            val startCol = dragStartCoordX - minX
            val startRow = dragStartCoordZ - minZ
            val curCol = dragCurrentCoordX - minX
            val curRow = dragCurrentCoordZ - minZ

            val left = min(startCol, curCol) * cellWidth
            val top = min(startRow, curRow) * cellHeight
            val right = (max(startCol, curCol) + 1) * cellWidth
            val bottom = (max(startRow, curRow) + 1) * cellHeight

            dragRect.set(left, top, right, bottom)
            canvas.drawRoundRect(dragRect, cornerRadius, cornerRadius, dragBoxFillPaint)
            canvas.drawRoundRect(dragRect, cornerRadius, cornerRadius, dragBoxStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val size = min(width, height).toFloat()
        if (size <= 0) return super.onTouchEvent(event)

        val cellWidth = size / spanX.toFloat()
        val cellHeight = size / spanZ.toFloat()

        val touchCoordX = (minX + (event.x / cellWidth).toInt()).coerceIn(minX, maxX)
        val touchCoordZ = (minZ + (event.y / cellHeight).toInt()).coerceIn(minZ, maxZ)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (selectMode == SelectMode.BOX) {
                    isDraggingBox = true
                    dragStartCoordX = touchCoordX
                    dragStartCoordZ = touchCoordZ
                    dragCurrentCoordX = touchCoordX
                    dragCurrentCoordZ = touchCoordZ
                    invalidate()
                } else {
                    toggleChunkSelection(touchCoordX, touchCoordZ)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selectMode == SelectMode.BOX && isDraggingBox) {
                    if (dragCurrentCoordX != touchCoordX || dragCurrentCoordZ != touchCoordZ) {
                        dragCurrentCoordX = touchCoordX
                        dragCurrentCoordZ = touchCoordZ
                        invalidate()
                    }
                } else if (selectMode == SelectMode.TAP) {
                    selectSingleChunk(touchCoordX, touchCoordZ, notify = true)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (selectMode == SelectMode.BOX && isDraggingBox) {
                    isDraggingBox = false
                    selectBox(dragStartCoordX, dragStartCoordZ, dragCurrentCoordX, dragCurrentCoordZ, toggle = false)
                    selectSingleChunk(dragCurrentCoordX, dragCurrentCoordZ, notify = true)
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDraggingBox = false
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
