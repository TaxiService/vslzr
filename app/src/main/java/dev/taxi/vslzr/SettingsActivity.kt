package dev.taxi.vslzr

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import androidx.appcompat.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.text.InputType
import android.widget.EditText
import android.widget.Toast



private const val ACTION_APPLY_PREFS = "dev.taxi.vslzr.APPLY_PREFS"

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        seedDefaultsIfNeeded(this)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

}

class SettingsFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "vslzr_prefs"
        setPreferencesFromResource(R.xml.prefs_vslzr, rootKey)

        // Click handlers
        findPreference<Preference>("open_toys_mgr")?.onPreferenceClickListener = this
        findPreference<Preference>("apply_now")?.onPreferenceClickListener = this

        val keys = listOf(
            "save_preset","load_preset","copy_preset","paste_preset",
            "auto_apply","use_circle_map","circle_inset","cols","rows","clock_y","batt_y",
            "hide_clock","hide_batt","erase_clock","erase_batt","full_threshold",
            "bright_hhmm","bright_batt","bright_viz",
            "frame_decay_idle","frame_decay_play",
            "bands","beta_x100","scale_low","scale_high","tilt_db",
            "gate","gain_x100","gamma_x100","attack_x100","release_x100",
            "high_cut_khz","low_cut_hz","smooth_alpha_x100","floor_after_ar","silence_gate_sum","zero_on_silence",
            "resample_tilt_x100","col_tilt_db"
        )
        keys.forEach { findPreference<Preference>(it)?.onPreferenceChangeListener = this }
        arrayOf("open_toys_mgr","apply_now","save_preset","load_preset","copy_preset","paste_preset","delete_preset","edit_font")
            .forEach { k -> findPreference<Preference>(k)?.onPreferenceClickListener = this }

        // numeric summaries
        fun bind(key:String, fmt:(Int)->String) {
            findPreference<SeekBarPreference>(key)?.summaryProvider =
                Preference.SummaryProvider<SeekBarPreference> { p -> fmt(p.value) }
        }
        bind("cols"){ "columns: $it" }
        bind("rows"){ "rows: $it" }
        bind("clock_y"){ "row: $it" }
        bind("batt_y"){ "row: $it" }
        bind("full_threshold"){ "≥$it% shows FULL" }
        bind("bright_hhmm"){ "value: $it" }
        bind("bright_batt"){ "value: $it" }
        bind("bright_viz"){ "value: $it" }
        bind("frame_decay_idle"){ "idle decay: $it" }
        bind("frame_decay_play"){ "play decay: $it" }
        bind("bands"){ "bands: $it" }
        bind("beta_x100"){ "beta: ${"%.2f".format(it/100f)}" }
        bind("scale_low"){ "low knee: $it" }
        bind("scale_high"){ "high knee: $it" }
        bind("tilt_db"){ "tilt: $it dB" }
        bind("gate"){ "gate: $it" }
        bind("gain_x100"){ "gain: ${"%.2f".format(it/100f)}×" }
        bind("gamma_x100"){ "gamma: ${"%.2f".format(it/100f)}" }
        bind("attack_x100"){ "attack: ${"%.2f".format(it/100f)}" }
        bind("release_x100"){ "release: ${"%.2f".format(it/100f)}" }
        bind("high_cut_khz")        { "high cut: $it kHz" }
        bind("beta_x100")           { "beta: ${"%.2f".format(it/100f)}" }
        bind("resample_tilt_x100")  { "tilt: ${"%.2f".format(it/100f)}" }
        bind("col_tilt_db")         { "high shelf: $it dB" }
        bind("floor_after_ar")      { "post floor: $it" }

        // Show formatted summaries under sliders
        fun bindSummaryInt(key: String, fmt: (Int) -> String) {
            findPreference<SeekBarPreference>(key)?.summaryProvider =
                Preference.SummaryProvider<SeekBarPreference> { p -> fmt(p.value) }
        }
        bindSummaryInt("bright_viz")      { v -> "value: $v"}
        bindSummaryInt("bar_bottom_y")    { v -> "row: $v"}
        bindSummaryInt("bar_max_h")       { v -> "rows: $v"}
        bindSummaryInt("gate")            { v -> "gate: $v"}
        bindSummaryInt("gain_x100")       { v -> "gain: ${"%.2f".format(v/100f)}×" }
        bindSummaryInt("gamma_x100")      { v -> "gamma: ${"%.2f".format(v/100f)}" }
        bindSummaryInt("attack_x100")     { v -> "attack: ${"%.2f".format(v/100f)}" }
        bindSummaryInt("release_x100")    { v -> "release: ${"%.2f".format(v/100f)}" }
        bindSummaryInt("frame_decay_idle"){ v -> "idle decay: $v"}
        bindSummaryInt("frame_decay_play"){ v -> "play decay: $v"}

        attachNumericEditor("bright_viz", 64, 255)
        attachNumericEditor("bright_hhmm", 64, 255)
        attachNumericEditor("bright_batt", 64, 255)

        attachNumericEditor("cols", 12, 25)
        attachNumericEditor("rows", 8, 25)
        attachNumericEditor("circle_inset", 0, 4)
        attachNumericEditor("clock_y", 0, 10)
        attachNumericEditor("batt_y", 12, 24)

        attachNumericEditor("frame_decay_idle", 150, 255)
        attachNumericEditor("frame_decay_play", 150, 255)

        attachNumericEditor("bands", 6, 32)
        attachNumericEditor("beta_x100", 120, 300, step = 5)
        attachNumericEditor("scale_low", 8, 128)
        attachNumericEditor("scale_high", 8, 128)
        attachNumericEditor("tilt_db", 0, 18)

        attachNumericEditor("gate", 0, 96)
        attachNumericEditor("gain_x100", 50, 200)
        attachNumericEditor("gamma_x100", 30, 200)
        attachNumericEditor("attack_x100", 10, 100)
        attachNumericEditor("release_x100", 5, 60)

        attachNumericEditor("high_cut_khz", 10, 22)
        attachNumericEditor("resample_tilt_x100", 100, 300)
        attachNumericEditor("col_tilt_db", 0, 18)
        attachNumericEditor("floor_after_ar", 0, 32)

        attachNumericEditor("smooth_alpha_x100", 10, 90)
        attachNumericEditor("silence_gate_sum", 0, 500)

    }

    override fun onPreferenceChange(p: Preference, newValue: Any?): Boolean {
        val auto = preferenceManager.sharedPreferences?.getBoolean("auto_apply", true) ?: true
        if (auto && p.key != "auto_apply") requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
        return true
    }

    override fun onPreferenceClick(pref: Preference): Boolean {
        when (pref.key) {
            "open_toys_mgr" -> {
                runCatching {
                    startActivity(
                        Intent().setClassName(
                            "com.nothing.thirdparty",
                            "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
                        )
                    )
                }
                return true
            }
            "apply_now" -> {
                requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS)); return true
            }
            "save_preset" -> {
                val input = EditTextPreference(requireContext())
                val edit = android.widget.EditText(requireContext())
                edit.hint = "name"
                AlertDialog.Builder(requireContext())
                    .setTitle("Save preset")
                    .setView(edit)
                    .setPositiveButton("Save") { _, _ ->
                        val name = edit.text?.toString()?.trim().orEmpty().ifEmpty { "preset" }
                        val f = PresetManager.save(requireContext(), name)
                        android.widget.Toast.makeText(requireContext(), "Saved: ${f.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return true
            }
            "load_preset" -> {
                val names = PresetManager.list(requireContext())
                if (names.isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "No presets saved", android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Load preset")
                    .setItems(names.toTypedArray()) { _, which ->
                        val preset = PresetManager.load(requireContext(), names[which])
                        PresetManager.apply(requireContext(), preset)
                        requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
                    }
                    .show()
                return true
            }
            "delete_preset" -> {
                val names = PresetManager.list(requireContext())
                if (names.isEmpty()) {
                    Toast.makeText(requireContext(), "No presets saved", Toast.LENGTH_SHORT).show()
                    return true
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete preset")
                    .setItems(names.toTypedArray()) { _, which ->
                        val name = names[which]
                        AlertDialog.Builder(requireContext())
                            .setMessage("Delete \"$name\"?")
                            .setPositiveButton("Delete") { _, _ ->
                                PresetManager.delete(requireContext(), name)
                                Toast.makeText(requireContext(), "Deleted: $name", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .show()
                return true
            }
            "copy_preset" -> {
                val json = PresetManager.snapshot(requireContext(), "clipboard").toString(2)
                val cm = requireContext().getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("vslzr preset", json))
                android.widget.Toast.makeText(requireContext(), "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                return true
            }
            "paste_preset" -> {
                val cm = requireContext().getSystemService(ClipboardManager::class.java)
                val text = cm.primaryClip?.getItemAt(0)?.coerceToText(requireContext())?.toString()
                if (text.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), "Clipboard empty", android.widget.Toast.LENGTH_SHORT).show()
                    return true
                }
                runCatching {
                    val preset = org.json.JSONObject(text)
                    PresetManager.apply(requireContext(), preset)
                    requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
                    android.widget.Toast.makeText(requireContext(), "Preset applied", android.widget.Toast.LENGTH_SHORT).show()
                }.onFailure {
                    android.widget.Toast.makeText(requireContext(), "Invalid JSON", android.widget.Toast.LENGTH_SHORT).show()
                }
                return true
            }
            "edit_font" -> {
                val prefs = preferenceManager.sharedPreferences ?: return false
                val existingFont = CustomFontStorage.load(prefs)

                FontEditorDialog(requireContext()) { newFont ->
                    if (newFont.isEmpty()) {
                        // User clicked "Reset to Default"
                        CustomFontStorage.clear(prefs)
                        Toast.makeText(requireContext(), "Font reset to default", Toast.LENGTH_SHORT).show()
                    } else {
                        // Save custom font
                        CustomFontStorage.save(prefs, newFont)
                        Toast.makeText(requireContext(), "Custom font saved", Toast.LENGTH_SHORT).show()
                    }
                    // Trigger hot-reload
                    requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
                }.show(existingFont)
                return true
            }
        }
        return false
    }
    private fun attachNumericEditor(key: String, min: Int, max: Int, step: Int = 1) {
        val pref = findPreference<SeekBarPreference>(key) ?: return
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val ctx = requireContext()
            val input = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(pref.value.toString())
                setSelectAllOnFocus(true)
            }
            AlertDialog.Builder(ctx)
                .setTitle(pref.title ?: key)
                .setView(input)
                .setPositiveButton("Apply") { _, _ ->
                    val v = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                    val clamped = (v / step) * step
                    pref.value = clamped.coerceIn(min, max)
                    // auto-apply if enabled
                    val auto = preferenceManager.sharedPreferences?.getBoolean("auto_apply", true) ?: true
                    if (auto) requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            true // consume click (we still keep the on-row SeekBar visible)
        }
    }


}
