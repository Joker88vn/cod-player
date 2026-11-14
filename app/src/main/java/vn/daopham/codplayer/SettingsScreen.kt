package vn.daopham.codplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val h = LocalHud.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Section("Giao diện") {
            HudThemes.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { th ->
                        val idx = HudThemes.indexOf(th)
                        val sel = Ui.theme == idx
                        Row(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
                                .clip(hudCut(10.dp))
                                .background(Brush.horizontalGradient(listOf(th.panel, th.bg)))
                                .border(if (sel) 2.dp else 1.dp, if (sel) th.accent else th.primary.copy(alpha = 0.4f), hudCut(10.dp))
                                .clickable { Ui.theme = idx; Prefs.saveUi() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(22.dp).clip(HexShape).background(th.primary))
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.size(14.dp).clip(HexShape).background(th.accent))
                            Spacer(Modifier.width(8.dp))
                            Text(th.name, color = th.text, fontSize = 14.sp, fontFamily = Condensed)
                            if (sel) { Spacer(Modifier.weight(1f)); Text("✓", color = th.accent, fontSize = 14.sp) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Section("Hiệu ứng hình ảnh") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VizMode.values().forEach { m -> HudChip(m.label, Ui.vizMode == m) { Ui.vizMode = m; Prefs.saveUi() } }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudText("Đồng bộ hiệu ứng với nhạc", 13.sp, modifier = Modifier.weight(1f))
                Text("${Ui.vizDelay} ms", color = h.accent, fontSize = 12.sp)
            }
            Text("Nếu hình nhảy sớm hơn tiếng thì tăng lên, trễ hơn thì giảm xuống.", color = h.dim, fontSize = 11.sp)
            Slider(
                value = Ui.vizDelay.toFloat(), onValueChange = { Ui.vizDelay = it.toInt() }, valueRange = 0f..600f,
                onValueChangeFinished = { Prefs.saveUi() },
                colors = SliderDefaults.colors(thumbColor = h.accent, activeTrackColor = h.primary, inactiveTrackColor = h.dim.copy(alpha = 0.25f))
            )
        }

        Section("Thư viện nhạc") {
            Text("Bỏ qua file ngắn hơn:", color = h.text, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 10, 30, 60).forEach { s ->
                    HudChip(if (s == 0) "Không" else "${s}s", Ui.minDur == s) {
                        Ui.minDur = s; Prefs.saveUi(); scope.launch { Library.scan(ctx) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (Library.loading) "Đang quét…" else "${Library.tracks.size} bài trong máy",
                    color = h.dim, fontSize = 12.sp, modifier = Modifier.weight(1f)
                )
                HudButton("Quét lại", Icons.Rounded.Refresh) { scope.launch { Library.scan(ctx) } }
            }
        }

        Section("Hẹn giờ tắt nhạc") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HudChip("Tắt", PlayerHolder.sleepAt == 0L) { PlayerHolder.setSleep(0) }
                listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                    HudChip("$m phút", false) { PlayerHolder.setSleep(m) }
                }
            }
            if (PlayerHolder.sleepAt > 0) {
                Spacer(Modifier.height(6.dp))
                val left = ((PlayerHolder.sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text("Nhạc sẽ dừng sau khoảng $left phút", color = h.accent, fontSize = 12.sp)
            }
        }

        Section("Giới thiệu") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Emblem(44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    HudText("COD Player 1.0", 16.sp, italic = true)
                    Text("Phát MP3 · FLAC · WAV · AAC · OGG\nEQ 10 băng · Bass/Treble · Stereo · Limiter", color = h.dim, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val h = LocalHud.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).hudPanel(h, 12.dp, 0.6f).padding(12.dp)) {
        HudText("◢ $title", 13.sp, h.accent)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
