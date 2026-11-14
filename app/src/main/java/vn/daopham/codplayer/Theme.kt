package vn.daopham.codplayer

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Hud(
    val name: String,
    val primary: Color,
    val accent: Color,
    val bg: Color = Color(0xFF0A0A0D),
    val surface: Color = Color(0xFF141419),
    val surface2: Color = Color(0xFF1D1D24),
    val text: Color = Color(0xFFF4F4F6),
    val dim: Color = Color(0xFF9A9AA6),
) {
    val line: Color get() = Color.White.copy(alpha = 0.07f)
}

val HudThemes = listOf(
    Hud("Đỏ Titan", Color(0xFFFF3D4A), Color(0xFFFFB547)),
    Hud("Xanh Khiên", Color(0xFF3D8BFF), Color(0xFF7FE0FF)),
    Hud("Vô Cực", Color(0xFFA66BFF), Color(0xFFFF7AD9)),
    Hud("Gamma", Color(0xFF2EE59D), Color(0xFFB8FF5C)),
    Hud("Báo Đen", Color(0xFFC9CCD6), Color(0xFF8F7BFF)),
    Hud("Sấm Sét", Color(0xFF3FC5FF), Color(0xFFFFE45C)),
)

val LocalHud = compositionLocalOf { HudThemes[0] }

val Pill = RoundedCornerShape(50)

@Composable
fun CodTheme(content: @Composable () -> Unit) {
    val h = HudThemes[Ui.theme.coerceIn(0, HudThemes.size - 1)]
    CompositionLocalProvider(LocalHud provides h) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = h.primary, secondary = h.accent, tertiary = h.accent,
                background = h.bg, surface = h.surface2, surfaceVariant = h.surface2,
                onPrimary = Color.White, onSecondary = Color.Black, onBackground = h.text, onSurface = h.text,
                onSurfaceVariant = h.dim,
            ),
            content = content
        )
    }
}

fun Modifier.card(h: Hud, radius: Dp = 18.dp, color: Color = h.surface): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalHud.current.dim) {
    Text(
        text.uppercase(), modifier = modifier, color = color, fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, maxLines = 1
    )
}

@Composable
fun FmtBadge(f: Fmt, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(f.color.copy(alpha = 0.14f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(f.label, color = f.color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector? = null,
    filled: Boolean = true,
    modifier: Modifier = Modifier,
    color: Color = LocalHud.current.primary,
    onClick: () -> Unit,
) {
    val h = LocalHud.current
    Row(
        modifier
            .clip(Pill)
            .background(if (filled) color else h.surface2)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (filled) (if (color.luminance() > 0.6f) Color.Black else Color.White) else h.text
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PillChip(text: String, selected: Boolean, modifier: Modifier = Modifier, count: String? = null, onClick: () -> Unit) {
    val h = LocalHud.current
    Row(
        modifier
            .clip(Pill)
            .background(if (selected) h.primary.copy(alpha = 0.18f) else h.surface2)
            .border(1.dp, if (selected) h.primary.copy(alpha = 0.6f) else Color.Transparent, Pill)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = if (selected) h.primary else h.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count, color = if (selected) h.primary.copy(alpha = 0.8f) else h.dim, fontSize = 12.sp)
        }
    }
}

/** Logo: vòng sóng âm chuyển màu + nút play. */
@Composable
fun Emblem(size: Dp, modifier: Modifier = Modifier) {
    val h = LocalHud.current
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = center
        drawCircle(Color(0xFF15151B), radius = s / 2, center = c)
        val sw = s * 0.075f
        drawCircle(
            Brush.sweepGradient(listOf(h.primary, h.accent, h.primary), center = c),
            radius = s / 2 - sw * 1.2f, center = c, style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawCircle(h.primary.copy(alpha = 0.18f), radius = s * 0.30f, center = c)
        val p = Path().apply {
            moveTo(c.x - s * 0.09f, c.y - s * 0.15f)
            lineTo(c.x + s * 0.16f, c.y)
            lineTo(c.x - s * 0.09f, c.y + s * 0.15f)
            close()
        }
        drawPath(p, Color.White)
        drawPath(p, Color.White, style = Stroke(s * 0.04f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
fun ArtOrEmblem(art: ImageBitmap?, modifier: Modifier, iconSize: Dp = 40.dp) {
    val h = LocalHud.current
    if (art != null) {
        Image(art, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.background(Brush.linearGradient(listOf(h.surface2, Color(0xFF101014)), Offset.Zero, Offset.Infinite)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = h.dim.copy(alpha = 0.6f), modifier = Modifier.size(iconSize))
        }
    }
}

/** Nền ảnh bìa làm mờ kiểu Poweramp. */
@Composable
fun BlurBackdrop(art: ImageBitmap?, accent: Color) {
    val h = LocalHud.current
    Box(Modifier.fillMaxSize().background(h.bg)) {
        if (art != null) {
            val small = androidx.compose.runtime.remember(art) {
                val b = art.asAndroidBitmap()
                Bitmap.createScaledBitmap(b, 40, maxOf(1, 40 * b.height / maxOf(1, b.width)), true).asImageBitmap()
            }
            Image(
                small, null,
                Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(40.dp) else Modifier),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, alpha = 0.55f
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent), center = Offset(500f, 300f), radius = 1400f)
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.55f), h.bg.copy(alpha = 0.97f)))
            )
        )
    }
}

@Composable
fun rememberArt(t: Track?): State<ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, t?.id) {
        value = if (t == null) null else withContext(Dispatchers.IO) { Art.load(ctx, t)?.asImageBitmap() }
    }
}

@Composable
fun rememberThumb(t: Track): State<ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState<ImageBitmap?>(null, t.id) {
        value = withContext(Dispatchers.IO) { Art.thumb(ctx, t)?.asImageBitmap() }
    }
}

@Composable
fun rememberTech(t: Track?): State<Tech?> {
    val ctx = LocalContext.current
    return produceState<Tech?>(null, t?.id) {
        value = if (t == null) null else withContext(Dispatchers.IO) { TechReader.read(ctx, t) }
    }
}

/** Lấy màu nổi bật từ ảnh bìa để tô nút, thanh tua, hiệu ứng. */
@Composable
fun rememberAccent(art: ImageBitmap?, fallback: Color): State<Color> =
    produceState(fallback, art, fallback) {
        value = if (art == null) fallback else withContext(Dispatchers.Default) {
            try {
                val p = Palette.from(art.asAndroidBitmap()).maximumColorCount(16).generate()
                val sw = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.mutedSwatch ?: p.dominantSwatch
                if (sw == null) fallback else {
                    val hsl = sw.hsl.clone()
                    if (hsl[1] < 0.25f) fallback else {
                        hsl[2] = hsl[2].coerceIn(0.5f, 0.68f)
                        hsl[1] = hsl[1].coerceAtLeast(0.55f)
                        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                    }
                }
            } catch (_: Throwable) { fallback }
        }
    }

@Composable
fun Txt(text: String, size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = color, fontSize = size, fontWeight = weight, maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

val CircleClip = CircleShape
