package nl.openschatkamer.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
    val Ink = Color(0xFF0E0F13)
    val Surface = Color(0xFF17191F)
    val SurfaceHigh = Color(0xFF21242C)
    val Outline = Color(0xFF30343E)
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

@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.openschatkamer_mark),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** Het woordmerk: spoel, met "Open" klein in amber boven "Schatkamer". */
@Composable
fun Wordmark(modifier: Modifier = Modifier, markSize: Dp = 30.dp, fontSize: TextUnit = 22.sp) {
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
    tagline: String = "Kijken en luisteren in het archief van de Nederlandse televisie"
) {
    val spin = rememberInfiniteTransition(label = "spoel")
    val angle by spin.animateFloat(
        0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "hoek"
    )
    val fade = remember { Animatable(1f) }
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
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
            .background(Brush.verticalGradient(listOf(Color(0xFF1C1D24), BrandColors.Ink))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(intro.value)) {
            BrandMark(96.dp, Modifier.rotate(angle))
            Spacer(Modifier.height(28.dp))
            BasicText("Open", style = TextStyle(fontFamily = BrandFonts.Display, fontSize = 20.sp, color = BrandColors.Amber))
            BasicText("Schatkamer", style = TextStyle(fontFamily = BrandFonts.Display, fontSize = 40.sp, color = BrandColors.Text))
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
