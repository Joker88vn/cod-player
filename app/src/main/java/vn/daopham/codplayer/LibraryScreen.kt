package vn.daopham.codplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

private val viCollator: Collator = Collator.getInstance(Locale("vi", "VN")).apply { strength = Collator.PRIMARY }

private fun sorted(list: List<Track>, by: SortBy): List<Track> = when (by) {
    SortBy.TITLE -> list.sortedWith { a, b -> viCollator.compare(a.title, b.title) }
    SortBy.ARTIST -> list.sortedWith { a, b ->
        val c = viCollator.compare(a.artist.ifBlank { "￿" }, b.artist.ifBlank { "￿" })
        if (c != 0) c else viCollator.compare(a.title, b.title)
    }
    SortBy.FILE -> list.sortedWith { a, b -> viCollator.compare(a.fileName, b.fileName) }
    SortBy.FOLDER -> list.sortedWith { a, b ->
        val c = viCollator.compare(a.folder, b.folder)
        if (c != 0) c else viCollator.compare(a.fileName, b.fileName)
    }
    SortBy.DURATION -> list.sortedByDescending { it.durationMs }
    SortBy.DATE -> list.sortedByDescending { it.dateAdded }
}

@Composable
fun LibraryScreen() {
    val h = LocalHud.current
    val ctx = LocalContext.current
    val all = Library.tracks
    val q = Ui.query.trim()
    val base = remember(all, Ui.folder, q) {
        all.filter { t ->
            (Ui.folder == null || t.folder == Ui.folder) &&
                (q.isEmpty() || t.title.contains(q, true) || t.artist.contains(q, true) ||
                    t.album.contains(q, true) || t.fileName.contains(q, true))
        }
    }
    val counts = remember(base) { FmtFilter.values().associateWith { f -> base.count { f.match(it) } } }
    val list = remember(base, Ui.filter, Ui.sort) { sorted(base.filter { Ui.filter.match(it) }, Ui.sort) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val cur = PlayerHolder.current

    Column(Modifier.fillMaxSize()) {
        // Thanh tìm kiếm + sắp xếp + thư mục
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(42.dp).hudPanel(h, 10.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = h.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (Ui.query.isEmpty()) Text("Tìm bài, ca sĩ, album…", color = h.dim, fontSize = 14.sp)
                    BasicTextField(
                        Ui.query, { Ui.query = it }, singleLine = true,
                        textStyle = TextStyle(color = h.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(h.primary), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (Ui.query.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, null, tint = h.dim, modifier = Modifier.size(18.dp).clickable { Ui.query = "" })
                }
            }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Rounded.FilterList, "Sắp xếp", tint = h.text) }
                DropdownMenu(sortOpen, { sortOpen = false }) {
                    SortBy.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text((if (s == Ui.sort) "▸ " else "   ") + s.label, color = if (s == Ui.sort) h.accent else h.text) },
                            onClick = { Ui.sort = s; Prefs.saveUi(); sortOpen = false }
                        )
                    }
                }
            }
            IconButton(onClick = { Ui.folderMode = !Ui.folderMode }) {
                Icon(if (Ui.folderMode) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, "Thư mục", tint = if (Ui.folderMode) h.accent else h.text)
            }
        }

        if (Ui.folderMode) {
            FolderList(all) { Ui.folder = it; Ui.folderMode = false }
            return@Column
        }

        // Tab định dạng
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FmtFilter.values().forEach { f ->
                HudChip("${f.label} ${counts[f] ?: 0}", Ui.filter == f) { Ui.filter = f }
            }
        }
        val folder = Ui.folder
        if (folder != null) {
            Row(
                Modifier.padding(start = 12.dp, top = 6.dp).hudPanel(h, 6.dp).clickable { Ui.folder = null }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = h.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(folder, color = h.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Close, null, tint = h.dim, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(6.dp))

        // Khung danh sách kiểu Winamp
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).hudPanel(h, 14.dp, 0.6f)) {
            Row(
                Modifier.fillMaxWidth().background(h.primary.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudText("◢ DANH SÁCH PHÁT", 11.sp, h.accent)
                Spacer(Modifier.weight(1f))
                HudText(Ui.sort.label, 10.sp, h.dim)
            }
            when {
                Library.loading && all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = h.primary)
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "Chưa tìm thấy bài nhạc nào trong máy.\nChép nhạc (MP3, FLAC, WAV…) vào máy rồi bấm Quét lại trong Cài đặt."
                        else "Không có bài nào khớp bộ lọc.",
                        color = h.dim, fontSize = 13.sp
                    )
                }
                else -> {
                    val digits = list.size.toString().length.coerceAtLeast(3)
                    LazyColumn(Modifier.weight(1f), state = state) {
                        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                            TrackRow(i, t, digits, cur?.id == t.id) {
                                PlayerHolder.playList(ctx, list, i)
                            }
                        }
                    }
                }
            }
        }

        // Thanh tổng kết
        val totalMs = remember(list) { list.sumOf { it.durationMs } }
        val totalSize = remember(list) { list.sumOf { it.size } }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${list.size} bài • ${fmtTime(totalMs)}", color = h.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(fmtSize(totalSize), color = h.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            if (cur != null) {
                IconButton(onClick = {
                    val idx = list.indexOfFirst { it.id == cur.id }
                    if (idx >= 0) scope.launch { state.animateScrollToItem((idx - 3).coerceAtLeast(0)) }
                }) { Icon(Icons.Rounded.MyLocation, "Tới bài đang phát", tint = h.accent) }
            }
            HudButton("Trộn", Icons.Rounded.Shuffle, filled = false) {
                if (list.isNotEmpty()) PlayerHolder.playList(ctx, list, list.indices.random(), shuffleOn = true)
            }
            Spacer(Modifier.width(8.dp))
            HudButton("Phát", Icons.Rounded.PlayArrow) {
                if (list.isNotEmpty()) PlayerHolder.playList(ctx, list, 0, shuffleOn = false)
            }
        }
    }
}

@Composable
private fun TrackRow(i: Int, t: Track, digits: Int, playing: Boolean, onClick: () -> Unit) {
    val h = LocalHud.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                when {
                    playing -> h.primary.copy(alpha = 0.2f)
                    i % 2 == 0 -> Color.White.copy(alpha = 0.025f)
                    else -> Color.Transparent
                }
            )
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (playing) h.primary else Color.Transparent))
        Spacer(Modifier.width(7.dp))
        Text(
            (i + 1).toString().padStart(digits, '0') + ".",
            color = if (playing) h.accent else h.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.display, color = if (playing) h.accent else h.text, fontSize = 14.sp,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = buildString {
                if (t.album.isNotBlank()) append(t.album)
                val kb = if ((t.fmt == Fmt.FLAC || t.fmt == Fmt.WAV) && t.size > 0 && t.durationMs > 0) (t.size * 8 / t.durationMs).toInt()
                else t.bitrate / 1000
                if (kb > 0) { if (isNotEmpty()) append("  •  "); append("$kb kbps") }
            }
            if (sub.isNotEmpty()) Text(sub, color = h.dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        FmtBadge(t.fmt)
        Spacer(Modifier.width(8.dp))
        Text(fmtTime(t.durationMs), color = if (playing) h.accent else h.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun FolderList(all: List<Track>, onPick: (String) -> Unit) {
    val h = LocalHud.current
    val groups = remember(all) {
        all.groupBy { it.folder }.toList().sortedWith { a, b -> viCollator.compare(a.first, b.first) }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        items(groups, key = { it.first }) { (name, tracks) ->
            val fl = tracks.count { it.fmt == Fmt.FLAC }
            val wv = tracks.count { it.fmt == Fmt.WAV }
            val mp = tracks.count { it.fmt == Fmt.MP3 }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).hudPanel(h, 8.dp, 0.6f).clickable { onPick(name) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Folder, null, tint = h.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name.substringAfterLast('/'), color = h.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${tracks.size} bài • ${fmtTime(tracks.sumOf { it.durationMs })}" +
                            (if (fl > 0) " • FLAC $fl" else "") + (if (wv > 0) " • WAV $wv" else "") + (if (mp > 0) " • MP3 $mp" else ""),
                        color = h.dim, fontSize = 11.sp, maxLines = 1
                    )
                    if (name.contains('/')) Text(name, color = h.dim.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
