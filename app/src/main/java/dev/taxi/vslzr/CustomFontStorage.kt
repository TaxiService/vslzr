package dev.taxi.vslzr

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Saves and loads custom font designs to/from SharedPreferences.
 */
object CustomFontStorage {

    private const val KEY_CUSTOM_FONT = "custom_font_json"

    /** Save custom font glyphs */
    fun save(prefs: SharedPreferences, glyphs: Map<Char, Array<String>>) {
        val json = JSONObject()
        for ((ch, rows) in glyphs) {
            val rowsJson = org.json.JSONArray(rows.toList())
            json.put(ch.toString(), rowsJson)
        }
        prefs.edit().putString(KEY_CUSTOM_FONT, json.toString()).apply()
    }

    /** Load custom font glyphs (returns empty map if none saved) */
    fun load(prefs: SharedPreferences): Map<Char, Array<String>> {
        val jsonStr = prefs.getString(KEY_CUSTOM_FONT, null) ?: return emptyMap()

        return try {
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<Char, Array<String>>()

            for (key in json.keys()) {
                if (key.length == 1) {
                    val ch = key[0]
                    val rowsJson = json.getJSONArray(key)
                    val rows = Array(rowsJson.length()) { i ->
                        rowsJson.getString(i)
                    }
                    result[ch] = rows
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Check if custom font is set */
    fun hasCustomFont(prefs: SharedPreferences): Boolean {
        return prefs.contains(KEY_CUSTOM_FONT)
    }

    /** Clear custom font (revert to default) */
    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove(KEY_CUSTOM_FONT).apply()
    }
}
