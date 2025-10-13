package dev.taxi.vslzr

import android.content.Context
import org.xmlpull.v1.XmlPullParser

private const val PREFS_NAME = "vslzr_prefs"
private const val SEED_VERSION_KEY = "__seed_version__"
// Increment when you change res/xml/prefs_seed.xml
private const val SEED_VERSION = 2

/**
 * Copies values from res/xml/prefs_seed.xml into SharedPreferences "vslzr_prefs"
 * the first time (or when SEED_VERSION increases).
 */
fun seedDefaultsIfNeeded(ctx: Context) {
    val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val current = sp.getInt(SEED_VERSION_KEY, 0)
    if (current >= SEED_VERSION) return

    val editor = sp.edit()
    val xpp = ctx.resources.getXml(R.xml.prefs_seed)

    var event = xpp.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
            val tag = xpp.name
            val name = xpp.getAttributeValue(null, "name") ?: ""

            if (name.isNotEmpty()) {
                when (tag) {
                    "boolean" -> {
                        val v = xpp.getAttributeBooleanValue(null, "value", false)
                        editor.putBoolean(name, v)
                    }
                    "int" -> {
                        val v = xpp.getAttributeIntValue(null, "value", 0)
                        editor.putInt(name, v)
                    }
                    "float" -> {
                        val raw = xpp.getAttributeValue(null, "value")
                        val v = raw?.toFloatOrNull() ?: 0f
                        editor.putFloat(name, v)
                    }
                    "string" -> {
                        val v = xpp.nextText()
                        editor.putString(name, v)
                    }
                }
            }
        }
        event = xpp.next()
    }

    editor.putInt(SEED_VERSION_KEY, SEED_VERSION).apply()
}

/** Optional helper if you need to force reseed during development. */
fun forceReseed(ctx: Context) {
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(SEED_VERSION_KEY, 0).apply()
}
