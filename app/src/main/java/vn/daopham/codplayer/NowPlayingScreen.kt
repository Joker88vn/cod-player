package vn.daopham.codplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(art: ImageBitmap?, accent: Color) {
    val h = LocalHud.current
    val ctx = LocalContext.current
    val t = PlayerHolder.current
    if (t == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Emblem(96.dp)
            Spacer(Modifier.height(20.dp))
            Text("Chưa có bài nào đang phát", color = h.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Chọn một bài trong thư viện để bắt đầu", color = h.dim, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            PillButton("Mở thư viện") { Ui.tab = Tab.LIBRARY }
        }
        return
    }
    val tech by rememberTech(t)
    val accent2 = remember(accent) { lerpColor(accent, Color.White, 0.35f) }

    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(t.durationMs) }
    LaunchedEffect(t.id) {
        while (true) {
            val p = PlayerHolder.exoOrNull
            if (p != null) {
                pos = p.currentPosition
                val d = p.duration
                dur = if (d > 0) d else t.durationMs
            }
            delay(200)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 22.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Đang phát")
                Txt(
                    if (Ui.folder != null) Ui.folder!!.substringAfterLast('/') else "Bài ${PlayerHolder.index + 1} / ${PlayerHolder.queue.size}",
                    12.sp, h.text.copy(alpha = 0.8f)
                )
            }
            var vizOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { vizOpen = true }) { Icon(Icons.Rounded.AutoAwesome, "Hiệu ứng", tint = h.text) }
                DropdownMenu(vizOpen, { vizOpen = false }) {
                    VizMode.values().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label, color = if (m == Ui.vizMode) accent else h.text) },
                            onClick = { Ui.vizMode = m; Prefs.saveUi(); vizOpen = false }
                        )
                    }
                }
            }
        }

        VizStage(Ui.vizMode, art, accent, accent2, Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp)) {
            val m = VizMode.values()
            Ui.vizMode = m[(Ui.vizMode.ordinal + 1) % m.size]
            Prefs.saveUi()
        }

        Spacer(Modifier.height(10.dp))
        Text(
            t.title, color = h.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(2.dp))
        Text(
            listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { t.fileName },
            color = h.text.copy(alpha = 0.65f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        TechLine(t, tech)
        Spacer(Modifier.height(14.dp))

        SeekBar(if (dur > 0) pos.toFloat() / dur else 0f, accent) { f ->
            val ms = (f * dur).toLong(); PlayerHolder.seek(ms); pos = ms
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(fmtTime(pos), color = h.text.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(dur), color = h.text.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { PlayerHolder.toggleShuffle(ctx) }) {
                Icon(Icons.Rounded.Shuffle, "Trộn bài", tint = if (PlayerHolder.shuffle) accent else h.text.copy(alpha = 0.55f))
            }
            IconButton(onClick = { PlayerHolder.prev(ctx) }, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Bài trước", tint = h.text, modifier = Modifier.size(40.dp))
            }
            Box(
                Modifier.size(74.dp).shadow(16.dp, CircleShape, ambientColor = accent, spotColor = accent)
                    .clip(CircleShape).background(accent).clickable { PlayerHolder.toggle(ctx) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (PlayerHolder.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Phát/Dừng",
                    tint = if (accent.luminance() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { PlayerHolder.next(ctx) }, modifier = Modifier.size(60.dp)) {
                Icon(Icons.Rounded.SkipNext, "Bài sau", tint = h.text, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { PlayerHolder.cycleRepeat(ctx) }) {
                Icon(
                    if (PlayerHolder.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Lặp lại",
                    tint = if (PlayerHolder.repeat == Player.REPEAT_MODE_OFF) h.text.copy(alpha = 0.55f) else accent
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(Pill).background(Color.White.copy(alpha = 0.08f)).clickable { Ui.tab = Tab.EQ }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Tune, null, tint = if (EqState.enabled) accent else h.dim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Txt(if (EqState.enabled) EqState.preset else "EQ tắt", 12.sp, h.text, FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            if (PlayerHolder.sleepAt > 0) {
                var left by remember { mutableLongStateOf(0L) }
                LaunchedEffect(PlayerHolder.sleepAt) {
                    while (true) { left = PlayerHolder.sleepAt - System.currentTimeMillis(); delay(1000) }
                }
                Row(
                    Modifier.clip(Pill).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Bedtime, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(fmtTime(left.coerceAtLeast(0)), color = h.text, fontSize = 12.sp)
                }
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)

@Composable
private fun TechLine(t: Track, tech: Tech?) {
    val h = LocalHud.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FmtBadge(t.fmt)
        Spacer(Modifier.width(8.dp))
        val parts = mutableListOf<String>()
        if (tech != null) {
            if (tech.bits > 0 && (t.fmt == Fmt.FLAC || t.fmt == Fmt.WAV || tech.bits > 16)) parts += "${tech.bits} bit"
            if (tech.sampleRate > 0) parts += (if (tech.sampleRate % 1000 == 0) "${tech.sampleRate / 1000}" else "%.1f".format(tech.sampleRate / 1000f)) + " kHz"
            if (tech.kbps > 0) parts += "${tech.kbps} kbps"
            when (tech.channels) { 1 -> parts += "Mono"; 2 -> parts += "Stereo"; 0 -> {}; else -> parts += "${tech.channels} kênh" }
        }
        Txt(parts.joinToString("  ·  "), 12.sp, h.text.copy(alpha = 0.6f), modifier = Modifier.weight(1f, false))
        if (tech?.hiRes == true) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFE8B84A)).padding(horizontal = 5.dp, vertical = 1.dp)
            ) { Text("Hi-Res", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SeekBar(frac: Float, accent: Color, onSeek: (Float) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragVal by remember { mutableFloatStateOf(0f) }
    val cb by rememberUpdatedState(onSeek)
    val v = (if (dragging) dragVal else frac).coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) { detectTapGestures { o -> cb((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragVal = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { cb(dragVal); dragging = false },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    dragVal = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        val cy = size.height / 2
        val trackH = 4.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.16f), Offset(0f, cy - trackH / 2), Size(size.width, trackH), CornerRadius(trackH / 2))
        drawRoundRect(accent, Offset(0f, cy - trackH / 2), Size(size.width * v, trackH), CornerRadius(trackH / 2))
        val r = (if (dragging) 9.dp else 7.dp).toPx()
        drawCircle(Color.White, r, Offset(size.width * v, cy))
    }
}
