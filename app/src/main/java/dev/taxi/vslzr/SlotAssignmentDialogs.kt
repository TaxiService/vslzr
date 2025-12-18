package dev.taxi.vslzr

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.*

/**
 * Dialogs for assigning fonts to clock and battery display slots.
 * This enables asymmetric, artistic displays like retro Casio watches!
 */

/** Clock slot assignment: HH:MM with 5 independent font slots */
class ClockSlotAssignmentDialog(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit
) {

    fun show() {
        val fonts = FontLibrary.loadFonts(prefs)
        val fontNames = listOf("[Default]") + fonts.keys.sorted()
        val config = FontLibrary.loadClockSlots(prefs)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        layout.addView(TextView(context).apply {
            text = "Assign fonts to each clock position.\n\n" +
                   "Create funky asymmetric displays where each digit has its own style!"
            setPadding(0, 0, 0, 24)
        })

        // Slot selectors
        val hourTensSpinner = createFontSpinner(fontNames, config.hourTens)
        val hourOnesSpinner = createFontSpinner(fontNames, config.hourOnes)
        val colonSpinner = createFontSpinner(fontNames, config.colon)
        val minuteTensSpinner = createFontSpinner(fontNames, config.minuteTens)
        val minuteOnesSpinner = createFontSpinner(fontNames, config.minuteOnes)

        addSlotRow(layout, "Hour (tens):", hourTensSpinner, "e.g., '1' in 12:34")
        addSlotRow(layout, "Hour (ones):", hourOnesSpinner, "e.g., '2' in 12:34")
        addSlotRow(layout, "Colon:", colonSpinner, "e.g., ':' in 12:34")
        addSlotRow(layout, "Minute (tens):", minuteTensSpinner, "e.g., '3' in 12:34")
        addSlotRow(layout, "Minute (ones):", minuteOnesSpinner, "e.g., '4' in 12:34")

        AlertDialog.Builder(context)
            .setTitle("Clock Font Slots")
            .setView(ScrollView(context).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                val newConfig = ClockSlotConfig(
                    hourTens = getSelectedFont(hourTensSpinner, fontNames),
                    hourOnes = getSelectedFont(hourOnesSpinner, fontNames),
                    colon = getSelectedFont(colonSpinner, fontNames),
                    minuteTens = getSelectedFont(minuteTensSpinner, fontNames),
                    minuteOnes = getSelectedFont(minuteOnesSpinner, fontNames)
                )
                FontLibrary.saveClockSlots(prefs, newConfig)
                onChanged()
                Toast.makeText(context, "Clock slots saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Default") { _, _ ->
                FontLibrary.saveClockSlots(prefs, ClockSlotConfig())
                onChanged()
                Toast.makeText(context, "Reset to default", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun createFontSpinner(fontNames: List<String>, selectedFont: String?): Spinner {
        return Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, fontNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            // Select current font
            val index = if (selectedFont == null) 0 else {
                val idx = fontNames.indexOf(selectedFont)
                if (idx >= 0) idx else 0
            }
            setSelection(index)
        }
    }

    private fun getSelectedFont(spinner: Spinner, fontNames: List<String>): String? {
        val selected = spinner.selectedItem?.toString() ?: return null
        return if (selected == "[Default]") null else selected
    }

    private fun addSlotRow(layout: LinearLayout, label: String, spinner: Spinner, hint: String) {
        layout.addView(TextView(context).apply {
            text = label
            textSize = 16f
            setPadding(0, 12, 0, 4)
        })
        layout.addView(TextView(context).apply {
            text = hint
            textSize = 12f
            alpha = 0.7f
            setPadding(0, 0, 0, 4)
        })
        layout.addView(spinner.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
    }
}

/** Battery slot assignment: XX% or FULL with 7 independent font slots */
class BatterySlotAssignmentDialog(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit
) {

    fun show() {
        val fonts = FontLibrary.loadFonts(prefs)
        val fontNames = listOf("[Default]") + fonts.keys.sorted()
        val config = FontLibrary.loadBatterySlots(prefs)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        layout.addView(TextView(context).apply {
            text = "Assign fonts to battery display.\n\n" +
                   "Different fonts for percentage (XX%) and FULL display!"
            setPadding(0, 0, 0, 24)
        })

        // Percentage slots
        val digit1Spinner = createFontSpinner(fontNames, config.digit1)
        val digit2Spinner = createFontSpinner(fontNames, config.digit2)
        val percentSpinner = createFontSpinner(fontNames, config.percent)

        layout.addView(TextView(context).apply {
            text = "Percentage Display (e.g., 73%)"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        })
        addSlotRow(layout, "First digit:", digit1Spinner, "e.g., '7' in 73%")
        addSlotRow(layout, "Second digit:", digit2Spinner, "e.g., '3' in 73%")
        addSlotRow(layout, "Percent sign:", percentSpinner, "e.g., '%' in 73%")

        // FULL slots
        val letterFSpinner = createFontSpinner(fontNames, config.letterF)
        val letterUSpinner = createFontSpinner(fontNames, config.letterU)
        val letterL1Spinner = createFontSpinner(fontNames, config.letterL1)
        val letterL2Spinner = createFontSpinner(fontNames, config.letterL2)

        layout.addView(TextView(context).apply {
            text = "\nFULL Display"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        })
        addSlotRow(layout, "Letter 'F':", letterFSpinner, "First letter")
        addSlotRow(layout, "Letter 'U':", letterUSpinner, "Second letter")
        addSlotRow(layout, "First 'L':", letterL1Spinner, "Third letter")
        addSlotRow(layout, "Second 'L':", letterL2Spinner, "Fourth letter")

        AlertDialog.Builder(context)
            .setTitle("Battery Font Slots")
            .setView(ScrollView(context).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                val newConfig = BatterySlotConfig(
                    digit1 = getSelectedFont(digit1Spinner, fontNames),
                    digit2 = getSelectedFont(digit2Spinner, fontNames),
                    percent = getSelectedFont(percentSpinner, fontNames),
                    letterF = getSelectedFont(letterFSpinner, fontNames),
                    letterU = getSelectedFont(letterUSpinner, fontNames),
                    letterL1 = getSelectedFont(letterL1Spinner, fontNames),
                    letterL2 = getSelectedFont(letterL2Spinner, fontNames)
                )
                FontLibrary.saveBatterySlots(prefs, newConfig)
                onChanged()
                Toast.makeText(context, "Battery slots saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset to Default") { _, _ ->
                FontLibrary.saveBatterySlots(prefs, BatterySlotConfig())
                onChanged()
                Toast.makeText(context, "Reset to default", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun createFontSpinner(fontNames: List<String>, selectedFont: String?): Spinner {
        return Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, fontNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            val index = if (selectedFont == null) 0 else {
                val idx = fontNames.indexOf(selectedFont)
                if (idx >= 0) idx else 0
            }
            setSelection(index)
        }
    }

    private fun getSelectedFont(spinner: Spinner, fontNames: List<String>): String? {
        val selected = spinner.selectedItem?.toString() ?: return null
        return if (selected == "[Default]") null else selected
    }

    private fun addSlotRow(layout: LinearLayout, label: String, spinner: Spinner, hint: String) {
        layout.addView(TextView(context).apply {
            text = label
            textSize = 14f
            setPadding(0, 8, 0, 4)
        })
        layout.addView(TextView(context).apply {
            text = hint
            textSize = 12f
            alpha = 0.7f
            setPadding(0, 0, 0, 4)
        })
        layout.addView(spinner.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
    }
}
