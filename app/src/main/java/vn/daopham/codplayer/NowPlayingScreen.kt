package vn.daopham.codplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen() {
    val h = LocalHud.current
    val ctx = LocalContext.current
    val t = PlayerHolder.current
    if (t == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Emblem(96.dp)
            Spacer(Modifier.height(16.dp))
            HudText("Chưa có bài nào đang phát", 18.sp)
            Spacer(Modifier.height(16.dp))
            HudButton("Mở thư viện") { Ui.tab = Tab.LIBRARY }
        }
        return
    }
    val art by rememberArt(t)
    val tech by rememberTech(t)

    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(t.durationMs) }
    var dragging by remember { mutableStateOf(false) }
    var dragVal by remember { mutableFloatStateOf(0f) }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TechStrip(t, tech)
        VizStage(
            Ui.vizMode, art, t.title,
            Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp)
        ) {
            val m = VizMode.values()
            Ui.vizMode = m[(Ui.vizMode.ordinal + 1) % m.size]
            Prefs.saveUi()
        }
        HudText("◤ ${Ui.vizMode.label} · chạm để đổi ◢", 10.sp, h.dim)
        Spacer(Modifier.height(8.dp))
        Text(
            t.title, color = h.text, fontSize = 22.sp, fontFamily = Condensed, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Text(
            listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString("  •  ").ifBlank { t.fileName },
            color = h.accent, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
        Slider(
            value = if (dragging) dragVal else frac,
            onValueChange = { dragging = true; dragVal = it },
            onValueChangeFinished = { PlayerHolder.seek((dragVal * dur).toLong()); pos = (dragVal * dur).toLong(); dragging = false },
            colors = SliderDefaults.colors(
                thumbColor = h.accent, activeTrackColor = h.primary, inactiveTrackColor = h.dim.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(if (dragging) (dragVal * dur).toLong() else pos), color = h.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text("-" + fmtTime((dur - pos).coerceAtLeast(0)), color = h.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = { PlayerHolder.toggleShuffle(ctx) }) {
                Icon(Icons.Rounded.Shuffle, "Trộn", tint = if (PlayerHolder.shuffle) h.accent else h.dim, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = { PlayerHolder.prev(ctx) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Bài trước", tint = h.text, modifier = Modifier.size(38.dp))
            }
            Box(
                Modifier
                    .size(78.dp)
                    .clip(HexShape)
                    .background(Brush.linearGradient(listOf(h.primary, h.glow)))
                    .border(2.dp, h.accent, HexShape)
                    .clickable { PlayerHolder.toggle(ctx) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (PlayerHolder.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Phát/Dừng",
                    tint = Color.White, modifier = Modifier.size(44.dp)
                )
            }
            IconButton(onClick = { PlayerHolder.next(ctx) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Bài sau", tint = h.text, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = { PlayerHolder.cycleRepeat(ctx) }) {
                Icon(
                    if (PlayerHolder.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Lặp",
                    tint = if (PlayerHolder.repeat == Player.REPEAT_MODE_OFF) h.dim else h.accent, modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.hudPanel(h, 6.dp).clickable { Ui.tab = Tab.EQ }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Tune, null, tint = if (EqState.enabled) h.accent else h.dim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                HudText(if (EqState.enabled) "EQ: ${EqState.preset}" else "EQ: TẮT", 11.sp, if (EqState.enabled) h.text else h.dim, spacing = 1.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (PlayerHolder.sleepAt > 0) {
                var left by remember { mutableLongStateOf(0L) }
                LaunchedEffect(PlayerHolder.sleepAt) {
                    while (true) { left = PlayerHolder.sleepAt - System.currentTimeMillis(); delay(1000) }
                }
                Row(
                    Modifier.hudPanel(h, 6.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Bedtime, null, tint = h.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(fmtTime(left.coerceAtLeast(0)), color = h.text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${PlayerHolder.index + 1} / ${PlayerHolder.queue.size}",
                color = h.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TechStrip(t: Track, tech: Tech?) {
    val h = LocalHud.current
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).hudPanel(h, 8.dp, 0.7f).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FmtBadge(t.fmt)
        Spacer(Modifier.width(8.dp))
        val parts = mutableListOf<String>()
        if (tech != null) {
            if (tech.bits > 0 && (t.fmt == Fmt.FLAC || t.fmt == Fmt.WAV || tech.bits > 16)) parts += "${tech.bits}-bit"
            if (tech.sampleRate > 0) parts += (if (tech.sampleRate % 1000 == 0) "${tech.sampleRate / 1000}" else "%.1f".format(tech.sampleRate / 1000f)) + " kHz"
            if (tech.kbps > 0) parts += "${tech.kbps} kbps"
            when (tech.channels) { 1 -> parts += "Mono"; 2 -> parts += "Stereo"; 0 -> {}; else -> parts += "${tech.channels}ch" }
        }
        Text(
            if (parts.isEmpty()) "Đang đọc thông số…" else parts.joinToString("  ·  "),
            color = h.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (tech?.hiRes == true) {
            Box(
                Modifier.clip(hudCut(4.dp)).background(Color(0xFFFFC23D)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) { Text("HI-RES", color = Color.Black, fontSize = 9.sp, fontFamily = Condensed, fontWeight = FontWeight.Bold) }
        }
    }
}
