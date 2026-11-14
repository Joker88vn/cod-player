package vn.daopham.codplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
fun VizStage(mode: VizMode, art: ImageBitmap?, c1: Color, c2: Color, modifier: Modifier = Modifier, onTap: () -> Unit) {
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
        when (mode) {
            VizMode.ART -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .padding(6.dp)
                            .shadow(24.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                    ) { ArtOrEmblem(art, Modifier.fillMaxSize(), 72.dp) }
                }
                Spacer(Modifier.height(10.dp))
                Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                    @Suppress("UNUSED_VARIABLE") val f = frame
                    drawStrip(an, c1, c2)
                }
            }
            VizMode.RING -> {
                Canvas(Modifier.fillMaxSize()) {
                    @Suppress("UNUSED_VARIABLE") val f = frame
                    drawRing(an, c1, c2)
                }
                Box(
                    Modifier
                        .fillMaxSize(0.52f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            @Suppress("UNUSED_VARIABLE") val f = frame
                            val s = 1f + an.bass * 0.05f
                            scaleX = s; scaleY = s
                        }
                        .shadow(20.dp, CircleShape)
                        .clip(CircleShape)
                ) { ArtOrEmblem(art, Modifier.fillMaxSize(), 56.dp) }
            }
            VizMode.BARS -> Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawBars(an, c1, c2)
            }
            VizMode.WAVE -> Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE") val f = frame
                drawWave(an, c1, c2)
            }
        }
    }
}

/** Phổ nhỏ dùng trong danh sách / thanh phát mini. */
@Composable
fun MiniBars(modifier: Modifier, color: Color) {
    val an = remember { Analyzer(4) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { t -> an.update(PlayerHolder.isPlaying, Ui.vizDelay); frame = t }
        }
    }
    Canvas(modifier) {
        @Suppress("UNUSED_VARIABLE") val f = frame
        val n = an.bars
        val bw = size.width / (n * 1.6f)
        val gap = (size.width - bw * n) / (n - 1)
        for (i in 0 until n) {
            val lv = max(0.15f, an.levels[i])
            val bh = lv * size.height
            drawRoundRect(color, Offset(i * (bw + gap), size.height - bh), Size(bw, bh), CornerRadius(bw / 2))
        }
    }
}

private fun DrawScope.drawStrip(an: Analyzer, c1: Color, c2: Color) {
    val n = an.bars
    val w = size.width
    val hh = size.height
    val bw = w / n
    val barW = bw * 0.55f
    val mid = hh / 2
    for (i in 0 until n) {
        val lv = an.levels[i]
        val bh = max(2.5f, lv * hh)
        val x = i * bw + (bw - barW) / 2
        drawRoundRect(lerp(c1, c2, lv).copy(alpha = 0.55f + 0.45f * lv), Offset(x, mid - bh / 2), Size(barW, bh), CornerRadius(barW / 2))
    }
}

private fun DrawScope.drawRing(an: Analyzer, c1: Color, c2: Color) {
    val c = center
    val minDim = size.minDimension
    val r0 = minDim * 0.27f
    val maxLen = minDim * 0.17f
    val glowR = r0 * (1.6f + an.bass * 0.35f)
    drawCircle(
        Brush.radialGradient(listOf(c1.copy(alpha = 0.10f + 0.25f * an.bass), Color.Transparent), center = c, radius = glowR),
        radius = glowR, center = c
    )
    val nb = an.bars
    val total = nb * 2
    val sw = (2 * PI * r0 / total * 0.45).toFloat()
    for (i in 0 until total) {
        val bi = if (i < nb) i else total - 1 - i
        val lv = an.levels[bi]
        val ang = (i.toFloat() / total) * 2f * PI.toFloat() - PI.toFloat() / 2f
        val ca = cos(ang); val sa = sin(ang)
        val r1 = r0 + 14f
        val r2 = r1 + 2f + lv * maxLen
        drawLine(
            lerp(c1, c2, lv).copy(alpha = 0.5f + 0.5f * lv),
            Offset(c.x + ca * r1, c.y + sa * r1), Offset(c.x + ca * r2, c.y + sa * r2),
            strokeWidth = sw, cap = StrokeCap.Round
        )
    }
    drawCircle(Color.White.copy(alpha = 0.08f), radius = r0 + 6f, center = c, style = Stroke(1.5f))
}

private fun DrawScope.drawBars(an: Analyzer, c1: Color, c2: Color) {
    val w = size.width
    val hh = size.height
    val nb = an.bars
    val bw = w / nb
    val barW = bw * 0.62f
    val base = hh * 0.66f
    val maxH = hh * 0.6f
    val up = Brush.verticalGradient(listOf(c2, c1), startY = base - maxH, endY = base)
    val down = Brush.verticalGradient(listOf(c1.copy(alpha = 0.28f), Color.Transparent), startY = base, endY = base + maxH * 0.5f)
    for (k in 1..3) {
        val y = base - maxH * k / 3f
        drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, y), Offset(w, y), 1f)
    }
    for (i in 0 until nb) {
        val x = i * bw + (bw - barW) / 2
        val bh = max(3f, an.levels[i] * maxH)
        drawRoundRect(up, Offset(x, base - bh), Size(barW, bh), CornerRadius(barW / 2))
        drawRoundRect(down, Offset(x, base + 4f), Size(barW, bh * 0.5f), CornerRadius(barW / 2))
        val py = base - an.peaks[i] * maxH - 8f
        drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(x, py), Size(barW, 3f), CornerRadius(1.5f))
    }
}

private fun DrawScope.drawWave(an: Analyzer, c1: Color, c2: Color) {
    val w = size.width
    val hh = size.height
    val mid = hh / 2
    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, mid), Offset(w, mid), 1f)
    val amp = hh * 0.38f
    val n = an.wave.size
    val path = Path()
    for (i in 0 until n) {
        val x = w * i / (n - 1)
        val env = sin(PI * i / (n - 1)).toFloat()
        val y = mid - (an.wave[i] * 1.6f).coerceIn(-1f, 1f) * amp * env
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val brush = Brush.horizontalGradient(listOf(c1, c2, c1))
    drawPath(path, c1.copy(alpha = 0.18f), style = Stroke(16f, cap = StrokeCap.Round))
    drawPath(path, brush, style = Stroke(3f, cap = StrokeCap.Round))
}
