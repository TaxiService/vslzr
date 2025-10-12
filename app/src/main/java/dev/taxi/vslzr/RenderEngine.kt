package dev.taxi.vslzr

import android.content.Context
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
    // Brightness knobs (0..255)
    var BRIGHT_HHMM = 255
    var BRIGHT_BATT = 100
    var BRIGHT_VIZ  = 180

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var running = false

    // 25×25 grayscale buffer (0..255)
    private val buf = Array(25) { IntArray(25) }

    // Visualizer (mono output mix). Lazily init to avoid SecurityException before permission.
    private var vis: Visualizer? = null
    private fun tryInitVis(): Visualizer? = try {
        Visualizer(0).apply {
            captureSize = Visualizer.getCaptureSizeRange()[1]
            scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            enabled = true
        }
    } catch (_: Throwable) { null }

    // Bottom brighter, top dimmer
    private val WY = IntArray(25) { y ->
        val t = (24 - y) / 24f
        ( (0.40f + 0.60f * (1f - t*t)) * 255 ).toInt()
    }

    // ----- public controls -----
    fun start() {
        if (running) return
        running = true
        vis = tryInitVis()

        // seed feedback grid that “dissipates”
        seedGrid()

        scope.launch {
            val fft = ByteArray(vis?.captureSize ?: 2048)
            var lastBattRead = 0L
            var battPct = readBattery()

            // show seeded frame once
            push(blit())

            while (running) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBattRead > 1_000L) { battPct = readBattery(); lastBattRead = now }

                clearAndDecay(220) // ~0.86 decay

                // time (blink colon 1 Hz, 0.5 s duty)
                val t = LocalTime.now()
                val colonOn = (now / 500L) % 2L == 0L
                drawHHMM(t.hour, t.minute, colonOn, y = 6, bright = BRIGHT_HHMM)

                // battery bottom
                drawBattery(battPct, y = 15, bright = BRIGHT_BATT)

                // FFT overlay if playing and visualizer ready
                vis?.let { v ->
                    if (MediaBridge.isPlaying(ctx)) {
                        v.getFft(fft)
                        val mags = compressFft(fft, bars = 12)
                        drawBars(mags, baseBright = BRIGHT_VIZ)
                    }
                }

                push(blit())
                delay(33) // ~30 FPS
            }
        }
    }

    fun stop() {
        running = false
        scope.cancel()
        try { vis?.release() } catch (_: Throwable) {}
        vis = null
    }

    fun toggleBoost() { BRIGHT_VIZ = if (BRIGHT_VIZ < 200) 220 else 160 }
    fun seedGrid() {
        for (y in 0 until 25) for (x in 0 until 25)
            buf[y][x] = if ((x + y) % 2 == 0) 64 else 0
    }

    // ----- drawing -----

    // Simple 1-bit bitmap font with variable widths
    class BitmapFont(val height: Int, var spacing: Int = 1) {
        private val glyphs = mutableMapOf<Char, Array<BooleanArray>>() // [row][col]
        fun drawChar(buf: Array<IntArray>, ch: Char, x: Int, y: Int, bright: Int) {
            val g = glyphs[ch] ?: return
            val gw = g[0].size
            for (r in 0 until height) for (c in 0 until gw) {
                if (!g[r][c]) continue
                val gx = x + c; val gy = y + r
                if (gx in 0..24 && gy in 0..24) buf[gy][gx] = max(buf[gy][gx], bright)
            }
        }

        fun add(ch: Char, rows: Array<String>) {
            require(rows.size == height)
            val w = rows[0].length
            val g = Array(height) { r -> BooleanArray(w) { c -> rows[r][c] == '1' } }
            glyphs[ch] = g
        }

        fun width(ch: Char) = glyphs[ch]?.get(0)?.size ?: 0

        fun measure(text: String): Int {
            var w = 0
            for (i in text.indices) {
                w += width(text[i])
                if (i != text.lastIndex) w += spacing
            }
            return w
        }

        fun draw(buf: Array<IntArray>, text: String, x: Int, y: Int, bright: Int) {
            var cx = x
            for (i in text.indices) {
                val g = glyphs[text[i]]
                if (g == null) {
                    cx += if (i != text.lastIndex) spacing else 0
                    continue
                }
                val gw = g[0].size
                for (r in 0 until height) for (c in 0 until gw) {
                    if (!g[r][c]) continue
                    val gx = cx + c
                    val gy = y + r
                    if (gx in 0..24 && gy in 0..24) {
                        buf[gy][gx] = max(buf[gy][gx], bright) // uses kotlin.math.max
                    }
                }
                cx += gw + if (i != text.lastIndex) spacing else 0
            }
        }
    }

    // Build font from 5-row digit strip (10 digits, 3px each, 1px separator)
    private fun fontFromFiveRows(rows: List<String>, digitWidth: Int = 3, sep: Int = 1): BitmapFont {
        require(rows.size == 5)
        val f = BitmapFont(height = 5, spacing = 1)
        for (d in 0..9) {
            val x0 = d * (digitWidth + sep)
            val glyph = Array(5) { r -> rows[r].substring(x0, x0 + digitWidth) }
            f.add(('0'.code + d).toChar(), glyph)
        }
        // colon (1×5, dots at rows 1 and 3), percent, FULL (temporary)
        f.add(':', arrayOf("0","1","0","1","0"))
        f.add('%', arrayOf("100","001","010","100","001"))
        f.add('F', arrayOf("111","100","110","100","100"))
        f.add('U', arrayOf("101","101","101","101","111"))
        f.add('L', arrayOf("100","100","100","100","111"))
        return f
    }

    // TODO: replace with your designs when ready
    private val DIGIT_ROWS = listOf(
        "010001001100110010101110010011100110010",
        "101011000010001010101000100000101010101",
        "101001000100010011101100111001000100111",
        "101001001000001000100010101001001010001",
        "010011101110110000101100010001001100010"
    )
    private val font = fontFromFiveRows(DIGIT_ROWS).apply { spacing = 1 }

    private fun centerX(text: String) = ((25 - font.measure(text)).coerceAtLeast(0)) / 2

    private fun drawHHMM(h: Int, m: Int, colonOn: Boolean, y: Int, bright: Int) {
        val template = "00:00"                 // constant width = 17 px with your font
        var cx = centerX(template)

        // hours
        font.drawChar(buf, ('0'.code + h/10).toChar(), cx, y, bright)
        cx += font.width('0') + font.spacing
        font.drawChar(buf, ('0'.code + h%10).toChar(), cx, y, bright)
        cx += font.width('0') + font.spacing

        // colon slot: always advance by colon width + spacing
        val colonX = cx
        if (colonOn) font.drawChar(buf, ':', colonX, y, bright)
        cx += font.width(':') + font.spacing

        // minutes
        font.drawChar(buf, ('0'.code + m/10).toChar(), cx, y, bright)
        cx += font.width('0') + font.spacing
        font.drawChar(buf, ('0'.code + m%10).toChar(), cx, y, bright)
    }


    private fun drawBattery(pct: Int, y: Int, bright: Int) {
        val s = if (pct >= 80) "FULL" else "%02d%%".format(pct)
        font.draw(buf, s, x = centerX(s), y = y, bright = bright)
    }

    private fun drawBars(mags: IntArray, baseBright: Int) {
        for (i in mags.indices) {
            val h = min(12, mags[i] / 10) // 0..12 rows
            val b = quantize(compressMag(mags[i]), steps = 16).coerceAtLeast(24)
            val x = 1 + i * 2
            for (yy in 24 downTo 24 - h) {
                val v = (b * baseBright / 255) * WY[yy] / 255
                setMax(buf, x, yy, v)
            }
        }
    }

    // ----- buffer ops -----
    private fun clearAndDecay(numer: Int) {
        for (y in 0 until 25) for (x in 0 until 25)
            buf[y][x] = (buf[y][x] * numer) ushr 8
    }
    private fun setMax(b: Array<IntArray>, x: Int, y: Int, v: Int) {
        if (x in 0..24 && y in 0..24) b[y][x] = max(b[y][x], v.coerceIn(0, 255))
    }
    private fun blit(): IntArray {
        val out = IntArray(25 * 25)
        var k = 0
        for (y in 0 until 25) for (x in 0 until 25) out[k++] = buf[y][x].coerceIn(0, 255)
        return out
    }

    // ----- utils -----
    private fun readBattery(): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
    private fun compressMag(mag: Int): Int {
        val m = mag.coerceAtLeast(0)
        return (255.0 * ln(1.0 + m / 64.0) / ln(1.0 + 255.0 / 64.0)).toInt()
    }
    private fun quantize(v: Int, steps: Int): Int =
        (v * steps / 255) * (255 / steps)

    private fun compressFft(fft: ByteArray, bars: Int): IntArray {
        val n = fft.size / 2
        val mags = IntArray(bars)
        for (i in 0 until bars) {
            val start = (i * n) / bars
            val end = (((i + 1) * n) / bars).coerceAtMost(n)
            var acc = 0.0
            var cnt = 0
            var k = start
            while (k < end && k * 2 + 1 < fft.size) {
                val re = fft[k * 2].toInt()
                val im = fft[k * 2 + 1].toInt()
                acc += kotlin.math.hypot(re.toDouble(), im.toDouble())
                cnt++
                k++
            }
            mags[i] = if (cnt == 0) 0 else min(255, (acc / cnt).toInt())
        }
        return mags
    }
}
