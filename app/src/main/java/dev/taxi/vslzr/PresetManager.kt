package dev.taxi.vslzr

import android.content.Context
import org.json.JSONObject
import java.io.File

private const val PREFS_NAME = "vslzr_prefs"
private const val PRESET_DIR = "presets"
private const val PRESET_EXT = ".json"

// Keep in sync with prefs XML keys
private val KEYS = arrayOf(
    // layout / mapping
    "use_circle_map","circle_inset","cols","rows","clock_y","batt_y",
    // visibility + overlap
    "hide_clock","hide_batt","erase_clock","erase_batt",
    // labels
    "full_threshold",
    // brightness
    "bright_hhmm","bright_batt","bright_viz",
    // frame decay
    "frame_decay_idle","frame_decay_play",
    // fft analysis + shaping
    "bands","beta_x100","scale_low","scale_high","tilt_db",
    // post-processing
    "gate","gain_x100","gamma_x100","attack_x100","release_x100",
    // hi-end recovery
    "high_cut_khz","resample_tilt_x100","col_tilt_db","floor_after_ar",
    // smoothing + silence
    "smooth_alpha_x100","silence_gate_sum","zero_on_silence",
    // misc
    "auto_apply"
)

object PresetManager {
    private fun dir(ctx: Context): File = File(ctx.filesDir, PRESET_DIR).apply { mkdirs() }
    private fun file(ctx: Context, name: String) = File(dir(ctx), "$name$PRESET_EXT")

    fun snapshot(ctx: Context, name: String = "preset"): JSONObject {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = JSONObject().put("name", name).put("version", 1)
        val values = JSONObject()
        for (k in KEYS) {
            if (!sp.contains(k)) continue
            when (val v = sp.all[k]) {
                is Int -> values.put(k, v)
                is Boolean -> values.put(k, v)
                is Float -> values.put(k, v.toDouble())
                is Long -> values.put(k, v)
                is String -> values.put(k, v)
            }
        }
        root.put("values", values)
        return root
    }

    fun apply(ctx: Context, preset: JSONObject) {
        val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val values = preset.optJSONObject("values") ?: preset // accept plain map too
        val e = sp.edit()
        for (k in KEYS) {
            if (!values.has(k)) continue
            val v = values.get(k)
            when (v) {
                is Int -> e.putInt(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is Long -> e.putLong(k, v)
                is String -> e.putString(k, v)
            }
        }
        e.apply()
    }

    fun save(ctx: Context, name: String): File {
        val json = snapshot(ctx, name).toString(2)
        return file(ctx, name).apply { writeText(json) }
    }

    fun load(ctx: Context, name: String): JSONObject =
        JSONObject(file(ctx, name).readText())

    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(PRESET_EXT) }
            ?.map { it.name.removeSuffix(PRESET_EXT) }
            ?.sorted() ?: emptyList()

    fun delete(ctx: Context, name: String) { file(ctx, name).delete() }
}
