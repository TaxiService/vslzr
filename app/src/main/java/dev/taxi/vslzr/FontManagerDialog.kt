package dev.taxi.vslzr

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.ViewGroup
import android.widget.*

/**
 * Dialog for managing the font library.
 * Lists all fonts, create new, edit, delete.
 */
class FontManagerDialog(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit  // Called when fonts are modified
) {

    fun show() {
        val fonts = FontLibrary.loadFonts(prefs)
        val fontNames = fonts.keys.sorted()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(context).apply {
            text = "Font Library"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        })

        // Font list
        if (fontNames.isEmpty()) {
            layout.addView(TextView(context).apply {
                text = "No custom fonts yet.\nCreate one to get started!"
                setPadding(0, 16, 0, 16)
            })
        } else {
            layout.addView(TextView(context).apply {
                text = "Tap a font to edit:"
                setPadding(0, 8, 0, 8)
            })

            val scrollView = ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    400  // Max height
                )
            }
            val fontListLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            for (name in fontNames) {
                val font = fonts[name] ?: continue

                val fontRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }

                // Font info button
                val btn = Button(context).apply {
                    text = "$name (${font.width}×${font.height}, ${font.glyphs.size} chars)"
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                    setOnClickListener {
                        editFont(name)
                    }
                }
                fontRow.addView(btn)

                // Delete button
                val deleteBtn = Button(context).apply {
                    text = "×"
                    layoutParams = LinearLayout.LayoutParams(
                        100,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setOnClickListener {
                        confirmDelete(name)
                    }
                }
                fontRow.addView(deleteBtn)

                fontListLayout.addView(fontRow)
            }

            scrollView.addView(fontListLayout)
            layout.addView(scrollView)
        }

        // Create new font button
        layout.addView(Button(context).apply {
            text = "Create New Font"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 24
            }
            setOnClickListener {
                createNewFont()
            }
        })

        AlertDialog.Builder(context)
            .setTitle("Font Library")
            .setView(layout)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun createNewFont() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        layout.addView(TextView(context).apply {
            text = "Font Name:"
            setPadding(0, 0, 0, 8)
        })

        val nameInput = EditText(context).apply {
            hint = "e.g., 'retro_bold'"
        }
        layout.addView(nameInput)

        layout.addView(TextView(context).apply {
            text = "Grid Size:"
            setPadding(0, 24, 0, 8)
        })

        // Grid size selector
        val sizeLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        sizeLayout.addView(TextView(context).apply {
            text = "Width:"
            setPadding(0, 0, 16, 0)
        })

        val widthInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("3")
            layoutParams = LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        sizeLayout.addView(widthInput)

        sizeLayout.addView(TextView(context).apply {
            text = "  Height:"
            setPadding(24, 0, 16, 0)
        })

        val heightInput = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("5")
            layoutParams = LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        sizeLayout.addView(heightInput)

        layout.addView(sizeLayout)

        AlertDialog.Builder(context)
            .setTitle("Create New Font")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val width = widthInput.text.toString().toIntOrNull() ?: 3
                val height = heightInput.text.toString().toIntOrNull() ?: 5

                if (name.isEmpty()) {
                    Toast.makeText(context, "Please enter a font name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Check if name already exists
                if (FontLibrary.getFont(prefs, name) != null) {
                    Toast.makeText(context, "Font '$name' already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Create empty font
                val font = FontDefinition(
                    name = name,
                    width = width.coerceIn(1, 10),
                    height = height.coerceIn(1, 10),
                    glyphs = emptyMap()
                )

                FontLibrary.saveFont(prefs, font)
                onChanged()
                Toast.makeText(context, "Created font: $name", Toast.LENGTH_SHORT).show()

                // Open editor for the new font
                editFont(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun editFont(name: String) {
        val font = FontLibrary.getFont(prefs, name) ?: return

        // Open a new FontEditorDialog configured for this font
        NewFontEditorDialog(context, prefs, font) { updatedFont ->
            FontLibrary.saveFont(prefs, updatedFont)
            onChanged()
            Toast.makeText(context, "Font '$name' saved", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(context)
            .setTitle("Delete Font")
            .setMessage("Delete font '$name'?\n\nThis will reset any slots using this font to default.")
            .setPositiveButton("Delete") { _, _ ->
                FontLibrary.deleteFont(prefs, name)
                onChanged()
                Toast.makeText(context, "Deleted: $name", Toast.LENGTH_SHORT).show()
                show()  // Refresh the list
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
