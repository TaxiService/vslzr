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
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("VSLZR", "onCreate()")
        seedDefaultsIfNeeded(this)

        // Register receiver once at service creation with proper flags on API 33+
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("VSLZR", "onStartCommand() - attempting to keep service alive")
        return START_STICKY  // Tell Android to restart if killed
    }
    override fun onBind(intent: Intent): IBinder {
        android.util.Log.d("VSLZR", "onBind() called - full initialization")
        gm = GlyphMatrixManager.getInstance(applicationContext).also { m ->
            m.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: ComponentName) {
                    android.util.Log.d("VSLZR", "onServiceConnected callback - registering + starting engine")
                    m.register("23111")
                    startEngine()
                }
                override fun onServiceDisconnected(name: ComponentName) {
                    android.util.Log.d("VSLZR", "onServiceDisconnected - unexpected!")
                }
            })
        }

        return messenger.binder
    }

    private fun pushBlack() {
        try { gm?.setMatrixFrame(IntArray(25 * 25)) } catch (_: Throwable) {}
        try { gm?.turnOff() } catch (_: Throwable) {}
    }

    override fun onUnbind(intent: Intent): Boolean {
        android.util.Log.d("VSLZR", "onUnbind() - stopping engine, keeping GM alive")
        engine?.stop(); engine = null
        pushBlack()
        // Keep GlyphMatrixManager initialized for fast rebind
        return true  // Allow onRebind() to be called
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        // Fast reactivation - GlyphMatrixManager is already initialized and registered
        android.util.Log.d("VSLZR", "onRebind() - quick restart (no re-register)")
        startEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("VSLZR", "onDestroy() - full cleanup")
        engine?.stop(); engine = null
        pushBlack()
        runCatching { gm?.unInit() }
        gm = null
        runCatching { unregisterReceiver(applyReceiver) }
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
