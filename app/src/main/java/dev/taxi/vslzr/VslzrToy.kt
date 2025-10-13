package dev.taxi.vslzr

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

private const val ACTION_APPLY_PREFS = "dev.taxi.vslzr.APPLY_PREFS"

class VslzrToy : Service() {

    private var gm: GlyphMatrixManager? = null
    private var engine: RenderEngine? = null

    private val prefs by lazy { getSharedPreferences("vslzr_prefs", MODE_PRIVATE) }

    // Correct signature and types
    private val applyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            engine?.applyPrefs(prefs)
        }
    }

    // Handler must exist before Messenger
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

        // Register receiver with proper flags on API 33+
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                applyReceiver,
                IntentFilter(ACTION_APPLY_PREFS),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(applyReceiver, IntentFilter(ACTION_APPLY_PREFS))
        }

        return messenger.binder
    }

    private fun pushBlack() {
        try { gm?.setMatrixFrame(IntArray(25 * 25)) } catch (_: Throwable) {}
        try { gm?.turnOff() } catch (_: Throwable) {}
    }

    override fun onUnbind(intent: Intent): Boolean {
        runCatching { unregisterReceiver(applyReceiver) }
        engine?.stop(); engine = null
        pushBlack()
        runCatching { gm?.unInit() }
        gm = null
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(applyReceiver) }
        engine?.stop(); engine = null
        pushBlack()
    }

    private fun startEngine() {
        val push: (IntArray) -> Unit = { frame ->
            android.util.Log.d("VSLZR", "push ${frame.size}")
            gm?.setMatrixFrame(frame)
        }
        engine = RenderEngine(this, push).apply {
            applyPrefs(prefs)
            start()
        }
    }
}
