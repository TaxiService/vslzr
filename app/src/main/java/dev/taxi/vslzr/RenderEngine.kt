package dev.taxi.vslzr

import android.content.*
import android.media.audiofx.Visualizer
import android.os.BatteryManager
import android.os.SystemClock
import kotlinx.coroutines.*
import java.time.LocalTime
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class RenderEngine(
    private val ctx: Context,
    private val push: (IntArray) -> Unit
) {
    // Independent brightness controls (0..255)
    var BRIGHT_HHMM = 255
    var BRIGHT_BATT = 200
    var BRIGHT_VIZ  = 180

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var running = false

    // 25x25 grayscale buffer
    private val buf = Array(25) { IntArray(25) }

    // Weight curve: brighter at bottom rows
    private val WY = IntArray(25) { y ->
        val t = (24 - y) / 24f
        ( (0.4f + 0.6f * (1f - t*t)) * 255 ).toInt()
    }

    private var vis: Visualizer? = null

    private fun tryInitVis(): Visualizer? = try {
        Visualizer(0).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]
            scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            enabled = true
        }
    } catch (_: Throwable) { null }

    fun start() {
        if (running) return
        running = true
        vis = tryInitVis()
        scope.launch {
            val fft = ByteArray(vis?.captureSize ?: 2048)
            var lastBattRead = 0L
            var battPct = readBattery()
            while (running) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBattRead > 1_000L) { battPct = readBattery(); lastBattRead = now }

                clearAndDecay()
                val t = java.time.LocalTime.now()
                val colonOn = (now / 500L) % 2L == 0L
                drawHHMM(t.hour, t.minute, colonOn, x = 4, y = 2, bright = BRIGHT_HHMM)
                drawBattery(battPct, x = 3, y = 17, bright = BRIGHT_BATT)

                if (MediaBridge.isPlaying(ctx) && vis != null) {
                    vis!!.getFft(fft)
                    val mags = compressFft(fft, bars = 12)
                    drawBars(mags, baseBright = BRIGHT_VIZ)
                }

                push(blit())
                delay(33)
            }
        }
    }

    fun stop() {
        running = false
        scope.cancel()
        try { vis?.release() } catch (_: Throwable) {}
        vis = null
    }
    fun toggleBoost() { BRIGHT_VIZ = if (BRIGHT_VIZ < 200) 220 else 160 } // optional

    // --- drawing primitives ---

    private fun clearAndDecay() {
        // simple decay for mild persistence
        for (y in 0 until 25) for (x in 0 until 25) buf[y][x] = (buf[y][x] * 220) ushr 8
    }
    private fun setMax(x: Int, y: Int, v: Int) {
        if (x in 0..24 && y in 0..24) buf[y][x] = max(buf[y][x], v.coerceIn(0, 255))
    }
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun blit(): IntArray {
        val out = IntArray(25 * 25)
        var k = 0
        for (y in 0 until 25) for (x in 0 until 25) out[k++] = gray(buf[y][x])
        return out
    }

    // Plug-in font pack. Replace this with your own bitmaps later.
    private val font = TinyFont3x5() // has digits, ':', '%', 'F','U','L'

    private fun drawHHMM(h: Int, m: Int, colonOn: Boolean, x: Int, y: Int, bright: Int) {
        var cx = x
        font.drawDigit(buf, h / 10, cx, y, bright); cx += 4
        font.drawDigit(buf, h % 10, cx, y, bright); cx += 4
        if (colonOn) {
            font.drawColon(buf, cx, y, bright)
        }
        cx += 2
        font.drawDigit(buf, m / 10, cx, y, bright); cx += 4
        font.drawDigit(buf, m % 10, cx, y, bright)
    }

    private fun drawBattery(pct: Int, x: Int, y: Int, bright: Int) {
        if (pct >= 100) {
            font.drawTextFULL(buf, x, y, bright)
            return
        }
        // "NN%"
        val d0 = pct / 10
        val d1 = pct % 10
        var cx = x
        font.drawDigit(buf, d0, cx, y, bright); cx += 4
        font.drawDigit(buf, d1, cx, y, bright); cx += 4
        font.drawPercent(buf, cx, y, bright)
    }

    private fun drawBars(mags: IntArray, baseBright: Int) {
        for (i in mags.indices) {
            val h = min(12, mags[i] / 10) // map to 0..12 rows
            val b = q16(mags[i], floor = 32, steps = 16) // brightness quantized
            val x = 1 + i * 2
            for (yy in 24 downTo 24 - h) {
                val v = (b * baseBright / 255) * WY[yy] / 255
                setMax(x, yy, v)
            }
        }
    }

    private fun q16(mag: Int, floor: Int, steps: Int): Int {
        // compress magnitude and quantize to avoid shimmer
        val comp = (255.0 * ln(1.0 + mag / 64.0) / ln(1.0 + 255.0 / 64.0)).toInt()
        val q = (comp * steps / 255) * (255 / steps)
        return max(q, floor)
    }

    private fun readBattery(): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return v.coerceIn(0, 100)
    }

    private fun compressFft(fft: ByteArray, bars: Int): IntArray {
        // basic magnitude bucketing from mono FFT
        val n = fft.size / 2
        val mags = IntArray(bars)
        for (i in 0 until bars) {
            val start = (i     * n / bars)
            val end   = ((i+1) * n / bars)
            var acc = 0.0
            var cnt = 0
            var k = start
            while (k < end && k*2+1 < fft.size) {
                val re = fft[k*2].toInt()
                val im = fft[k*2+1].toInt()
                val m = kotlin.math.hypot(re.toDouble(), im.toDouble())
                acc += m; cnt++
                k++
            }
            mags[i] = min(255, if (cnt == 0) 0 else (acc / cnt).toInt())
        }
        return mags
    }
}
