package dev.taxi.vslzr

import android.content.Context
import android.content.SharedPreferences
import org.xmlpull.v1.XmlPullParser

private const val PREFS_NAME = "vslzr_prefs"
private const val SEED_FLAG = "__seed_applied__"

fun seedDefaultsIfNeeded(ctx: Context) {
    val sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (sp.getBoolean(SEED_FLAG, false)) return

    val editor = sp.edit()
    val xpp = ctx.resources.getXml(R.xml.prefs_seed)
    while (true) {
        when (xpp.eventType) {
            XmlPullParser.START_TAG -> {
                val tag = xpp.name
                val name = xpp.getAttributeValue(null, "name") ?: ""
                when (tag) {
                    "boolean" -> editor.putBoolean(name, xpp.getAttributeBooleanValue(null, "value", false))
                    "int"     -> editor.putInt(name,     xpp.getAttributeIntValue(null, "value", 0))
                    "float"   -> editor.putFloat(name,   xpp.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f)
                    "string"  -> editor.putString(name,  xpp.nextText())
                }
            }
            XmlPullParser.END_DOCUMENT -> break
        }
        xpp.next()
    }
    editor.putBoolean(SEED_FLAG, true).apply()
}
