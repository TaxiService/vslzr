package dev.taxi.vslzr

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * Improved font editor for FontDefinition objects.
 * Supports variable grid sizes and character set management.
 */
class NewFontEditorDialog(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val font: FontDefinition,
    private val onSave: (FontDefinition) -> Unit
) {

    private val editedGlyphs = font.glyphs.toMutableMap()
    private var currentChar = '0'

    // Common characters for editing
    private val commonChars = "0123456789:%-".toCharArray()

    fun show() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Font info header
        layout.addView(TextView(context).apply {
            text = "${font.name}\nGrid: ${font.width}×${font.height}"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        })

        // Character selector label
        layout.addView(TextView(context).apply {
            text = "Editing:"
            textSize = 14f
            setPadding(0, 8, 0, 4)
        })

        val charLabel = TextView(context).apply {
            text = currentChar.toString()
            textSize = 24f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(charLabel)

        // Grid editor
        val editor = GlyphEditorView(context).apply {
            setGridSize(font.width, font.height)
            layoutParams = ViewGroup.LayoutParams(600, 600)
        }

        // Preview
        val preview = object : View(context) {
            private var glyph = Array(font.height) { BooleanArray(font.width) }

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
                val cellSize = 10f
                for (row in glyph.indices) {
                    for (col in 0 until glyph[row].size) {
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
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                font.width * 10 + 20,
                font.height * 10 + 20
            )
        }

        // Load current character helper
        fun loadCurrentChar() {
            charLabel.text = "Character: $currentChar"
            val rows = editedGlyphs[currentChar] ?: getDefaultGlyph(currentChar, font.width, font.height)
            editor.fromBitmapStrings(rows)
            preview.setGlyph(editor.getGlyph())
        }

        // Editor callback
        editor.onGlyphChanged = { glyph ->
            editedGlyphs[currentChar] = Array(font.height) { row ->
                (0 until font.width).joinToString("") { col ->
                    if (glyph[row][col]) "1" else "0"
                }
            }
            preview.setGlyph(glyph)
        }

        // Character picker (common chars)
        val charScrollView = HorizontalScrollView(context)
        val charButtonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (ch in commonChars) {
            charButtonLayout.addView(Button(context).apply {
                text = ch.toString()
                isAllCaps = false
                setPadding(24, 16, 24, 16)
                setOnClickListener {
                    currentChar = ch
                    loadCurrentChar()
                }
            })
        }

        charScrollView.addView(charButtonLayout)
        layout.addView(charScrollView)

        // Add custom character button
        layout.addView(Button(context).apply {
            text = "Add Custom Character"
            setOnClickListener {
                val input = EditText(context).apply {
                    hint = "Enter character (e.g., A, @, #)"
                }
                AlertDialog.Builder(context)
                    .setTitle("Add Character")
                    .setView(input)
                    .setPositiveButton("Add") { _, _ ->
                        val text = input.text.toString()
                        if (text.length == 1) {
                            currentChar = text[0]
                            loadCurrentChar()
                        }
                    }
                    .show()
            }
        })

        // Editor and preview
        layout.addView(editor, ViewGroup.MarginLayoutParams(600, 600).apply {
            topMargin = 24
        })

        layout.addView(TextView(context).apply {
            text = "Preview (actual size):"
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
                editedGlyphs[currentChar] = Array(font.height) { "0".repeat(font.width) }
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

        AlertDialog.Builder(context)
            .setTitle("Edit Font: ${font.name}")
            .setView(ScrollView(context).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                val updatedFont = font.copy(glyphs = editedGlyphs.toMap())
                onSave(updatedFont)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getDefaultGlyph(ch: Char, width: Int, height: Int): Array<String> {
        // If we have a 3×5 glyph, use it
        if (width == 3 && height == 5) {
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
                else -> Array(height) { "0".repeat(width) }
            }
        }

        // Otherwise return blank
        return Array(height) { "0".repeat(width) }
    }
}
