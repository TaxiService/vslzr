package dev.taxi.vslzr

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * Dialog for editing custom font glyphs.
 * Shows a grid editor and character selector.
 */
class FontEditorDialog(
    private val context: Context,
    private val onSave: (Map<Char, Array<String>>) -> Unit
) {

    private val editedGlyphs = mutableMapOf<Char, Array<String>>()

    // Characters that can be edited
    private val editableChars = "0123456789:-%ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

    private var currentChar = '0'

    fun show(existingFont: Map<Char, Array<String>> = emptyMap()) {
        // Load existing font
        editedGlyphs.clear()
        editedGlyphs.putAll(existingFont)

        val view = createView()

        AlertDialog.Builder(context)
            .setTitle("Font Editor")
            .setView(view)
            .setPositiveButton("Save All") { _, _ ->
                onSave(editedGlyphs.toMap())
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Default") { _, _ ->
                onSave(emptyMap())  // Clear custom font
            }
            .show()
    }

    private fun createView(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Create UI elements
        val charLabel = TextView(context).apply {
            text = "Editing: $currentChar"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }

        val editor = GlyphEditorView(context)
        val preview = GlyphPreviewView(context)

        // Helper to reload current character
        fun loadCurrentChar() {
            val rows = editedGlyphs[currentChar] ?: getDefaultGlyph(currentChar)
            editor.fromBitmapStrings(rows)
            preview.setGlyph(editor.getGlyph())
            charLabel.text = "Editing: $currentChar"
        }

        // Character picker (horizontal scroll of buttons)
        val charScrollView = HorizontalScrollView(context)
        val charButtonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (ch in editableChars) {
            val btn = Button(context).apply {
                text = ch.toString()
                isAllCaps = false
                setPadding(24, 16, 24, 16)
                setOnClickListener {
                    currentChar = ch
                    loadCurrentChar()
                }
            }
            charButtonLayout.addView(btn)
        }
        charScrollView.addView(charButtonLayout)

        // Configure editor
        editor.apply {
            layoutParams = ViewGroup.LayoutParams(600, 600)
            onGlyphChanged = { glyph ->
                editedGlyphs[currentChar] = Array(5) { row ->
                    (0 until 5).joinToString("") { col ->
                        if (glyph[row][col]) "1" else "0"
                    }
                }
                preview.setGlyph(glyph)
            }
        }

        preview.layoutParams = ViewGroup.LayoutParams(100, 100)

        // Add to layout
        layout.addView(charLabel)
        layout.addView(charScrollView)
        layout.addView(editor, ViewGroup.MarginLayoutParams(600, 600).apply {
            topMargin = 32
            bottomMargin = 16
        })
        layout.addView(TextView(context).apply {
            text = "Preview (actual size):"
            textSize = 14f
            setPadding(0, 16, 0, 8)
        })
        layout.addView(preview)

        // Action buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }

        buttonLayout.addView(Button(context).apply {
            text = "Clear"
            setOnClickListener {
                editor.clear()
                editedGlyphs[currentChar] = Array(5) { "00000" }
                preview.setGlyph(editor.getGlyph())
            }
        })

        buttonLayout.addView(Button(context).apply {
            text = "Revert"
            setOnClickListener {
                loadCurrentChar()
            }
        })

        layout.addView(buttonLayout)

        // Load initial character
        loadCurrentChar()

        return layout
    }

    /** Tiny preview view showing glyph at actual size */
    private inner class GlyphPreviewView(context: Context) : View(context) {
        private var glyph = Array(5) { BooleanArray(5) }

        private val paint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            isAntiAlias = false
        }

        fun setGlyph(g: Array<BooleanArray>) {
            glyph = g.map { it.copyOf() }.toTypedArray()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cellSize = 10f  // Small pixels
            for (row in 0 until 5) {
                for (col in 0 until 5) {
                    if (glyph[row][col]) {
                        canvas.drawRect(
                            col * cellSize,
                            row * cellSize,
                            (col + 1) * cellSize,
                            (row + 1) * cellSize,
                            paint
                        )
                    }
                }
            }
        }
    }

    /** Get default glyph for a character (from existing font or blank) */
    private fun getDefaultGlyph(ch: Char): Array<String> {
        // 3×5 default glyphs (slim, matches default grid)
        return when (ch) {
            '0' -> arrayOf("010", "101", "101", "101", "010")
            '1' -> arrayOf("001", "011", "001", "001", "011")
            '2' -> arrayOf("001", "010", "001", "100", "101")
            '3' -> arrayOf("110", "001", "010", "001", "110")
            '4' -> arrayOf("110", "101", "111", "001", "001")
            '5' -> arrayOf("010", "100", "110", "001", "010")
            '6' -> arrayOf("101", "100", "110", "101", "010")
            '7' -> arrayOf("111", "001", "010", "100", "100")
            '8' -> arrayOf("010", "101", "010", "101", "010")
            '9' -> arrayOf("011", "101", "011", "001", "001")
            ':' -> arrayOf("000", "010", "000", "010", "000")
            '%' -> arrayOf("100", "001", "010", "100", "001")
            '-' -> arrayOf("000", "000", "111", "000", "000")
            'F' -> arrayOf("111", "100", "110", "100", "100")
            'U' -> arrayOf("101", "101", "101", "101", "111")
            'L' -> arrayOf("100", "100", "100", "100", "111")
            else -> Array(5) { "000" }  // Blank 3-wide for new chars
        }
    }
}
