package vn.daopham.codplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Hud(
    val name: String,
    val primary: Color,
    val accent: Color,
    val glow: Color,
    val bg: Color,
    val panel: Color,
    val text: Color = Color(0xFFF3F3F6),
    val dim: Color = Color(0xFF8C8F9C),
)

val HudThemes = listOf(
    Hud("Đỏ Titan", Color(0xFFE62429), Color(0xFFFFC23D), Color(0xFFFF4A2E), Color(0xFF07060A), Color(0xFF16090C)),
    Hud("Khiên Sao", Color(0xFF2F6BFF), Color(0xFFE9ECF4), Color(0xFF59A8FF), Color(0xFF05070D), Color(0xFF0B1222)),
    Hud("Vô Cực", Color(0xFF9D4DFF), Color(0xFFFFC23D), Color(0xFFC77DFF), Color(0xFF07050C), Color(0xFF140C22)),
    Hud("Gamma", Color(0xFF38E07B), Color(0xFFB57BFF), Color(0xFF5CFF9A), Color(0xFF040805), Color(0xFF0A1A10)),
    Hud("Báo Đen", Color(0xFFB98CFF), Color(0xFFD9DCE6), Color(0xFF9F6BFF), Color(0xFF040406), Color(0xFF111118)),
    Hud("Sấm Sét", Color(0xFF34B6FF), Color(0xFFFFE15A), Color(0xFF7FD4FF), Color(0xFF04070B), Color(0xFF0B1620)),
)

val LocalHud = compositionLocalOf { HudThemes[0] }

val Condensed: FontFamily = FontFamily(
    Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD))
)

val HexShape = GenericShape { s, _ ->
    moveTo(s.width * 0.25f, 0f); lineTo(s.width * 0.75f, 0f); lineTo(s.width, s.height / 2)
    lineTo(s.width * 0.75f, s.height); lineTo(s.width * 0.25f, s.height); lineTo(0f, s.height / 2); close()
}

val SlantShape = GenericShape { s, _ ->
    val k = s.height * 0.35f
    moveTo(k, 0f); lineTo(s.width, 0f); lineTo(s.width - k, s.height); lineTo(0f, s.height); close()
}

fun hudCut(c: Dp = 12.dp) = CutCornerShape(topStart = c, bottomEnd = c)

@Composable
fun CodTheme(content: @Composable () -> Unit) {
    val h = HudThemes[Ui.theme.coerceIn(0, HudThemes.size - 1)]
    CompositionLocalProvider(LocalHud provides h) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = h.primary, secondary = h.accent, tertiary = h.glow,
                background = h.bg, surface = h.panel, surfaceVariant = h.panel,
                onPrimary = Color.White, onSecondary = Color.Black, onBackground = h.text, onSurface = h.text,
                onSurfaceVariant = h.dim,
            ),
            content = content
        )
    }
}

@Composable
fun HudBackground(content: @Composable BoxScope.() -> Unit) {
    val h = LocalHud.current
    Box(
        Modifier
            .fillMaxSize()
            .background(h.bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(h.primary.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f), radius = size.width * 0.95f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(h.accent.copy(alpha = 0.07f), Color.Transparent),
                        center = Offset(0f, size.height * 0.85f), radius = size.width
                    )
                )
                val step = 32.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(Color.White.copy(alpha = 0.025f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y), Offset(size.width, y), 1f); y += step }
            },
        content = content
    )
}

fun Modifier.hudPanel(h: Hud, cut: Dp = 12.dp, alpha: Float = 0.85f): Modifier {
    val shape = hudCut(cut)
    return this
        .clip(shape)
        .background(h.panel.copy(alpha = alpha))
        .border(
            1.dp,
            Brush.linearGradient(listOf(h.primary.copy(alpha = 0.9f), h.accent.copy(alpha = 0.3f), h.primary.copy(alpha = 0.15f))),
            shape
        )
}

@Composable
fun HudText(
    text: String,
    size: TextUnit = 14.sp,
    color: Color = LocalHud.current.text,
    spacing: TextUnit = 1.5.sp,
    italic: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(), modifier = modifier, color = color, fontSize = size, fontFamily = Condensed,
        fontWeight = FontWeight.Bold, letterSpacing = spacing, maxLines = 1,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
    )
}

@Composable
fun FmtBadge(f: Fmt, modifier: Modifier = Modifier) {
    Box(
        modifier
            .border(1.dp, f.color.copy(alpha = 0.8f), CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp))
            .background(f.color.copy(alpha = 0.12f), CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(f.label, color = f.color, fontSize = 9.sp, fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun HudButton(
    text: String,
    icon: ImageVector? = null,
    filled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val h = LocalHud.current
    val shape = hudCut(10.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (filled) Brush.horizontalGradient(listOf(h.primary, h.primary.copy(alpha = 0.75f))) else Brush.linearGradient(listOf(h.panel, h.panel)))
            .border(1.dp, if (filled) h.accent.copy(alpha = 0.6f) else h.primary.copy(alpha = 0.7f), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Color.White else h.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        HudText(text, 13.sp, if (filled) Color.White else h.text)
    }
}

@Composable
fun HudChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val h = LocalHud.current
    val shape = hudCut(7.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) h.primary else h.panel.copy(alpha = 0.7f))
            .border(1.dp, if (selected) h.accent.copy(alpha = 0.7f) else h.dim.copy(alpha = 0.3f), shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        HudText(text, 12.sp, if (selected) Color.White else h.dim, spacing = 1.sp)
    }
}

/** Biểu tượng của app: khiên lục giác + cột phổ. */
@Composable
fun Emblem(size: Dp, modifier: Modifier = Modifier) {
    val h = LocalHud.current
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val hh = this.size.height
        fun hex(inset: Float): Path = Path().apply {
            val cx = w / 2; val cy = hh / 2; val r = w / 2 - inset
            for (i in 0 until 6) {
                val a = Math.toRadians((60.0 * i - 90.0)).toFloat()
                val x = cx + r * kotlin.math.cos(a); val y = cy + r * kotlin.math.sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(hex(0f), h.accent)
        drawPath(hex(w * 0.07f), h.primary)
        drawPath(hex(w * 0.07f), Color.Black.copy(alpha = 0.15f), style = Stroke(w * 0.02f))
        val bars = floatArrayOf(0.25f, 0.45f, 0.62f, 0.4f, 0.22f)
        val bw = w * 0.09f
        val gap = w * 0.035f
        val total = bars.size * bw + (bars.size - 1) * gap
        var x = (w - total) / 2
        val base = hh * 0.68f
        for (b in bars) {
            drawRect(Color.White, Offset(x, base - b * hh * 0.6f), androidx.compose.ui.geometry.Size(bw, b * hh * 0.6f))
            x += bw + gap
        }
    }
}

@Composable
fun ArtOrEmblem(art: ImageBitmap?, title: String, modifier: Modifier) {
    val h = LocalHud.current
    if (art != null) {
        Image(art, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.background(
                Brush.radialGradient(listOf(h.primary.copy(alpha = 0.55f), h.panel, h.bg))
            ),
            contentAlignment = Alignment.Center
        ) {
            Emblem(56.dp)
        }
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
fun rememberTech(t: Track?): State<Tech?> {
    val ctx = LocalContext.current
    return produceState<Tech?>(null, t?.id) {
        value = if (t == null) null else withContext(Dispatchers.IO) { TechReader.read(ctx, t) }
    }
}
