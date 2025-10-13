package dev.taxi.vslzr

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*

private const val ACTION_APPLY_PREFS = "dev.taxi.vslzr.APPLY_PREFS"

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            "auto_apply","use_circle_map","circle_inset","cols","rows","clock_y","batt_y",
            "hide_clock","hide_batt","erase_clock","erase_batt","full_threshold",
            "bright_hhmm","bright_batt","bright_viz",
            "frame_decay_idle","frame_decay_play",
            "bands","beta_x100","scale_low","scale_high","tilt_db",
            "gate","gain_x100","gamma_x100","attack_x100","release_x100",
            "high_cut_khz","low_cut_hz","smooth_alpha_x100","floor_after_ar","silence_gate_sum","zero_on_silence"
        )
        keys.forEach { findPreference<Preference>(it)?.onPreferenceChangeListener = this }

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
    }

    override fun onPreferenceChange(p: Preference, newValue: Any?): Boolean {
        val auto = preferenceManager.sharedPreferences?.getBoolean("auto_apply", true) ?: true
        if (auto && p.key != "auto_apply") requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
        return true
    }

    override fun onPreferenceClick(pref: Preference): Boolean {
        return when (pref.key) {
            "open_toys_mgr" -> {
                runCatching {
                    startActivity(
                        Intent().setClassName(
                            "com.nothing.thirdparty",
                            "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
                        )
                    )
                }
                true
            }
            "apply_now" -> {
                requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
                true
            }
            else -> false
        }
    }
}
