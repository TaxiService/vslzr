package dev.taxi.vslzr

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import android.content.Context

private const val ACTION_APPLY_PREFS = "dev.taxi.vslzr.APPLY_PREFS"

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Change handlers (auto-apply)
        listOf(
            "hide_batt","erase_batt","bright_viz","bar_bottom_y","bar_max_h",
            "gate","gain_x100","gamma_x100","attack_x100","release_x100",
            "frame_decay_idle","frame_decay_play","auto_apply"
        ).forEach { key ->
            findPreference<Preference>(key)?.onPreferenceChangeListener = this
        }

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
        // Only broadcast if auto_apply is ON, or if user toggled auto_apply itself we do nothing extra
        val auto = preferenceManager.sharedPreferences?.getBoolean("auto_apply", false) ?: false
        if (auto && p.key != "auto_apply") {
            requireContext().sendBroadcast(Intent(ACTION_APPLY_PREFS))
        }
        return true // let Preference save the value
    }

    override fun onPreferenceClick(pref: Preference): Boolean {
        return when (pref.key) {
            "open_toys_mgr" -> {
                val i = Intent().setClassName(
                    "com.nothing.thirdparty",
                    "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"
                )
                runCatching { startActivity(i) }
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
