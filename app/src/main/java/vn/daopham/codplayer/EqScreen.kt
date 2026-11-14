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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private fun fmtDb(v: Float): String = when {
    abs(v) < 0.05f -> "0"
    v > 0 -> "+%.1f".format(v)
    else -> "%.1f".format(v)
}

@Composable
fun EqScreen() {
    val h = LocalHud.current
    var saveOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HudText("Bộ chỉnh âm 10 băng", 18.sp, italic = true)
                Text("Đang dùng: ${EqState.preset}", color = h.accent, fontSize = 12.sp)
            }
            HudText(if (EqState.enabled) "BẬT" else "TẮT", 12.sp, if (EqState.enabled) h.accent else h.dim)
            Spacer(Modifier.width(8.dp))
            Switch(
                EqState.enabled, { EqState.enabled = it; EqState.push() },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = h.primary, uncheckedTrackColor = h.panel)
            )
        }
        Spacer(Modifier.height(8.dp))

        // Preset
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (BuiltInPresets + EqState.custom).forEach { p ->
                HudChip(p.name, EqState.preset == p.name) { EqState.apply(p) }
            }
        }
        Spacer(Modifier.height(10.dp))

        val dim = if (EqState.enabled) 1f else 0.45f
        Column(Modifier.alpha(dim)) {
            // Đường cong đáp tuyến
            ResponseCurve(Modifier.fillMaxWidth().height(110.dp).hudPanel(h, 10.dp, 0.6f))
            Spacer(Modifier.height(10.dp))

            // 10 thanh trượt dọc
            Row(Modifier.fillMaxWidth().hudPanel(h, 12.dp, 0.6f).padding(vertical = 10.dp, horizontal = 4.dp)) {
                for (i in 0 until 10) {
                    VSlider(
                        value = EqState.gains[i],
                        label = EQ_LABELS[i],
                        modifier = Modifier.weight(1f)
                    ) { v -> EqState.gains[i] = v; EqState.preset = "Tuỳ chỉnh"; EqState.push() }
                }
            }
            Spacer(Modifier.height(10.dp))

            Column(Modifier.fillMaxWidth().hudPanel(h, 12.dp, 0.6f).padding(12.dp)) {
                KnobRow("Preamp", fmtDb(EqState.preamp) + " dB", EqState.preamp, -12f..12f) { EqState.preamp = it; EqState.push() }
                KnobRow("Bass", fmtDb(EqState.bass) + " dB", EqState.bass, -12f..12f) { EqState.bass = it; EqState.preset = "Tuỳ chỉnh"; EqState.push() }
                KnobRow("Treble", fmtDb(EqState.treble) + " dB", EqState.treble, -12f..12f) { EqState.treble = it; EqState.preset = "Tuỳ chỉnh"; EqState.push() }
                KnobRow("Độ rộng stereo", "${(EqState.width * 100).roundToInt()}%", EqState.width, 0f..2f) { EqState.width = it; EqState.push() }
                val b = EqState.balance
                KnobRow(
                    "Cân bằng trái/phải",
                    if (abs(b) < 0.02f) "Giữa" else if (b < 0) "T ${(-b * 100).roundToInt()}%" else "P ${(b * 100).roundToInt()}%",
                    b, -1f..1f
                ) { EqState.balance = if (abs(it) < 0.04f) 0f else it; EqState.push() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HudText("Chống vỡ tiếng (Limiter)", 13.sp)
                        Text("Giữ tiếng không bị rè khi tăng EQ nhiều", color = h.dim, fontSize = 11.sp)
                    }
                    Switch(
                        EqState.limiter, { EqState.limiter = it; EqState.push() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = h.primary, uncheckedTrackColor = h.panel)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudButton("Đặt lại", Icons.Rounded.RestartAlt, filled = false) { EqState.reset() }
            HudButton("Lưu preset", Icons.Rounded.Save) { saveOpen = true }
            val custom = EqState.custom.firstOrNull { it.name == EqState.preset }
            if (custom != null) {
                HudButton("Xoá", Icons.Rounded.Delete, filled = false) {
                    EqState.custom.remove(custom); EqState.preset = "Tuỳ chỉnh"; Prefs.saveEq()
                }
            }
        }
        Spacer(Modifier.height(16.dp))
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
            containerColor = h.panel
        )
    }
}

@Composable
private fun KnobRow(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    val h = LocalHud.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HudText(label, 13.sp, modifier = Modifier.weight(1f))
            Text(value, color = h.accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = v, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = h.accent, activeTrackColor = h.primary, inactiveTrackColor = h.dim.copy(alpha = 0.25f)),
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun VSlider(value: Float, label: String, modifier: Modifier, onChange: (Float) -> Unit) {
    val h = LocalHud.current
    val cb by rememberUpdatedState(onChange)
    fun fromY(y: Float, height: Float): Float {
        val v = ((1f - y / height) * 24f - 12f).coerceIn(-12f, 12f)
        return (v * 2f).roundToInt() / 2f
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmtDb(value), color = if (abs(value) > 0.05f) h.accent else h.dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(Unit) { detectTapGestures { o -> cb(fromY(o.y, size.height.toFloat())) } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        cb(fromY(change.position.y, size.height.toFloat()))
                    }
                }
        ) {
            val cx = size.width / 2
            val hh = size.height
            fun yOf(v: Float) = (1f - (v + 12f) / 24f) * hh
            for (k in -12..12 step 3) {
                val y = yOf(k.toFloat())
                drawLine(h.dim.copy(alpha = if (k == 0) 0.5f else 0.18f), Offset(cx - 7f, y), Offset(cx + 7f, y), 1.5f)
            }
            drawLine(h.dim.copy(alpha = 0.3f), Offset(cx, 0f), Offset(cx, hh), 4f)
            val y0 = yOf(0f)
            val yv = yOf(value)
            drawLine(
                Brush.verticalGradient(listOf(h.accent, h.primary), startY = 0f, endY = hh),
                Offset(cx, y0), Offset(cx, yv), 6f
            )
            val tw = size.width * 0.62f
            drawRect(h.glow.copy(alpha = 0.35f), Offset(cx - tw / 2 - 3f, yv - 9f), Size(tw + 6f, 18f))
            drawRect(h.accent, Offset(cx - tw / 2, yv - 6f), Size(tw, 12f))
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(cx - tw / 3, yv), Offset(cx + tw / 3, yv), 2f)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = h.text, fontSize = 10.sp, fontFamily = Condensed)
    }
}

@Composable
private fun ResponseCurve(modifier: Modifier) {
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
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w = size.width
            val hh = size.height
            val range = 18f
            fun yOf(db: Float) = hh / 2 - (db / range) * (hh / 2)
            for (f in EQ_FREQS) {
                val x = (kotlin.math.log10(f / 20f) / 3f) * w
                drawLine(h.dim.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, hh), 1f)
            }
            drawLine(h.dim.copy(alpha = 0.35f), Offset(0f, hh / 2), Offset(w, hh / 2), 1f)
            val path = Path()
            val fill = Path()
            val pts = 120
            for (i in 0..pts) {
                val f = 20.0 * 10.0.pow(3.0 * i / pts)
                var db = pre.toDouble()
                for (b in filters) db += b.magDb(f, 48000.0)
                val x = w * i / pts
                val y = yOf(db.toFloat().coerceIn(-range, range))
                if (i == 0) { path.moveTo(x, y); fill.moveTo(x, hh / 2); fill.lineTo(x, y) } else { path.lineTo(x, y); fill.lineTo(x, y) }
            }
            fill.lineTo(w, hh / 2); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(h.primary.copy(alpha = 0.35f), h.primary.copy(alpha = 0.02f))))
            drawPath(path, h.glow.copy(alpha = 0.4f), style = Stroke(7f))
            drawPath(path, h.accent, style = Stroke(2.5f))
        }
        Text("+18", color = h.dim, fontSize = 9.sp, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
        Text("-18", color = h.dim, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp))
    }
}
