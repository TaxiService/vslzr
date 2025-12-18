package dev.taxi.vslzr

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Manages a library of custom fonts with variable grid sizes.
 * Stores fonts and slot configurations in SharedPreferences.
 */
object FontLibrary {

    private const val KEY_FONTS = "font_library_json"
    private const val KEY_CLOCK_SLOTS = "clock_slots_json"
    private const val KEY_BATTERY_SLOTS = "battery_slots_json"

    // ===== Font Management =====

    /** Save all fonts to storage */
    fun saveFonts(prefs: SharedPreferences, fonts: Map<String, FontDefinition>) {
        val json = JSONObject()
        for ((name, font) in fonts) {
            json.put(name, font.toJson())
        }
        prefs.edit().putString(KEY_FONTS, json.toString()).apply()
    }

    /** Load all fonts from storage */
    fun loadFonts(prefs: SharedPreferences): Map<String, FontDefinition> {
        val jsonStr = prefs.getString(KEY_FONTS, null) ?: return emptyMap()

        return try {
            val json = JSONObject(jsonStr)
            val fonts = mutableMapOf<String, FontDefinition>()

            for (key in json.keys()) {
                val fontJson = json.getJSONObject(key)
                fonts[key] = FontDefinition.fromJson(fontJson)
            }
            fonts
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Add or update a single font */
    fun saveFont(prefs: SharedPreferences, font: FontDefinition) {
        val fonts = loadFonts(prefs).toMutableMap()
        fonts[font.name] = font
        saveFonts(prefs, fonts)
    }

    /** Delete a font by name */
    fun deleteFont(prefs: SharedPreferences, name: String) {
        val fonts = loadFonts(prefs).toMutableMap()
        fonts.remove(name)
        saveFonts(prefs, fonts)
    }

    /** Get a specific font by name */
    fun getFont(prefs: SharedPreferences, name: String): FontDefinition? {
        return loadFonts(prefs)[name]
    }

    /** List all font names */
    fun listFontNames(prefs: SharedPreferences): List<String> {
        return loadFonts(prefs).keys.sorted()
    }

    // ===== Clock Slot Configuration =====

    /** Save clock slot configuration */
    fun saveClockSlots(prefs: SharedPreferences, config: ClockSlotConfig) {
        prefs.edit().putString(KEY_CLOCK_SLOTS, config.toJson().toString()).apply()
    }

    /** Load clock slot configuration */
    fun loadClockSlots(prefs: SharedPreferences): ClockSlotConfig {
        val jsonStr = prefs.getString(KEY_CLOCK_SLOTS, null) ?: return ClockSlotConfig()

        return try {
            ClockSlotConfig.fromJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            ClockSlotConfig()
        }
    }

    // ===== Battery Slot Configuration =====

    /** Save battery slot configuration */
    fun saveBatterySlots(prefs: SharedPreferences, config: BatterySlotConfig) {
        prefs.edit().putString(KEY_BATTERY_SLOTS, config.toJson().toString()).apply()
    }

    /** Load battery slot configuration */
    fun loadBatterySlots(prefs: SharedPreferences): BatterySlotConfig {
        val jsonStr = prefs.getString(KEY_BATTERY_SLOTS, null) ?: return BatterySlotConfig()

        return try {
            BatterySlotConfig.fromJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            BatterySlotConfig()
        }
    }

    // ===== Utility =====

    /** Clear all custom fonts and slot configs */
    fun clearAll(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_FONTS)
            .remove(KEY_CLOCK_SLOTS)
            .remove(KEY_BATTERY_SLOTS)
            .apply()
    }
}
