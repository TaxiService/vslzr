package dev.taxi.vslzr

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.*
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

class VslzrToy : Service() {
    private var gm: GlyphMatrixManager? = null
    private var engine: RenderEngine? = null

    // initialize handler BEFORE using it in Messenger
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return
            when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> engine?.toggleBoost()
                GlyphToy.EVENT_ACTION_DOWN -> {}
                GlyphToy.EVENT_ACTION_UP -> {}
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent): IBinder {
        gm = GlyphMatrixManager.getInstance(applicationContext).also { m ->
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName) {
                    m.register("23111")
                    startEngine()
                    android.util.Log.d("VSLZR", "registered + engine started")
                }
                override fun onServiceDisconnected(name: ComponentName) {}
            })
        }
        return messenger.binder
    }

    private fun pushBlack() {
        try { gm?.setMatrixFrame(IntArray(25*25) { 0 }) } catch (_: Throwable) {}
        try { /* optional if available in your AAR */ gm?.turnOff() } catch (_: Throwable) {}
    }

    override fun onUnbind(intent: Intent): Boolean {
        engine?.stop(); engine = null
        pushBlack()
        try { gm?.unInit() } catch (_: Throwable) {}
        gm = null
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        engine?.stop(); engine = null
        pushBlack()
    }

    private fun startEngine() {
        val push: (IntArray) -> Unit = { frame ->
            android.util.Log.d("VSLZR", "push ${frame.size}")
            gm?.setMatrixFrame(frame)
        }
        engine = RenderEngine(this, push).apply { start() }
    }
}
