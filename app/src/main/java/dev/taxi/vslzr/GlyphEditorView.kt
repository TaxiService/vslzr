package dev.taxi.vslzr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Interactive grid editor for designing font glyphs with variable dimensions.
 * Tap cells to toggle pixels on/off.
 */
class GlyphEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Grid state (variable size)
    var gridWidth = 3
        private set
    var gridHeight = 5
        private set

    private var grid = Array(gridHeight) { BooleanArray(gridWidth) }

    var onGlyphChanged: ((Array<BooleanArray>) -> Unit)? = null

    private val paintFilled = Paint().apply {
        color = 0xFFFFFFFF.toInt()  // White for filled cells
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    private val paintEmpty = Paint().apply {
        color = 0xFF333333.toInt()  // Dark gray for empty cells
        style = Paint.Style.FILL
        isAntiAlias = false
    }

    private val paintGrid = Paint().apply {
        color = 0xFF666666.toInt()  // Grid lines
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = false
    }

    private val paintBorder = Paint().apply {
        color = 0xFFAAAAAA.toInt()  // Bright border
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = false
    }

    private var cellSize = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        ).coerceAtLeast(200)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gridMax = kotlin.math.max(gridWidth, gridHeight)
        cellSize = min(w, h) / gridMax.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw cells
        for (row in 0 until gridHeight) {
            for (col in 0 until gridWidth) {
                val left = col * cellSize
                val top = row * cellSize
                val right = left + cellSize
                val bottom = top + cellSize

                // Fill cell
                canvas.drawRect(
                    left, top, right, bottom,
                    if (grid[row][col]) paintFilled else paintEmpty
                )

                // Draw cell border
                canvas.drawRect(left, top, right, bottom, paintGrid)
            }
        }

        // Draw outer border
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paintBorder)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val col = (event.x / cellSize).toInt().coerceIn(0, gridWidth - 1)
                val row = (event.y / cellSize).toInt().coerceIn(0, gridHeight - 1)

                // Toggle the cell
                grid[row][col] = !grid[row][col]
                invalidate()
                onGlyphChanged?.invoke(grid)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** Set grid dimensions (clears current grid) */
    fun setGridSize(width: Int, height: Int) {
        gridWidth = width.coerceIn(1, 10)
        gridHeight = height.coerceIn(1, 10)
        grid = Array(gridHeight) { BooleanArray(gridWidth) }
        requestLayout()
        invalidate()
    }

    /** Set the glyph to display/edit */
    fun setGlyph(glyph: Array<BooleanArray>) {
        require(glyph.size == gridHeight) { "Glyph must have $gridHeight rows" }
        for (row in 0 until gridHeight) {
            require(glyph[row].size <= gridWidth) { "Glyph rows must be ≤$gridWidth columns" }
            for (col in 0 until gridWidth) {
                grid[row][col] = glyph[row].getOrNull(col) ?: false
            }
        }
        invalidate()
    }

    /** Get the current glyph state */
    fun getGlyph(): Array<BooleanArray> = grid.map { it.copyOf() }.toTypedArray()

    /** Convert glyph to bitmap strings (for storage) */
    fun toBitmapStrings(): Array<String> = Array(gridHeight) { row ->
        (0 until gridWidth).joinToString("") { col -> if (grid[row][col]) "1" else "0" }
    }

    /** Load glyph from bitmap strings */
    fun fromBitmapStrings(rows: Array<String>) {
        require(rows.size == gridHeight) { "Must provide $gridHeight rows" }
        for (row in 0 until gridHeight) {
            for (col in 0 until gridWidth) {
                grid[row][col] = rows[row].getOrNull(col) == '1'
            }
        }
        invalidate()
    }

    /** Clear the grid */
    fun clear() {
        for (row in 0 until gridHeight) {
            for (col in 0 until gridWidth) {
                grid[row][col] = false
            }
        }
        invalidate()
    }
}
