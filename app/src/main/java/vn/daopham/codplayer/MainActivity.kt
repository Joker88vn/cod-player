package vn.daopham.codplayer

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        Prefs.init(this)
        PlayerHolder.player(this)
        setContent { CodTheme { Root() } }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        try {
            controllerFuture = MediaController.Builder(
                this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
            ).buildAsync()
        } catch (_: Exception) {}
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_VIEW) return
        val u = i.data ?: return
        val name = queryName(u) ?: u.lastPathSegment?.substringAfterLast('/') ?: "Không tên"
        val t = Track(
            id = -System.currentTimeMillis(), uri = u, title = name.substringBeforeLast('.'), artist = "", album = "",
            albumId = 0, durationMs = 0, fileName = name, folder = "", ext = name.substringAfterLast('.', "").lowercase(),
            size = 0, bitrate = 0, dateAdded = 0
        )
        PlayerHolder.playList(this, listOf(t), 0)
        Ui.tab = Tab.PLAYING
    }

    private fun queryName(u: Uri): String? = try {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }
}

private fun audioPerm(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPerm(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, audioPerm()) == PackageManager.PERMISSION_GRANTED

private fun permsToAsk(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
fun Root() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPerm(ctx)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPerm(ctx); asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(permsToAsk()) }
    LaunchedEffect(granted) {
        if (granted && !Library.scanned) {
            Library.scan(ctx)
            PlayerHolder.restore(ctx)
        }
    }
    LaunchedEffect(PlayerHolder.errorTick) {
        if (PlayerHolder.errorTick > 0) Toast.makeText(ctx, PlayerHolder.errorText, Toast.LENGTH_SHORT).show()
    }
    BackHandler(enabled = granted && Ui.tab != Tab.LIBRARY) { Ui.tab = Tab.LIBRARY }

    HudBackground {
        if (!granted) {
            PermissionScreen(asked) {
                if (asked) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else launcher.launch(permsToAsk())
            }
            // Khi người dùng quay lại từ màn hình Cài đặt
            LaunchedEffect(Unit) { while (!granted) { delay(1000); granted = hasAudioPerm(ctx) } }
        } else {
            Column(Modifier.fillMaxSize()) {
                TopBar()
                Box(Modifier.weight(1f)) {
                    when (Ui.tab) {
                        Tab.LIBRARY -> LibraryScreen()
                        Tab.PLAYING -> NowPlayingScreen()
                        Tab.EQ -> EqScreen()
                        Tab.SETTINGS -> SettingsScreen()
                    }
                }
                if (Ui.tab != Tab.PLAYING && PlayerHolder.current != null) MiniPlayer()
                BottomNav()
            }
        }
    }
}

@Composable
private fun TopBar() {
    val h = LocalHud.current
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Emblem(30.dp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.clip(SlantShape).background(h.primary).padding(horizontal = 12.dp, vertical = 1.dp)) {
                HudText("COD", 22.sp, Color.White, spacing = 2.sp, italic = true)
            }
            Spacer(Modifier.width(6.dp))
            HudText("PLAYER", 22.sp, h.text, spacing = 4.sp, italic = true)
            Spacer(Modifier.weight(1f))
            HudText(Ui.tab.title, 11.sp, h.dim, spacing = 2.sp)
        }
        Box(
            Modifier.fillMaxWidth().height(1.5.dp)
                .background(Brush.horizontalGradient(listOf(h.primary, h.accent.copy(alpha = 0.5f), Color.Transparent)))
        )
    }
}

@Composable
private fun BottomNav() {
    val h = LocalHud.current
    Row(
        Modifier.fillMaxWidth().background(h.bg.copy(alpha = 0.92f)).navigationBarsPadding().height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { tab ->
            val sel = Ui.tab == tab
            Column(
                Modifier.weight(1f).fillMaxSize().clickable { Ui.tab = tab },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.width(36.dp).height(3.dp)
                        .background(if (sel) h.primary else Color.Transparent, SlantShape)
                )
                Spacer(Modifier.height(7.dp))
                Icon(tab.icon, tab.title, tint = if (sel) h.primary else h.dim, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(2.dp))
                HudText(tab.title, 10.sp, if (sel) h.accent else h.dim, spacing = 1.sp)
            }
        }
    }
}

@Composable
private fun MiniPlayer() {
    val h = LocalHud.current
    val ctx = LocalContext.current
    val t = PlayerHolder.current ?: return
    val art by rememberArt(t)
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t.id) {
        while (true) {
            val p = PlayerHolder.exoOrNull
            if (p != null && p.duration > 0) frac = (p.currentPosition.toFloat() / p.duration).coerceIn(0f, 1f)
            delay(500)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).hudPanel(h, 10.dp, 0.95f)
            .clickable { Ui.tab = Tab.PLAYING }
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(h.dim.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(Brush.horizontalGradient(listOf(h.primary, h.accent))))
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(hudCut(8.dp))) { ArtOrEmblem(art, t.title, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, color = h.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FmtBadge(t.fmt)
                    Spacer(Modifier.width(6.dp))
                    Text(t.artist.ifBlank { t.fileName }, color = h.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            MiniBars(Modifier.width(36.dp).height(26.dp))
            IconButton(onClick = { PlayerHolder.toggle(ctx) }) {
                Icon(
                    if (PlayerHolder.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Phát/Dừng",
                    tint = h.primary, modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { PlayerHolder.next(ctx) }) {
                Icon(Icons.Rounded.SkipNext, "Bài sau", tint = h.text, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun PermissionScreen(denied: Boolean, onAsk: () -> Unit) {
    val h = LocalHud.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Emblem(120.dp)
        Spacer(Modifier.height(20.dp))
        Row {
            Box(Modifier.clip(SlantShape).background(h.primary).padding(horizontal = 14.dp)) {
                HudText("COD", 34.sp, Color.White, spacing = 3.sp, italic = true)
            }
            Spacer(Modifier.width(8.dp))
            HudText("PLAYER", 34.sp, h.text, spacing = 5.sp, italic = true)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (denied) "Anh cần mở Cài đặt → Quyền → cho phép \"Nhạc và âm thanh\" để app đọc được nhạc trong máy."
            else "Cho phép app đọc nhạc trong máy để hiện danh sách MP3, FLAC, WAV.",
            color = h.dim, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        HudButton(if (denied) "Mở cài đặt" else "Cấp quyền", onClick = onAsk)
    }
}
