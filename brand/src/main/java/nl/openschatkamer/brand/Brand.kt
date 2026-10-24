package nl.openschatkamer.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * De huisstijl van OpenSchatkamer: een onofficiële app voor het openbare
 * archief van De Schatkamer. Bewust een eigen beeldmerk en eigen kleuren,
 * zodat niemand denkt dat dit de app van Beeld & Geluid is.
 */
object BrandColors {
    /** Nachtblauw, dezelfde basis als het launcher-icoon. */
    val Ink = Color(0xFF0B0F1C)
    val Surface = Color(0xFF131A2B)
    val SurfaceHigh = Color(0xFF1C2439)
    val Outline = Color(0xFF2B3550)
    val NightTop = Color(0xFF1E2848)
    val Gold = Color(0xFFFFDC78)
    val Amber = Color(0xFFF2B33D)
    val AmberDeep = Color(0xFFC98A1B)
    val Text = Color(0xFFF4F0E8)
    val TextMuted = Color(0xFFA9A59B)
    val Audio = Color(0xFF7FD1C7)
    val Danger = Color(0xFFFF7A6B)
}

object BrandFonts {
    /** DM Serif Display (SIL Open Font License) voor titels; brood-tekst blijft het systeemfont. */
    val Display = FontFamily(Font(R.font.dm_serif_display))
}

const val UNOFFICIAL_NOTICE =
    "OpenSchatkamer is een onofficiële app en niet verbonden aan Beeld & Geluid. " +
        "Alle uitzendingen komen uit De Schatkamer (schatkamer.beeldengeluid.nl) en zijn " +
        "eigendom van Beeld & Geluid en de omroepen."

/**
 * Het beeldmerk: een "O" die op een kier staat (open), met een lens en een
 * afspeelteken. Getekend in Compose, dus scherp op elk formaat; [open] laat
 * de ring openzwaaien (0..1) voor het opstartscherm.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier, open: Float = 1f) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 48f
        val gold = Brush.linearGradient(
            listOf(BrandColors.Gold, BrandColors.AmberDeep),
            start = androidx.compose.ui.geometry.Offset(6 * u, 4 * u),
            end = androidx.compose.ui.geometry.Offset(42 * u, 44 * u)
        )
        val ringRadius = 18.7f * u
        val sweep = 312f * open.coerceIn(0f, 1f)
        drawArc(
            brush = gold,
            startAngle = -30f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(24 * u - ringRadius, 24 * u - ringRadius),
            size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.6f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        val lens = androidx.compose.ui.graphics.Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(24 * u, 24 * u), 12f * u))
            val h = 10.4f * u
            val w = h * 0.9f
            val cx = 24 * u + w * 0.1f
            moveTo(cx - w / 2, 24 * u - h / 2)
            lineTo(cx - w / 2, 24 * u + h / 2)
            lineTo(cx + w / 2, 24 * u)
            close()
        }
        drawPath(lens, gold)
        val dotAngle = Math.toRadians(-54.0)
        drawCircle(
            color = Color(0xFFFFECB4).copy(alpha = open.coerceIn(0f, 1f)),
            radius = 2.1f * u,
            center = androidx.compose.ui.geometry.Offset(
                (24 + 18.7 * kotlin.math.cos(dotAngle)).toFloat() * u,
                (24 + 18.7 * kotlin.math.sin(dotAngle)).toFloat() * u
            )
        )
    }
}

/** Het woordmerk: spoel, met "Open" klein in amber boven "Schatkamer". */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 30.dp,
    fontSize: TextUnit = 22.sp,
    platformSuffix: String? = null
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(markSize)
        Spacer(Modifier.width(markSize * 0.3f))
        Column(verticalArrangement = Arrangement.spacedBy((-6).dp)) {
            BasicText(
                "Open",
                style = TextStyle(fontFamily = BrandFonts.Display, fontSize = fontSize * 0.5f, color = BrandColors.Amber)
            )
            BasicText(
                "Schatkamer",
                style = TextStyle(fontFamily = BrandFonts.Display, fontSize = fontSize, color = BrandColors.Text)
            )
        }
        if (platformSuffix != null) {
            Spacer(Modifier.width(fontSize.value.dp * 0.4f))
            BasicText(
                platformSuffix,
                modifier = Modifier
                    .background(BrandColors.Amber, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
                style = TextStyle(fontFamily = BrandFonts.Display, fontSize = fontSize * 0.55f, color = BrandColors.Ink)
            )
        }
    }
}

/**
 * Opstartscherm: de spoel draait tot de catalogus er is ([ready]) en vervaagt
 * dan. Na minimaal een korte toonduur volgt [onFinished].
 */
@Composable
fun BrandSplash(
    ready: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    tagline: String = "Kijken en luisteren in het archief van de Nederlandse televisie",
    platformSuffix: String? = null
) {
    val pulse = rememberInfiniteTransition(label = "laden")
    val glow by pulse.animateFloat(0.85f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "gloed")
    val fade = remember { Animatable(1f) }
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    LaunchedEffect(ready) {
        if (ready) {
            kotlinx.coroutines.delay(450)
            fade.animateTo(0f, tween(350))
            onFinished()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(Brush.linearGradient(listOf(BrandColors.NightTop, BrandColors.Ink))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(104.dp, Modifier.alpha(glow), open = intro.value)
            Spacer(Modifier.height(28.dp))
            BasicText("Open", style = TextStyle(fontFamily = BrandFonts.Display, fontSize = 20.sp, color = BrandColors.Amber))
            BasicText("Schatkamer", style = TextStyle(fontFamily = BrandFonts.Display, fontSize = 40.sp, color = BrandColors.Text))
            if (platformSuffix != null) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    platformSuffix,
                    modifier = Modifier
                        .background(BrandColors.Amber, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    style = TextStyle(fontFamily = BrandFonts.Display, fontSize = 18.sp, color = BrandColors.Ink)
                )
            }
            Spacer(Modifier.height(12.dp))
            BasicText(
                tagline,
                modifier = Modifier.padding(horizontal = 40.dp),
                style = TextStyle(fontSize = 14.sp, color = BrandColors.TextMuted, textAlign = TextAlign.Center)
            )
        }
        BasicText(
            "Onofficiële app · bron: De Schatkamer van Beeld & Geluid",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            style = TextStyle(fontSize = 11.sp, color = BrandColors.TextMuted.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        )
    }
}
