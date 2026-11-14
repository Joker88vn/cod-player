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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val h = LocalHud.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Group("Màu chủ đạo") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HudThemes.forEachIndexed { idx, th ->
                    val sel = Ui.theme == idx
                    Column(
                        Modifier.clickable { Ui.theme = idx; Prefs.saveUi() }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(th.primary, th.accent)))
                                .border(if (sel) 3.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sel) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(th.name, color = if (sel) h.text else h.dim, fontSize = 10.sp)
                    }
                }
            }
        }

        Group("Hiệu ứng hình ảnh") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VizMode.values().forEach { m -> PillChip(m.label, Ui.vizMode == m) { Ui.vizMode = m; Prefs.saveUi() } }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Đồng bộ hình với tiếng", 14.sp, h.text, FontWeight.Medium, Modifier.weight(1f))
                Text("${Ui.vizDelay} ms", color = h.primary, fontSize = 13.sp)
            }
            Text("Hình nhảy sớm hơn tiếng thì tăng lên, trễ hơn thì giảm xuống.", color = h.dim, fontSize = 12.sp)
            Slider(
                value = Ui.vizDelay.toFloat(), onValueChange = { Ui.vizDelay = it.toInt() }, valueRange = 0f..600f,
                onValueChangeFinished = { Prefs.saveUi() },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = h.primary, inactiveTrackColor = Color.White.copy(alpha = 0.12f))
            )
        }

        Group("Thư viện") {
            Txt("Bỏ qua file ngắn hơn", 14.sp, h.text, FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 10, 30, 60).forEach { s ->
                    PillChip(if (s == 0) "Không" else "$s giây", Ui.minDur == s) {
                        Ui.minDur = s; Prefs.saveUi(); scope.launch { Library.scan(ctx) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("Ẩn file ghi âm", 14.sp, h.text, FontWeight.Medium)
                    Txt("Bỏ các bản ghi âm, cuộc gọi khỏi thư viện", 12.sp, h.dim)
                }
                Switch(
                    Ui.hideRec, { Ui.hideRec = it; Prefs.saveUi(); scope.launch { Library.scan(ctx) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = h.primary, uncheckedTrackColor = h.surface2)
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(if (Library.loading) "Đang quét…" else "${Library.tracks.size} bài trong máy", 14.sp, h.text, FontWeight.Medium)
                    Txt("FLAC ${Library.tracks.count { it.fmt == Fmt.FLAC }} · WAV ${Library.tracks.count { it.fmt == Fmt.WAV }} · MP3 ${Library.tracks.count { it.fmt == Fmt.MP3 }}", 12.sp, h.dim)
                }
                PillButton("Quét lại", Icons.Rounded.Refresh, filled = false) { scope.launch { Library.scan(ctx) } }
            }
        }

        Group("Hẹn giờ tắt nhạc") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip("Tắt", PlayerHolder.sleepAt == 0L) { PlayerHolder.setSleep(0) }
                listOf(15, 30, 45, 60, 90, 120).forEach { m -> PillChip("$m phút", false) { PlayerHolder.setSleep(m) } }
            }
            if (PlayerHolder.sleepAt > 0) {
                Spacer(Modifier.height(8.dp))
                val left = ((PlayerHolder.sleepAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
                Text("Nhạc sẽ dừng sau khoảng $left phút", color = h.primary, fontSize = 12.sp)
            }
        }

        Group("Giới thiệu") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Emblem(48.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Txt("COD Player", 16.sp, h.text, FontWeight.SemiBold)
                    Txt("Phiên bản 1.2", 12.sp, h.dim)
                    Txt("MP3 · FLAC · WAV · AAC · OGG · EQ 10 băng", 12.sp, h.dim)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val h = LocalHud.current
    SectionLabel(title, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().card(h).padding(16.dp)) { content() }
}
