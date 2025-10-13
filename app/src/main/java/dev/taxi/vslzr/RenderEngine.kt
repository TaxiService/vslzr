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
import kotlin.math.pow

// layout/mapping (used by your drawers)
//var USE_CIRCLE_MAP = true
var CIRCLE_INSET = 0
var COLS = 23; var ROWS = 23
var X0 = (25 - COLS)/2; var Y0 = (25 - ROWS)/2; var Y1 = Y0 + ROWS - 1
var CLOCK_Y = 2; var BATT_Y = 17
var FULL_THRESHOLD = 80
// frequency window
var LOW_CUT_HZ  = 50
var HIGH_CUT_HZ = 18000          // was 12000

var BETA_SPACING = 1.8           // log spacing; higher = more lows
var RESAMPLE_TILT = 1.7f         // >1 allocates more columns to highs
var COL_TILT_DB = 9f             // post-resample high shelf
var FLOOR_AFTER_AR = 4           // lower so tiny highs survive

// smoothing and floor
var SMOOTH_ALPHA = 0.35f

// silence handling
var SILENCE_GATE_SUM = 40  // sum of colsIn below this = silence
var ZERO_ON_SILENCE  = true

private var spansInsetCached = Int.MIN_VALUE
private fun ensureSpans() {
    //if (!USE_CIRCLE_MAP) return
    if (spansInsetCached != CIRCLE_INSET) {
        buildColumnSpans() // your half-pixel version
        spansInsetCached = CIRCLE_INSET
    }
}

fun reconfig(newBands: Int, newCols: Int) {
    if (newBands != BANDS) { BANDS = newBands.coerceIn(6, 32); ema = FloatArray(BANDS) }
    if (newCols != COLS)   { COLS  = newCols.coerceIn(12, 25); env = FloatArray(COLS) }
    X0 = (25 - COLS)/2; Y0 = (25 - ROWS)/2; Y1 = Y0 + ROWS - 1
    spansInsetCached = Int.MIN_VALUE
    ensureSpans()
}

private val yTopCol = IntArray(25)
private val yBotCol = IntArray(25)
private var spansBuilt = false


private fun buildColumnSpans() {
    val cx = 12.0
    val cy = 12.0
    val r  = 12.5 - CIRCLE_INSET.toDouble()   // half-pixel radius
    val r2 = r * r
    for (x in 0..24) {
        val dx = x - cx
        val d2 = r2 - dx*dx
        if (d2 <= 0.0) {
            val y = cy.toInt()
            yTopCol[x] = y.coerceIn(Y0, Y1)
            yBotCol[x] = y.coerceIn(Y0, Y1)
            continue
        }
        val dy = kotlin.math.sqrt(d2)
        val top = kotlin.math.ceil(cy - dy).toInt()     // ceil top
        val bot = kotlin.math.floor(cy + dy).toInt()    // floor bottom
        yTopCol[x] = top.coerceIn(Y0, Y1)
        yBotCol[x] = bot.coerceIn(Y0, Y1)
        if (yBotCol[x] < yTopCol[x]) yBotCol[x] = yTopCol[x] // guard
    }
    spansBuilt = true
}

// behavior toggles
var CLOCK_HIDE_WHEN_PLAYING = false
var BATT_HIDE_WHEN_PLAYING  = false
var FFT_ERASES_CLOCK        = false
var FFT_ERASES_BATT         = true

// battery mask for overlap logic
private val clockMask = Array(25) { BooleanArray(25) }
private val battMask  = Array(25) { BooleanArray(25) }
private fun clearMasks() {
    for (y in 0 until 25) {
        java.util.Arrays.fill(clockMask[y], false)
        java.util.Arrays.fill(battMask[y],  false)
    }
}
// tuning
var BRIGHT_HHMM = 255; var BRIGHT_BATT = 200; var BRIGHT_VIZ = 180
var FRAME_DECAY_IDLE = 220; var FRAME_DECAY_PLAY = 190

// analysis/post
var BANDS = 12
private var ema = FloatArray(BANDS)
private var env = FloatArray(COLS)
var EMA_ALPHA = 0.35f
var SCALE_LOW = 64.0; var SCALE_HIGH = 20.0; var TILT_DB = 9f
var GATE = 28; var GAIN = 1.2f; var GAMMA = 0.75f; var ATTACK = 0.55f; var RELEASE = 0.20f

class RenderEngine(
    private val ctx: Context,
    private val push: (IntArray) -> Unit
) {
    fun applyPrefs(p: android.content.SharedPreferences) {
        //USE_CIRCLE_MAP = p.getBoolean("use_circle_map", true)
        val newCols = p.getInt("cols", COLS)
        ROWS        = p.getInt("rows", ROWS).coerceIn(8,25)
        CIRCLE_INSET= p.getInt("circle_inset", 0)

        CLOCK_Y     = p.getInt("clock_y", CLOCK_Y)
        BATT_Y      = p.getInt("batt_y", BATT_Y)
        FULL_THRESHOLD = p.getInt("full_threshold", FULL_THRESHOLD)

        BRIGHT_HHMM = p.getInt("bright_hhmm", BRIGHT_HHMM)
        BRIGHT_BATT = p.getInt("bright_batt", BRIGHT_BATT)
        BRIGHT_VIZ  = p.getInt("bright_viz",  BRIGHT_VIZ)

        FRAME_DECAY_IDLE = p.getInt("frame_decay_idle", FRAME_DECAY_IDLE)
        FRAME_DECAY_PLAY = p.getInt("frame_decay_play", FRAME_DECAY_PLAY)

        CLOCK_HIDE_WHEN_PLAYING = p.getBoolean("hide_clock", false)
        BATT_HIDE_WHEN_PLAYING  = p.getBoolean("hide_batt",  false)
        FFT_ERASES_CLOCK        = p.getBoolean("erase_clock", false)
        FFT_ERASES_BATT         = p.getBoolean("erase_batt",  true)

        LOW_CUT_HZ  = p.getInt("low_cut_hz", 50)
        SMOOTH_ALPHA = p.getInt("smooth_alpha_x100", 35) / 100f
        SILENCE_GATE_SUM = p.getInt("silence_gate_sum", 40)
        ZERO_ON_SILENCE  = p.getBoolean("zero_on_silence", true)

        HIGH_CUT_HZ   = p.getInt("high_cut_khz", 18) * 1000
        BETA_SPACING  = p.getInt("beta_x100", 180) / 100.0
        RESAMPLE_TILT = p.getInt("resample_tilt_x100", 170) / 100f
        COL_TILT_DB   = p.getInt("col_tilt_db", 9).toFloat()
        FLOOR_AFTER_AR= p.getInt("floor_after_ar", 4)



        val newBands = p.getInt("bands", BANDS)
        EMA_ALPHA    = p.getInt("beta_x100", 200) / 100f  // if you used this for spacing, keep separate; else remove
        val beta     = p.getInt("beta_x100", 200) / 100.0
        SCALE_LOW    = p.getInt("scale_low", SCALE_LOW.toInt()).toDouble()
        SCALE_HIGH   = p.getInt("scale_high", SCALE_HIGH.toInt()).toDouble()
        TILT_DB      = p.getInt("tilt_db", TILT_DB.toInt()).toFloat()

        GATE   = p.getInt("gate", GATE)
        GAIN   = p.getInt("gain_x100", (GAIN*100).toInt()) / 100f
        GAMMA  = p.getInt("gamma_x100", (GAMMA*100).toInt()) / 100f
        ATTACK = p.getInt("attack_x100", (ATTACK*100).toInt()) / 100f
        RELEASE= p.getInt("release_x100", (RELEASE*100).toInt()) / 100f

        reconfig(newBands, newCols)
    }


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

    // ----- public controls -----
    fun start() {
        if (running) return
        running = true
        ensureSpans()
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
                ensureSpans()
                val now = SystemClock.elapsedRealtime()
                if (now - lastBattRead > 1_000L) { battPct = readBattery(); lastBattRead = now }
                val playing = MediaBridge.isPlaying(ctx)
                clearAndDecay(if (playing) FRAME_DECAY_PLAY else FRAME_DECAY_IDLE)
                clearMasks()

                // time (blink colon 1 Hz, 0.5 s duty)
                val t = LocalTime.now()
                val colonOn = (now / 500L) % 2L == 0L
                if (!(CLOCK_HIDE_WHEN_PLAYING && playing)) {
                    val nowColon = (now / 500L) % 2L == 0L
                    drawHHMM(t.hour, t.minute, nowColon, y = CLOCK_Y, bright = BRIGHT_HHMM, markMask = true)
                }
                if (!(BATT_HIDE_WHEN_PLAYING && playing)) {
                    drawBattery(readBattery(), y = BATT_Y, bright = BRIGHT_BATT, markMask = true)
                }

                // FFT overlay if playing and visualizer ready
                vis?.let { v ->
                    if (playing) {
                        try {
                            val fft = ByteArray(v.captureSize)
                            v.getFft(fft)
                            var sr = v.samplingRate; if (sr > 200_000) sr /= 1000; if (sr <= 0) sr = 44100

                            val bands  = fftToBandsLogWindowed(
                                fft, bands = BANDS, sampleRateHz = sr,
                                lowHz = LOW_CUT_HZ.coerceAtLeast(20),
                                highHz = HIGH_CUT_HZ.coerceAtMost(sr / 2),
                                beta = BETA_SPACING, scaleLow = SCALE_LOW, scaleHigh = SCALE_HIGH
                            )
                            val smooth = smoothBarsWithAlpha(bands, alpha = SMOOTH_ALPHA)
                            val colsIn = resampleToColsTilt(smooth, cols = COLS, tilt = RESAMPLE_TILT)

// high-shelf across columns (0 dB → +COL_TILT_DB at rightmost)
                            val colsShelved = IntArray(COLS) { i ->
                                val t = i.toFloat() / (COLS - 1).coerceAtLeast(1)
                                val g = 10.0.pow((COL_TILT_DB * t) / 20.0).toFloat()
                                (colsIn[i] * g).toInt().coerceIn(0, 255)
                            }

// gate + gamma + AR
                            val colsOut = IntArray(COLS)
                            val sumIn = colsShelved.sum()
                            for (i in 0 until COLS) {
                                var v255 = (colsShelved[i] * GAIN).toInt().coerceIn(0, 255)
                                v255 = (v255 - GATE).coerceAtLeast(0)
                                val n = (v255 / 255f).toDouble().pow(GAMMA.toDouble()).toFloat()
                                val target = (255f * n).toInt()
                                val a = if (target > env[i].toInt()) ATTACK else RELEASE
                                env[i] += a * (target - env[i])
                                var out = env[i].toInt().coerceIn(0, 255)
                                if (out < FLOOR_AFTER_AR) out = 0
                                colsOut[i] = out
                            }
                            if (ZERO_ON_SILENCE && sumIn < SILENCE_GATE_SUM) {
                                java.util.Arrays.fill(colsOut, 0)
                                for (i in 0 until COLS) env[i] *= 0.85f
                            }
                            drawColumnsMapped(colsOut, BRIGHT_VIZ, FFT_ERASES_BATT, FFT_ERASES_CLOCK)

                        } catch (_: Throwable) {}
                    }
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

    fun toggleBoost() { BRIGHT_VIZ = if (BRIGHT_VIZ < 200) 220 else 160 }
    fun seedGrid() {
        for (y in 0 until 25) for (x in 0 until 25)
            buf[y][x] = if ((x + y) % 2 == 0) 64 else 0
    }

    // ----- drawing -----

    // Simple 1-bit bitmap font with variable widths
    class BitmapFont(val height: Int, var spacing: Int = 1) {
        fun isOn(ch: Char, r: Int, c: Int): Boolean =
            glyphs[ch]?.getOrNull(r)?.getOrNull(c) == true
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

    private fun drawHHMM(h: Int, m: Int, colonOn: Boolean, y: Int, bright: Int, markMask: Boolean) {
        val template = "00:00"
        var cx = centerX(template)

        fun put(ch: Char) {
            font.draw(buf, ch.toString(), cx, y, bright)
            if (markMask) {
                val w = font.width(ch)
                for (r in 0 until font.height) for (c in 0 until w) {
                    if (font.isOn(ch, r, c)) {
                        val gx = cx + c; val gy = y + r
                        if (gx in 0..24 && gy in 0..24) clockMask[gy][gx] = true
                    }
                }
            }
            cx += font.width(ch) + font.spacing
        }

        put(('0'.code + h/10).toChar())
        put(('0'.code + h%10).toChar())
        if (colonOn) put(':') else cx += font.width(':') + font.spacing
        put(('0'.code + m/10).toChar())
        put(('0'.code + m%10).toChar())
    }



    private fun drawBattery(pct: Int, y: Int, bright: Int, markMask: Boolean) {
        val label = if (pct >= FULL_THRESHOLD || isBatteryFull()) "FULL" else "%02d%%".format(pct)
        val x = centerX(label)
        font.draw(buf, label, x, y, bright)
        if (!markMask) return
        var cx = x
        for (ch in label) {
            val w = font.width(ch)
            for (r in 0 until font.height) for (c in 0 until w) {
                if (font.isOn(ch, r, c)) {
                    val gx = cx + c; val gy = y + r
                    if (gx in 0..24 && gy in 0..24) battMask[gy][gx] = true
                }
            }
            cx += w + if (ch != label.last()) font.spacing else 0
        }
    }


    private fun weightAt(y: Int, bottom: Int): Int {
        // 1.0 at bottom, ~0.35 near the top of the bar span
        val dist = (bottom - y).coerceAtLeast(0)
        val span = max(1, bottom) // normalize by screen coords
        val t = dist.toFloat() / span
        val w = 0.40f + 0.60f * (1f - t * t)
        return (w * 255f).toInt()
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
        // Prefer sticky broadcast with rounding
        val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val i = ctx.registerReceiver(null, ifilter)
        if (i != null) {
            val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (level >= 0) return kotlin.math.round(level * 100f / scale).toInt().coerceIn(0, 100)
        }
        // Fallback to BatteryManager property (often floored)
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
    private fun isBatteryFull(): Boolean {
        val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val status = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_FULL
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
    private fun smoothBars(raw: IntArray, alpha: Float): IntArray {
        val out = IntArray(raw.size)
        for (i in raw.indices) {
            ema[i] = ema[i] * (1f - alpha) + raw[i] * alpha
            out[i] = ema[i].toInt().coerceIn(0, 255)
        }
        return out
    }
    private fun smoothBarsWithAlpha(raw: IntArray, alpha: Float): IntArray {
        if (ema.size != raw.size) java.util.Arrays.fill(ema, 0f)
        val out = IntArray(raw.size)
        for (i in raw.indices) {
            ema[i] = ema[i] * (1f - alpha) + raw[i] * alpha
            out[i] = ema[i].toInt().coerceIn(0, 255)
        }
        return out
    }
    private fun fftToBandsLogWindowed(
        fft: ByteArray,
        bands: Int,
        sampleRateHz: Int,
        lowHz: Int,
        highHz: Int,
        beta: Double = 2.0,
        scaleLow: Double = 64.0,
        scaleHigh: Double = 20.0
    ): IntArray {
        val n = fft.size / 2
        val nyq = sampleRateHz / 2.0
        val startBin = ((lowHz / nyq) * (n - 1)).coerceAtLeast(1.0).toInt()
        val endBin   = ((highHz / nyq) * (n - 1)).coerceIn(2.0, (n - 1).toDouble()).toInt()
        val span = (endBin - startBin).coerceAtLeast(bands)

        // edges with beta^ curve across [startBin..endBin]
        val edges = IntArray(bands + 1)
        for (i in 0..bands) {
            val t = i.toDouble() / bands
            val f = t.pow(beta)
            edges[i] = (startBin + f * span).toInt().coerceIn(startBin, endBin)
            if (i > 0 && edges[i] <= edges[i - 1]) edges[i] = (edges[i - 1] + 1).coerceAtMost(endBin)
        }

        val out = IntArray(bands)
        for (i in 0 until bands) {
            val s = edges[i]
            val e = edges[i + 1]
            var acc = 0.0; var cnt = 0
            var k = s
            while (k < e && k * 2 + 1 < fft.size) {
                val re = fft[k * 2].toInt()
                val im = fft[k * 2 + 1].toInt()
                acc += kotlin.math.hypot(re.toDouble(), im.toDouble())
                cnt++; k++
            }
            val avg = if (cnt == 0) 0.0 else acc / cnt
            val t = i.toDouble() / (bands - 1).coerceAtLeast(1)
            val scale = scaleLow + (scaleHigh - scaleLow) * t
            val comp = (255.0 * ln(1.0 + avg / scale) / ln(1.0 + 255.0 / scale)).toInt()
            out[i] = comp.coerceIn(0, 255)
        }
        return out
    }
    private fun fftToBandsLog(
        fft: ByteArray,
        bands: Int,
        beta: Double = 2.0,
        scaleLow: Double = 64.0,
        scaleHigh: Double = 64.0
    ): IntArray {
        val n = fft.size / 2
        val maxBin = (n - 1).coerceAtLeast(2)
        val edges = IntArray(bands + 1)
        edges[0] = 1 // skip DC
        for (i in 1..bands) {
            val t = i.toDouble() / bands
            val e = Math.exp(Math.log(maxBin.toDouble()) * Math.pow(t, beta)).toInt()
            edges[i] = e.coerceAtLeast(edges[i - 1] + 1).coerceAtMost(maxBin)
        }
        val out = IntArray(bands)
        for (i in 0 until bands) {
            val start = edges[i]
            val end   = edges[i + 1]
            var acc = 0.0; var cnt = 0
            var k = start
            while (k < end && k * 2 + 1 < fft.size) {
                val re = fft[k * 2].toInt()
                val im = fft[k * 2 + 1].toInt()
                acc += kotlin.math.hypot(re.toDouble(), im.toDouble())
                cnt++; k++
            }
            val avg = if (cnt == 0) 0.0 else acc / cnt
            // per-band knee: smaller scale at higher bands → more boost up high
            val t = i.toDouble() / (bands - 1).coerceAtLeast(1)
            val scale = scaleLow + (scaleHigh - scaleLow) * t
            val comp = (255.0 * ln(1.0 + avg / scale) / ln(1.0 + 255.0 / scale)).toInt()
            out[i] = comp.coerceIn(0, 255)
        }
        return out
    }
    private fun resampleToColsTilt(bands: IntArray, cols: Int, tilt: Float): IntArray {
        val out = IntArray(cols)
        val maxIdx = bands.lastIndex
        for (x in 0 until cols) {
            val t  = x.toFloat() / (cols - 1).coerceAtLeast(1)
            val u  = t.pow(tilt)                   // tilt>1 → more columns to highs
            val pos = u * maxIdx
            val i0 = pos.toInt().coerceIn(0, maxIdx)
            val i1 = (i0 + 1).coerceAtMost(maxIdx)
            val f  = pos - i0
            out[x] = ((1f - f) * bands[i0] + f * bands[i1]).toInt().coerceIn(0, 255)
        }
        return out
    }
    private fun tiltBands(bands: IntArray, tiltDb: Float): IntArray {
        val n = bands.size
        val out = IntArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / (n - 1).coerceAtLeast(1)
            val gain = 10.0.pow((tiltDb * t) / 20.0).toFloat()
            out[i] = (bands[i] * gain).toInt().coerceIn(0, 255)
        }
        return out
    }
    private fun applyGamma(cols: IntArray, gamma: Float): IntArray {
        val out = IntArray(cols.size)
        for (i in cols.indices) {
            val n = (cols[i].coerceIn(0, 255)) / 255f
            out[i] = (255f * n.pow(gamma)).toInt()
        }
        return out
    }

    private fun clearBattMask() {
        for (y in 0 until 25) java.util.Arrays.fill(battMask[y], false)
    }

    // expose pixel test from the font
    private fun BitmapFont.isOn(ch: Char, r: Int, c: Int): Boolean {
        val g = try { // access internal glyph safely
            val f = BitmapFont::class.java.getDeclaredField("glyphs").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (f.get(this) as MutableMap<Char, Array<BooleanArray>>)[ch]
        } catch (_: Throwable) { null }
        return g?.getOrNull(r)?.getOrNull(c) == true
    }

    private fun drawColumnsMapped(cols: IntArray, baseBright: Int,
                                  eraseBatt: Boolean, eraseClock: Boolean) {
        for (i in 0 until COLS) {
            val x = X0 + i
            val yTop = yTopCol[x]; val yBot = yBotCol[x]
            val span = (yBot - yTop).coerceAtLeast(1)
            val h = (cols[i] * span / 255).coerceIn(0, span)
            if (h == 0) continue
            val b = quantize(compressMag(cols[i]), 16).coerceAtLeast(24)
            val yDrawTop = yBot - h
            for (yy in yBot downTo yDrawTop) {
                if (eraseBatt  && battMask[yy][x]) { buf[yy][x] = 0; continue }
                if (eraseClock && clockMask[yy][x]) { buf[yy][x] = 0; continue }
                val v = (b * baseBright / 255) * weightAt(yy, yBot) / 255
                buf[yy][x] = max(buf[yy][x], v)
            }
        }
    }

}
