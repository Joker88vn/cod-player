package vn.daopham.codplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private fun fmtDb(v: Float): String = when {
    abs(v) < 0.05f -> "0"
    v > 0 -> "+%.1f".format(v)
    else -> "%.1f".format(v)
}

@Composable
fun EqScreen() {
    val h = LocalHud.current
    var saveOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().card(h).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Equalizer", 16.sp, h.text, FontWeight.SemiBold)
                Txt(if (EqState.enabled) EqState.preset else "Đang tắt", 12.sp, if (EqState.enabled) h.primary else h.dim)
            }
            Switch(
                EqState.enabled, { EqState.enabled = it; EqState.push() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = h.primary, uncheckedTrackColor = h.surface2)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (BuiltInPresets + EqState.custom).forEach { p ->
                PillChip(p.name, EqState.preset == p.name) { EqState.apply(p) }
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.alpha(if (EqState.enabled) 1f else 0.4f)) {
            EqPanel()
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().card(h).padding(vertical = 14.dp)) {
                SectionLabel("Âm sắc", Modifier.padding(start = 16.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob("Preamp", fmtDb(EqState.preamp) + " dB", EqState.preamp, -12f, 12f, 0f) { EqState.preamp = it; EqState.push() }
                    Knob("Bass", fmtDb(EqState.bass) + " dB", EqState.bass, -12f, 12f, 0f) { EqState.bass = it; EqState.preset = "Tuỳ chỉnh"; EqState.push() }
                    Knob("Treble", fmtDb(EqState.treble) + " dB", EqState.treble, -12f, 12f, 0f) { EqState.treble = it; EqState.preset = "Tuỳ chỉnh"; EqState.push() }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Knob("Stereo", "${(EqState.width * 100).roundToInt()}%", EqState.width, 0f, 2f, 1f) { EqState.width = it; EqState.push() }
                    val b = EqState.balance
                    Knob(
                        "Cân bằng",
                        if (abs(b) < 0.02f) "Giữa" else if (b < 0) "Trái ${(-b * 100).roundToInt()}" else "Phải ${(b * 100).roundToInt()}",
                        b, -1f, 1f, 0f
                    ) { EqState.balance = if (abs(it) < 0.03f) 0f else it; EqState.push() }
                    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                            Switch(
                                EqState.limiter, { EqState.limiter = it; EqState.push() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = h.primary, uncheckedTrackColor = h.surface2)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Txt("Limiter", 12.sp, h.text, FontWeight.Medium)
                        Txt(if (EqState.limiter) "Chống vỡ" else "Tắt", 11.sp, h.dim)
                    }
                }
                Text(
                    "Kéo lên/xuống trên núm để chỉnh · chạm 2 lần để về mặc định",
                    color = h.dim.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Đặt lại", Icons.Rounded.RestartAlt, filled = false) { EqState.reset() }
            PillButton("Lưu preset", Icons.Rounded.Save) { saveOpen = true }
            val custom = EqState.custom.firstOrNull { it.name == EqState.preset }
            if (custom != null) {
                PillButton("Xoá", Icons.Rounded.Delete, filled = false) {
                    EqState.custom.remove(custom); EqState.preset = "Tuỳ chỉnh"; Prefs.saveEq()
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    if (saveOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveOpen = false },
            title = { Text("Lưu preset") },
            text = { OutlinedTextField(name, { name = it.take(24) }, label = { Text("Tên preset") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim().replace("=", "-")
                    if (n.isNotEmpty()) {
                        EqState.custom.removeAll { it.name == n }
                        EqState.custom.add(EqState.snapshot(n))
                        EqState.preset = n
                        Prefs.saveEq()
                    }
                    saveOpen = false
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { saveOpen = false }) { Text("Huỷ") } },
            containerColor = h.surface2
        )
    }
}

/** 10 thanh EQ + đường cong đáp tuyến vẽ xuyên qua các núm. */
@Composable
private fun EqPanel() {
    val h = LocalHud.current
    val gains = EqState.gains.toList()
    val bass = EqState.bass
    val treble = EqState.treble
    val pre = EqState.preamp
    val filters = remember(gains, bass, treble) {
        val fs = 48000.0
        val l = ArrayList<Biquad>()
        for (i in 0 until 10) if (abs(gains[i]) > 0.05f) l += Biquad().apply { setPeaking(fs, EQ_FREQS[i].toDouble(), EQ_Q, gains[i].toDouble()) }
        if (abs(bass) > 0.05f) l += Biquad().apply { setLowShelf(fs, BASS_HZ, bass.toDouble()) }
        if (abs(treble) > 0.05f) l += Biquad().apply { setHighShelf(fs, TREBLE_HZ, treble.toDouble()) }
        l
    }
    Column(Modifier.fillMaxWidth().card(h).padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            for (i in 0 until 10) {
                Text(
                    fmtDb(gains[i]), color = if (abs(gains[i]) > 0.05f) h.primary else h.dim.copy(alpha = 0.7f),
                    fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 6.dp)) {
            // đường cong
            Canvas(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                val w = size.width
                val hh = size.height
                val col = w / 10f
                val xs = FloatArray(10) { col * it + col / 2 }
                val lf = DoubleArray(10) { ln(EQ_FREQS[it].toDouble()) }
                fun xOf(f: Double): Float {
                    val l = ln(f)
                    if (l <= lf[0]) return (xs[0] - (lf[0] - l) / (lf[1] - lf[0]) * col).toFloat().coerceAtLeast(0f)
                    for (k in 0 until 9) if (l <= lf[k + 1]) return (xs[k] + (l - lf[k]) / (lf[k + 1] - lf[k]) * col).toFloat()
                    return (xs[9] + (l - lf[9]) / (lf[9] - lf[8]) * col).toFloat().coerceAtMost(w)
                }
                fun yOf(db: Float) = hh / 2 - (db / 12f).coerceIn(-1.15f, 1.15f) * (hh / 2)
                for (k in listOf(-12, -6, 0, 6, 12)) {
                    val y = yOf(k.toFloat())
                    drawLine(Color.White.copy(alpha = if (k == 0) 0.12f else 0.05f), Offset(0f, y), Offset(w, y), 1f)
                }
                val path = Path(); val fill = Path()
                val pts = 140
                for (i in 0..pts) {
                    val f = 20.0 * 10.0.pow(3.0 * i / pts)
                    var db = pre.toDouble()
                    for (b in filters) db += b.magDb(f, 48000.0)
                    val x = xOf(f)
                    val y = yOf(db.toFloat())
                    if (i == 0) { path.moveTo(x, y); fill.moveTo(x, hh / 2); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
                }
                fill.lineTo(w, hh / 2); fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(h.primary.copy(alpha = 0.28f), h.primary.copy(alpha = 0.0f), h.primary.copy(alpha = 0.28f))))
                drawPath(path, h.primary, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round))
            }
            Row(Modifier.fillMaxSize()) {
                for (i in 0 until 10) {
                    VSlider(EqState.gains[i], Modifier.weight(1f).fillMaxSize()) { v ->
                        EqState.gains[i] = v; EqState.preset = "Tuỳ chỉnh"; EqState.push()
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            for (i in 0 until 10) {
                Text(
                    EQ_LABELS[i], color = h.dim, fontSize = 10.5.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun VSlider(value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    val h = LocalHud.current
    val cb by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { cb(0f) })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    val pad = 10.dp.toPx()
                    val hh = size.height - pad * 2
                    val v = ((1f - (change.position.y - pad) / hh) * 24f - 12f).coerceIn(-12f, 12f)
                    cb((v * 2f).roundToInt() / 2f)
                }
            }
    ) {
        val pad = 10.dp.toPx()
        val hh = size.height - pad * 2
        val cx = size.width / 2
        val y = pad + (1f - (value + 12f) / 24f) * hh
        drawLine(Color.White.copy(alpha = 0.09f), Offset(cx, pad), Offset(cx, pad + hh), 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.Black.copy(alpha = 0.35f), 10.dp.toPx(), Offset(cx, y + 1.5f))
        drawCircle(h.surface2, 9.dp.toPx(), Offset(cx, y))
        drawCircle(h.primary, 9.dp.toPx(), Offset(cx, y), style = Stroke(2.5f.dp.toPx()))
        drawCircle(Color.White, 3.dp.toPx(), Offset(cx, y))
    }
}

/** Núm xoay: kéo dọc để chỉnh, chạm 2 lần để về mặc định. */
@Composable
private fun Knob(label: String, valueText: String, value: Float, min: Float, max: Float, def: Float, onChange: (Float) -> Unit) {
    val h = LocalHud.current
    val cb by rememberUpdatedState(onChange)
    val cur by rememberUpdatedState(value)
    Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(72.dp)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { cb(def) }) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, drag ->
                        change.consume()
                        val nv = (cur - drag / (160.dp.toPx()) * (max - min)).coerceIn(min, max)
                        cb(nv)
                    }
                }
        ) {
            val c = center
            val r = size.minDimension / 2
            val sw = 4.dp.toPx()
            val arcR = r - sw
            val tl = Offset(c.x - arcR, c.y - arcR)
            val sz = Size(arcR * 2, arcR * 2)
            val start = 135f
            val sweep = 270f
            drawArc(Color.White.copy(alpha = 0.09f), start, sweep, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            val fv = (value - min) / (max - min)
            val fd = (def - min) / (max - min)
            val a0 = start + sweep * minOf(fv, fd)
            val a1 = start + sweep * maxOf(fv, fd)
            if (a1 - a0 > 0.5f) drawArc(h.primary, a0, a1 - a0, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            val bodyR = r - sw * 3.2f
            drawCircle(Color.Black.copy(alpha = 0.4f), bodyR + 2f, Offset(c.x, c.y + 2f))
            drawCircle(
                Brush.verticalGradient(listOf(Color(0xFF34343E), Color(0xFF1A1A20)), startY = c.y - bodyR, endY = c.y + bodyR),
                bodyR, c
            )
            drawCircle(Color.White.copy(alpha = 0.06f), bodyR, c, style = Stroke(1.dp.toPx()))
            val ang = Math.toRadians((start + sweep * fv).toDouble())
            val p1 = Offset(c.x + (bodyR * 0.35f) * cos(ang).toFloat(), c.y + (bodyR * 0.35f) * sin(ang).toFloat())
            val p2 = Offset(c.x + (bodyR * 0.8f) * cos(ang).toFloat(), c.y + (bodyR * 0.8f) * sin(ang).toFloat())
            drawLine(h.primary, p1, p2, 3.dp.toPx(), cap = StrokeCap.Round)
        }
        Spacer(Modifier.height(6.dp))
        Txt(label, 12.sp, h.text, FontWeight.Medium)
        Txt(valueText, 11.sp, if (abs(value - def) > 0.01f) h.primary else h.dim)
    }
}
