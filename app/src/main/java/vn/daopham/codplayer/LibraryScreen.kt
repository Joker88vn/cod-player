package vn.daopham.codplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private fun kbpsOf(t: Track): Int =
    if ((t.fmt == Fmt.FLAC || t.fmt == Fmt.WAV) && t.size > 0 && t.durationMs > 0) (t.size * 8 / t.durationMs).toInt()
    else t.bitrate / 1000

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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(Pill).background(h.surface2).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = h.dim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (Ui.query.isEmpty()) Text("Tìm bài hát, ca sĩ, album", color = h.dim, fontSize = 14.sp)
                    BasicTextField(
                        Ui.query, { Ui.query = it }, singleLine = true,
                        textStyle = TextStyle(color = h.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(h.primary), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (Ui.query.isNotEmpty()) {
                    Icon(Icons.Rounded.Close, "Xoá", tint = h.dim, modifier = Modifier.size(18.dp).clickable { Ui.query = "" })
                }
            }
            var sortOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortOpen = true }) { Icon(Icons.Rounded.FilterList, "Sắp xếp", tint = h.text) }
                DropdownMenu(sortOpen, { sortOpen = false }) {
                    SortBy.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, color = if (s == Ui.sort) h.primary else h.text, fontWeight = if (s == Ui.sort) FontWeight.SemiBold else FontWeight.Normal) },
                            onClick = { Ui.sort = s; Prefs.saveUi(); sortOpen = false }
                        )
                    }
                }
            }
            IconButton(onClick = { Ui.folderMode = !Ui.folderMode }) {
                Icon(if (Ui.folderMode) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, "Thư mục", tint = if (Ui.folderMode) h.primary else h.text)
            }
        }

        if (Ui.folderMode) {
            FolderList(all) { Ui.folder = it; Ui.folderMode = false }
            return@Column
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FmtFilter.values().filter { it == FmtFilter.ALL || (counts[it] ?: 0) > 0 || Ui.filter == it }.forEach { f ->
                PillChip(f.label, Ui.filter == f, count = "%,d".format(counts[f] ?: 0).replace(',', '.')) { Ui.filter = f }
            }
        }
        val folder = Ui.folder
        if (folder != null) {
            Row(
                Modifier.padding(start = 16.dp, top = 8.dp).clip(Pill).background(h.primary.copy(alpha = 0.14f))
                    .clickable { Ui.folder = null }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = h.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Txt(folder.substringAfterLast('/'), 12.sp, h.text, FontWeight.Medium, Modifier.weight(1f, false))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.Close, "Bỏ lọc", tint = h.dim, modifier = Modifier.size(16.dp))
            }
        }

        // Thông tin + nút phát
        val totalMs = remember(list) { list.sumOf { it.durationMs } }
        val totalSize = remember(list) { list.sumOf { it.size } }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Txt("${list.size} bài", 15.sp, h.text, FontWeight.SemiBold)
                Txt("${fmtTime(totalMs)}  ·  ${fmtSize(totalSize)}", 11.5.sp, h.dim)
            }
            if (cur != null) {
                IconButton(onClick = {
                    val idx = list.indexOfFirst { it.id == cur.id }
                    if (idx >= 0) scope.launch { state.animateScrollToItem((idx - 3).coerceAtLeast(0)) }
                }) { Icon(Icons.Rounded.MyLocation, "Tới bài đang phát", tint = h.dim, modifier = Modifier.size(20.dp)) }
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(h.surface2).clickable {
                    if (list.isNotEmpty()) PlayerHolder.playList(ctx, list, list.indices.random(), shuffleOn = true)
                },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Shuffle, "Trộn bài", tint = h.text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(8.dp))
            PillButton("Phát", Icons.Rounded.PlayArrow) {
                if (list.isNotEmpty()) PlayerHolder.playList(ctx, list, 0, shuffleOn = false)
            }
        }

        when {
            Library.loading && all.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = h.primary)
            }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (all.isEmpty()) "Chưa tìm thấy bài nhạc nào.\nChép nhạc MP3, FLAC, WAV vào máy rồi vào Cài đặt → Quét lại."
                    else "Không có bài nào khớp bộ lọc.",
                    color = h.dim, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
            else -> {
                val digits = list.size.toString().length.coerceAtLeast(2)
                LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                        TrackRow(i, t, digits, cur?.id == t.id) { PlayerHolder.playList(ctx, list, i) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(i: Int, t: Track, digits: Int, playing: Boolean, onClick: () -> Unit) {
    val h = LocalHud.current
    val thumb by rememberThumb(t)
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .background(if (playing) h.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width((digits * 8 + 10).dp), contentAlignment = Alignment.Center) {
            if (playing) MiniBars(Modifier.size(14.dp), h.primary)
            else Text((i + 1).toString(), color = h.dim.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))) { Cover(thumb, t.title, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(t.title, 15.sp, if (playing) h.primary else h.text, if (playing) FontWeight.SemiBold else FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Txt(t.subtitle, 12.5.sp, h.dim)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(fmtTime(t.durationMs), color = h.dim, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            FmtBadge(t.fmt)
        }
    }
}

@Composable
private fun FolderList(all: List<Track>, onPick: (String) -> Unit) {
    val h = LocalHud.current
    val groups = remember(all) {
        all.groupBy { it.folder }.toList().sortedWith { a, b -> viCollator.compare(a.first, b.first) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item { SectionLabel("${groups.size} thư mục", Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp)) }
        items(groups, key = { it.first }) { (name, tracks) ->
            val parts = buildList {
                add("${tracks.size} bài")
                add(fmtTime(tracks.sumOf { it.durationMs }))
                Fmt.values().forEach { f -> val n = tracks.count { it.fmt == f }; if (n > 0 && f != Fmt.OTHER) add("${f.label} $n") }
            }
            Row(
                Modifier.fillMaxWidth().clickable { onPick(name) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(h.surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, null, tint = h.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(name.substringAfterLast('/'), 15.sp, h.text, FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Txt(parts.joinToString("  ·  "), 12.sp, h.dim)
                    if (name.contains('/')) Txt(name, 10.5.sp, h.dim.copy(alpha = 0.6f))
                }
            }
        }
    }
}
