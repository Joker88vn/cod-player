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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val h = LocalHud.current
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

    val cur = PlayerHolder.current
    val art by rememberArt(cur)
    val accent by rememberAccent(art, if (cur != null) coverColors(cur.title).first else h.primary)

    Box(Modifier.fillMaxSize().background(h.bg)) {
        if (!granted) {
            PermissionScreen(asked) {
                if (asked) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else launcher.launch(permsToAsk())
            }
            LaunchedEffect(Unit) { while (!granted) { delay(1000); granted = hasAudioPerm(ctx) } }
            return@Box
        }
        val playing = Ui.tab == Tab.PLAYING
        if (playing) BlurBackdrop(art, accent, cur?.title ?: "")
        Column(Modifier.fillMaxSize()) {
            if (!playing) TopBar()
            Box(Modifier.weight(1f)) {
                when (Ui.tab) {
                    Tab.LIBRARY -> LibraryScreen()
                    Tab.PLAYING -> NowPlayingScreen(art, accent)
                    Tab.EQ -> EqScreen()
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
            if (!playing && cur != null) MiniPlayer(cur, art, accent)
            BottomNav(playing)
        }
    }
}

@Composable
private fun TopBar() {
    val h = LocalHud.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(Ui.tab.title, color = h.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Emblem(30.dp)
    }
}

@Composable
private fun BottomNav(overArt: Boolean) {
    val h = LocalHud.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (overArt) Color.Transparent else h.surface)
            .navigationBarsPadding()
            .height(66.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { tab ->
            val sel = Ui.tab == tab
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { Ui.tab = tab },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.width(56.dp).height(30.dp).clip(Pill)
                        .background(if (sel) h.primary.copy(alpha = 0.18f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tab.icon, tab.title, tint = if (sel) h.primary else h.dim, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(3.dp))
                Text(tab.title, color = if (sel) h.text else h.dim, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun MiniPlayer(t: Track, art: ImageBitmap?, accent: Color) {
    val h = LocalHud.current
    val ctx = LocalContext.current
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(t.id) {
        while (true) {
            val p = PlayerHolder.exoOrNull
            if (p != null && p.duration > 0) frac = (p.currentPosition.toFloat() / p.duration).coerceIn(0f, 1f)
            delay(500)
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .card(h, 16.dp, h.surface2)
            .clickable { Ui.tab = Tab.PLAYING }
    ) {
        Row(Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))) { Cover(art, t.title, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(t.title, 14.sp, h.text, FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FmtBadge(t.fmt)
                    Spacer(Modifier.width(6.dp))
                    Txt(t.subtitle, 12.sp, h.dim)
                }
            }
            IconButton(onClick = { PlayerHolder.toggle(ctx) }) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                    Icon(
                        if (PlayerHolder.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Phát/Dừng",
                        tint = if (accent.luminance() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(24.dp)
                    )
                }
            }
            IconButton(onClick = { PlayerHolder.next(ctx) }) {
                Icon(Icons.Rounded.SkipNext, "Bài sau", tint = h.text, modifier = Modifier.size(28.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(2.dp).clip(Pill).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(accent))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PermissionScreen(denied: Boolean, onAsk: () -> Unit) {
    val h = LocalHud.current
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(h.primary.copy(alpha = 0.16f), h.bg, h.bg))
        ).padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Emblem(112.dp)
        Spacer(Modifier.height(24.dp))
        Text("COD Player", color = h.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            if (denied) "Vào Cài đặt → Quyền → cho phép \"Nhạc và âm thanh\" để app đọc được nhạc trong máy."
            else "Cho phép đọc nhạc trong máy để hiện danh sách MP3, FLAC, WAV.",
            color = h.dim, fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        PillButton(if (denied) "Mở cài đặt" else "Cho phép", onClick = onAsk)
    }
}
