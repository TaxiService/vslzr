package dev.taxi.vslzr

import android.content.Context
import org.xmlpull.v1.XmlPullParser

private const val PREFS_NAME = "vslzr_prefs"
private const val SEED_VERSION_KEY = "__seed_version__"

/** Hash the current res/xml/prefs_seed.xml so reseed triggers only when content changes. */
private fun seedVersionFromResource(ctx: Context): Int {
    val xpp = ctx.resources.getXml(R.xml.prefs_seed)
    var h = 1
    var e = xpp.eventType
    while (e != XmlPullParser.END_DOCUMENT) {
        if (e == XmlPullParser.START_TAG) {
            h = 31 * h + xpp.name.hashCode()
            h = 31 * h + (xpp.getAttributeValue(null, "name")?.hashCode() ?: 0)
            h = 31 * h + (xpp.getAttributeValue(null, "value")?.hashCode() ?: 0)
        }
        e = xpp.next()
    }
    return h
}

/** Copy values from res/xml/prefs_seed.xml into SharedPreferences if the seed changed. */
fun seedDefaultsIfNeeded(ctx: Context) {
    val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val seedVersion = seedVersionFromResource(ctx)
    val current = sp.getInt(SEED_VERSION_KEY, Int.MIN_VALUE)
    if (current == seedVersion) return

    val editor = sp.edit()
    val xpp = ctx.resources.getXml(R.xml.prefs_seed)
    var e = xpp.eventType
    while (e != XmlPullParser.END_DOCUMENT) {
        if (e == XmlPullParser.START_TAG) {
            val tag = xpp.name
            val name = xpp.getAttributeValue(null, "name") ?: ""
            if (name.isNotEmpty()) {
                when (tag) {
                    "boolean" -> editor.putBoolean(name, xpp.getAttributeBooleanValue(null, "value", false))
                    "int"     -> editor.putInt(name,     xpp.getAttributeIntValue(null, "value", 0))
                    "float"   -> editor.putFloat(name,   xpp.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f)
                    "string"  -> editor.putString(name,  xpp.nextText())
                }
            }
        }
        e = xpp.next()
    }
    editor.putInt(SEED_VERSION_KEY, seedVersion).apply()
}

/** Dev helper to force reseed once. */
fun forceReseed(ctx: Context) {
    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().remove(SEED_VERSION_KEY).apply()
}
