package vn.daopham.codplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Đọc tín hiệu từ VizBuffer, tính FFT và trả về mức của từng cột phổ. */
class Analyzer(val bars: Int) {
    private val n = 2048
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val buf = FloatArray(n)
    private val win = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }
    val levels = FloatArray(bars)
    val peaks = FloatArray(bars)
    private val hold = IntArray(bars)
    val wave = FloatArray(256)
    var bass = 0f
        private set
    var energy = 0f
        private set
    private var pos = 0.0
    private var lastNs = 0L
    private var lastWritten = -1L

    fun update(playing: Boolean, delayMs: Int) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016 else ((now - lastNs) / 1e9).coerceIn(0.0, 0.1)
        lastNs = now
        val written = VizBuffer.written
        val fresh = written != lastWritten
        lastWritten = written
        if (!playing) { decay(dt.toFloat()); return }
        val fs = VizBuffer.sampleRate.toDouble()
        val target = written - delayMs * fs / 1000.0
        pos += dt * fs
        val diff = target - pos
        if (abs(diff) > fs * 0.4) pos = target else pos += diff * 0.08
        if (!fresh && pos > written) { decay(dt.toFloat()); return }
        val end = min(pos.toLong(), written)
        VizBuffer.readAt(buf, end)

        for (i in 0 until n) { re[i] = buf[i] * win[i]; im[i] = 0.0 }
        fft(re, im)

        val binHz = fs / n
        val fLo = 35.0
        val fHi = min(16000.0, fs / 2 * 0.95)
        val ratio = fHi / fLo
        var bSum = 0f
        val bassBars = max(1, bars / 8)
        for (b in 0 until bars) {
            val f0 = fLo * ratio.pow(b.toDouble() / bars)
            val f1 = fLo * ratio.pow((b + 1).toDouble() / bars)
            var k0 = (f0 / binHz).toInt().coerceIn(1, n / 2 - 1)
            val k1 = (f1 / binHz).toInt().coerceIn(k0, n / 2 - 1)
            var mag = 0.0
            while (k0 <= k1) {
                val m = sqrt(re[k0] * re[k0] + im[k0] * im[k0])
                if (m > mag) mag = m
                k0++
            }
            mag *= 4.0 / n
            val fc = sqrt(f0 * f1)
            val tilt = if (fc > 200) 3.0 * log2(fc / 200) else 0.0
            val db = 20 * log10(mag + 1e-9) + tilt
            val l = ((db + 62) / 58).toFloat().coerceIn(0f, 1f)
            val cur = levels[b]
            levels[b] = if (l > cur) cur * 0.35f + l * 0.65f else max(l, cur - 2.2f * dt.toFloat())
            if (levels[b] >= peaks[b]) { peaks[b] = levels[b]; hold[b] = 18 }
            else if (hold[b] > 0) hold[b]--
            else peaks[b] = max(0f, peaks[b] - 0.7f * dt.toFloat())
            if (b < bassBars) bSum += levels[b]
        }
        bass = bass * 0.5f + (bSum / bassBars) * 0.5f
        var e = 0f
        for (i in 0 until bars) e += levels[i]
        energy = e / bars
        val step = 4
        val startW = n - wave.size * step
        for (i in wave.indices) wave[i] = buf[startW + i * step]
    }

    private fun decay(dt: Float) {
        for (b in 0 until bars) {
            levels[b] = max(0f, levels[b] - 1.6f * dt)
            peaks[b] = max(0f, peaks[b] - 0.8f * dt)
        }
        bass *= 0.9f; energy *= 0.9f
        for (i in wave.indices) wave[i] *= 0.85f
    }

    private fun fft(x: DoubleArray, y: DoubleArray) {
        val size = x.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = x[i]; x[i] = x[j]; x[j] = t
                t = y[i]; y[i] = y[j]; y[j] = t
            }
        }
        var len = 2
        while (len <= size) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < size) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val tr = x[b] * cr - y[b] * ci
                    val ti = x[b] * ci + y[b] * cr
                    x[b] = x[a] - tr; y[b] = y[a] - ti
                    x[a] += tr; y[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

@Composable
fun VizStage(mode: VizMode, art: ImageBitmap?, title: String, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val h = LocalHud.current
    val an = remember { Analyzer(48) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { t ->
                an.update(PlayerHolder.isPlaying, Ui.vizDelay)
                frame = t
            }
        }
    }
    val src = remember { MutableInteractionSource() }
    Box(modifier.clickable(interactionSource = src, indication = null) { onTap() }, contentAlignment = Alignment.Center) {
        if (mode != VizMode.RING && art != null) {
            Image(art, null, Modifier.fillMaxSize().alpha(0.18f), contentScale = ContentScale.Crop)
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = frame / 1e9f
            when (mode) {
                VizMode.RING -> drawRing(an, h, t)
                VizMode.BARS -> drawBars(an, h, mirror = false)
                VizMode.MIRROR -> drawBars(an, h, mirror = true)
                VizMode.WAVE -> drawWave(an, h, t)
            }
        }
        if (mode == VizMode.RING) {
            Box(
                Modifier
                    .fillMaxSize(0.46f)
                    .graphicsLayer {
                        @Suppress("UNUSED_VARIABLE") val f = frame
                        val s = 1f + an.bass * 0.07f
                        scaleX = s; scaleY = s
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ArtOrEmblem(art, title, Modifier.fillMaxSize())
            }
        }
    }
}

/** Phổ nhỏ dùng trong thanh phát mini. */
@Composable
fun MiniBars(modifier: Modifier) {
    val h = LocalHud.current
    val an = remember { Analyzer(12) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { t -> an.update(PlayerHolder.isPlaying, Ui.vizDelay); frame = t }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = frame
        val bw = size.width / an.bars
        for (i in 0 until an.bars) {
            val lv = max(0.06f, an.levels[i])
            val bh = lv * size.height
            drawRect(
                lerp(h.primary, h.accent, lv),
                topLeft = Offset(i * bw + bw * 0.15f, size.height - bh),
                size = Size(bw * 0.7f, bh)
            )
        }
    }
}

private fun DrawScope.drawRing(an: Analyzer, h: Hud, t: Float) {
    val c = center
    val minDim = size.minDimension
    val r0 = minDim * 0.25f
    val maxLen = minDim * 0.2f
    val glowR = r0 * (1.9f + an.bass * 0.4f)
    drawCircle(
        Brush.radialGradient(listOf(h.glow.copy(alpha = 0.18f + 0.4f * an.bass), Color.Transparent), center = c, radius = glowR),
        radius = glowR, center = c
    )
    val nb = an.bars
    val total = nb * 2
    val rot = t * 0.12f
    val sw = (2 * PI * r0 / total * 0.55).toFloat()
    for (i in 0 until total) {
        val bi = if (i < nb) i else total - 1 - i
        val lv = an.levels[bi]
        val ang = (i.toFloat() / total) * 2f * PI.toFloat() - PI.toFloat() / 2f + rot
        val ca = cos(ang); val sa = sin(ang)
        val r1 = r0 + 10f
        val r2 = r1 + 4f + lv * maxLen
        drawLine(
            lerp(h.primary, h.accent, lv),
            Offset(c.x + ca * r1, c.y + sa * r1), Offset(c.x + ca * r2, c.y + sa * r2),
            strokeWidth = sw, cap = StrokeCap.Round
        )
        val pr = r1 + 8f + an.peaks[bi] * maxLen
        drawCircle(h.text.copy(alpha = 0.75f), radius = sw * 0.35f, center = Offset(c.x + ca * pr, c.y + sa * pr))
    }
    // vòng HUD xoay
    val ringR = r0 + 4f
    drawCircle(h.accent.copy(alpha = 0.55f), radius = ringR, center = c, style = Stroke(2f))
    val outer = r0 + maxLen + 26f
    val tl = Offset(c.x - outer, c.y - outer)
    val sz = Size(outer * 2, outer * 2)
    val deg = t * 22f
    for (k in 0 until 3) {
        drawArc(h.primary.copy(alpha = 0.8f), deg + k * 120f, 70f, false, tl, sz, style = Stroke(3f, cap = StrokeCap.Round))
    }
    val outer2 = outer + 12f
    for (k in 0 until 4) {
        drawArc(
            h.accent.copy(alpha = 0.5f), -deg * 1.4f + k * 90f, 28f, false,
            Offset(c.x - outer2, c.y - outer2), Size(outer2 * 2, outer2 * 2), style = Stroke(1.5f)
        )
    }
    val ticks = 72
    for (i in 0 until ticks) {
        val a = (i.toFloat() / ticks) * 2f * PI.toFloat()
        val len = if (i % 6 == 0) 10f else 4f
        val ro = outer2 + 8f
        drawLine(
            h.dim.copy(alpha = 0.5f),
            Offset(c.x + cos(a) * ro, c.y + sin(a) * ro), Offset(c.x + cos(a) * (ro + len), c.y + sin(a) * (ro + len)),
            strokeWidth = 1.5f
        )
    }
}

private fun DrawScope.drawBars(an: Analyzer, h: Hud, mirror: Boolean) {
    val w = size.width
    val hh = size.height
    val nb = an.bars
    val bw = w / nb
    val seg = 6f
    val gap = 2.5f
    val maxH = if (mirror) hh * 0.46f else hh * 0.9f
    val base = if (mirror) hh / 2 else hh * 0.96f
    val brushUp = Brush.verticalGradient(listOf(h.accent, h.primary), startY = base - maxH, endY = base)
    val brushDown = Brush.verticalGradient(listOf(h.primary.copy(alpha = 0.5f), h.accent.copy(alpha = 0.1f)), startY = base, endY = base + maxH)
    // lưới nền
    for (k in 1..4) {
        val y = base - maxH * k / 4f
        drawLine(h.dim.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), 1f)
    }
    for (i in 0 until nb) {
        val x = i * bw + bw * 0.12f
        val bwi = bw * 0.76f
        val lv = an.levels[i]
        val segs = ((lv * maxH) / (seg + gap)).toInt()
        for (s in 0 until segs) {
            val y = base - (s + 1) * (seg + gap)
            drawRect(brushUp, Offset(x, y), Size(bwi, seg))
            if (mirror) drawRect(brushDown, Offset(x, base + s * (seg + gap) + gap), Size(bwi, seg))
        }
        val py = base - an.peaks[i] * maxH - seg
        drawRect(h.text, Offset(x, py - 3f), Size(bwi, 3f))
        if (mirror) drawRect(h.text.copy(alpha = 0.4f), Offset(x, base + an.peaks[i] * maxH + seg), Size(bwi, 3f))
    }
    if (mirror) drawLine(h.accent.copy(alpha = 0.6f), Offset(0f, base), Offset(w, base), 1.5f)
}

private fun DrawScope.drawWave(an: Analyzer, h: Hud, t: Float) {
    val w = size.width
    val hh = size.height
    val mid = hh / 2
    for (k in 1..7) {
        val x = w * k / 8f
        drawLine(h.dim.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, hh), 1f)
    }
    for (k in 1..3) {
        val y = hh * k / 4f
        drawLine(h.dim.copy(alpha = if (k == 2) 0.2f else 0.07f), Offset(0f, y), Offset(w, y), 1f)
    }
    val amp = hh * 0.42f
    val gain = 1.6f
    val path = Path()
    val n = an.wave.size
    for (i in 0 until n) {
        val x = w * i / (n - 1)
        val y = mid - (an.wave[i] * gain).coerceIn(-1f, 1f) * amp
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, h.primary.copy(alpha = 0.25f), style = Stroke(14f, cap = StrokeCap.Round))
    drawPath(path, h.glow.copy(alpha = 0.5f), style = Stroke(6f, cap = StrokeCap.Round))
    drawPath(path, h.accent, style = Stroke(2.5f, cap = StrokeCap.Round))
    // vệt quét
    val sx = ((t * 0.35f) % 1f) * w
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, h.primary.copy(alpha = 0.12f)), startX = sx - w * 0.25f, endX = sx),
        Offset(sx - w * 0.25f, 0f), Size(w * 0.25f, hh)
    )
    val e = an.energy
    drawCircle(h.glow.copy(alpha = 0.6f * e), radius = 6f + e * 10f, center = Offset(sx, mid))
}
