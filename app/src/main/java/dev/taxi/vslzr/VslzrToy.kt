package dev.taxi.vslzr

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.*
import com.nothing.glyph.sdk.*

class VslzrToy : Service() {
    private var gm: GlyphMatrixManager? = null
    private val messenger = Messenger(handler)
    private var engine: RenderEngine? = null

    override fun onBind(intent: Intent): IBinder {
        gm = GlyphMatrixManager.getInstance(applicationContext).also { m ->
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName) {
                    m.register(Glyph.DEVICE_23112) // Phone (3)
                    startEngine()
                }
                override fun onServiceDisconnected(name: ComponentName) {}
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        engine?.stop(); engine = null
        gm?.unInit(); gm = null
        return false
    }

    private fun startEngine() {
        val push: (IntArray) -> Unit = { frame -> vslzrgm?.setMatrixFrame(frame) }
        engine = RenderEngine(this, push).apply { start() }
    }

    // Glyph Button events if you later want to hook long-press or hold
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return
            when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_CHANGE -> engine?.toggleBoost() // optional
                GlyphToy.EVENT_ACTION_DOWN -> {}
                GlyphToy.EVENT_ACTION_UP -> {}
            }
        }
    }
}
