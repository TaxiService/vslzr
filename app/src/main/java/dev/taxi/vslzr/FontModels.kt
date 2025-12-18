package dev.taxi.vslzr

import org.json.JSONArray
import org.json.JSONObject

/**
 * A custom font definition with variable grid size.
 */
data class FontDefinition(
    val name: String,                      // "retro_bold", "slim_digital"
    val width: Int,                        // Grid width (e.g., 3)
    val height: Int,                       // Grid height (e.g., 5)
    val glyphs: Map<Char, Array<String>>   // Char → bitmap rows
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("width", width)
        json.put("height", height)

        val glyphsJson = JSONObject()
        for ((ch, rows) in glyphs) {
            glyphsJson.put(ch.toString(), JSONArray(rows.toList()))
        }
        json.put("glyphs", glyphsJson)

        return json
    }

    companion object {
        fun fromJson(json: JSONObject): FontDefinition {
            val name = json.getString("name")
            val width = json.getInt("width")
            val height = json.getInt("height")

            val glyphsJson = json.getJSONObject("glyphs")
            val glyphs = mutableMapOf<Char, Array<String>>()

            for (key in glyphsJson.keys()) {
                if (key.length == 1) {
                    val ch = key[0]
                    val rowsJson = glyphsJson.getJSONArray(key)
                    val rows = Array(rowsJson.length()) { i ->
                        rowsJson.getString(i)
                    }
                    glyphs[ch] = rows
                }
            }

            return FontDefinition(name, width, height, glyphs)
        }
    }
}

/**
 * Configuration for which font to use for each clock slot.
 */
data class ClockSlotConfig(
    val hourTens: String? = null,      // Font name or null for default
    val hourOnes: String? = null,
    val colon: String? = null,
    val minuteTens: String? = null,
    val minuteOnes: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("hourTens", hourTens ?: JSONObject.NULL)
            put("hourOnes", hourOnes ?: JSONObject.NULL)
            put("colon", colon ?: JSONObject.NULL)
            put("minuteTens", minuteTens ?: JSONObject.NULL)
            put("minuteOnes", minuteOnes ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ClockSlotConfig {
            return ClockSlotConfig(
                hourTens = if (json.isNull("hourTens")) null else json.getString("hourTens"),
                hourOnes = if (json.isNull("hourOnes")) null else json.getString("hourOnes"),
                colon = if (json.isNull("colon")) null else json.getString("colon"),
                minuteTens = if (json.isNull("minuteTens")) null else json.getString("minuteTens"),
                minuteOnes = if (json.isNull("minuteOnes")) null else json.getString("minuteOnes")
            )
        }
    }
}

/**
 * Configuration for which font to use for each battery slot.
 */
data class BatterySlotConfig(
    val digit1: String? = null,        // First digit of percentage
    val digit2: String? = null,        // Second digit
    val percent: String? = null,       // % symbol
    val letterF: String? = null,       // "FULL" - F
    val letterU: String? = null,       // U
    val letterL1: String? = null,      // First L
    val letterL2: String? = null       // Second L
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("digit1", digit1 ?: JSONObject.NULL)
            put("digit2", digit2 ?: JSONObject.NULL)
            put("percent", percent ?: JSONObject.NULL)
            put("letterF", letterF ?: JSONObject.NULL)
            put("letterU", letterU ?: JSONObject.NULL)
            put("letterL1", letterL1 ?: JSONObject.NULL)
            put("letterL2", letterL2 ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): BatterySlotConfig {
            return BatterySlotConfig(
                digit1 = if (json.isNull("digit1")) null else json.getString("digit1"),
                digit2 = if (json.isNull("digit2")) null else json.getString("digit2"),
                percent = if (json.isNull("percent")) null else json.getString("percent"),
                letterF = if (json.isNull("letterF")) null else json.getString("letterF"),
                letterU = if (json.isNull("letterU")) null else json.getString("letterU"),
                letterL1 = if (json.isNull("letterL1")) null else json.getString("letterL1"),
                letterL2 = if (json.isNull("letterL2")) null else json.getString("letterL2")
            )
        }
    }
}
