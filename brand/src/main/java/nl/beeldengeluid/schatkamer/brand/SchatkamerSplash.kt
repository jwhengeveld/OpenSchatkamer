package nl.beeldengeluid.schatkamer.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val BrandDarkBackground = Color(0xFF111218)
val BrandDarkElevated = Color(0xFF181A24)
val BrandCyan = Color(0xFF05C8F0)
val BrandMagenta = Color(0xFFFF00BC)
val BrandTextLight = Color(0xFFF0F1FA)
val BrandTextMuted = Color(0xFFA0A5BC)

/**
 * Het geanimeerde splashscherm van De Schatkamer (Beeld & Geluid).
 */
@Composable
fun SchatkamerSplash(
    ready: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    tagline: String = "Schatzoeken in het archief van Beeld & Geluid"
) {
    val emblemLift = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    val subtitle = remember { Animatable(0f) }
    val waiting = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    val currentReady by rememberUpdatedState(ready)
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            delay(120)
            listOf(
                async { emblemLift.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) },
                async { delay(80); glow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) },
                async { delay(320); text.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) },
                async { delay(520); subtitle.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
            ).awaitAll()
        }
        val dots = launch { delay(500); waiting.animateTo(1f, tween(400)) }
        while (!currentReady) delay(50)
        dots.cancel()
        exit.animateTo(0f, tween(320, easing = LinearEasing))
        currentOnFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        BrandDarkElevated.copy(alpha = 0.9f + glow.value * 0.1f),
                        BrandDarkBackground
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Zachte subtiele cirkelgloed achter het logo
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(y = (-40).dp * emblemLift.value)
                .alpha(glow.value * 0.25f)
                .scale(0.8f + glow.value * 0.35f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(BrandCyan.copy(alpha = 0.35f), BrandMagenta.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        // Het embleem
        Image(
            painter = painterResource(R.drawable.schatkamer_mark),
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .offset(y = (-55).dp * emblemLift.value)
                .scale(0.95f + emblemLift.value * 0.12f)
        )

        // Het woordmerk "DE SCHATKAMER"
        Image(
            painter = painterResource(R.drawable.schatkamer_logo),
            contentDescription = "De Schatkamer",
            modifier = Modifier
                .width(220.dp)
                .offset(y = 20.dp)
                .alpha(text.value)
                .scale(0.92f + text.value * 0.08f)
        )

        // De ondertitel
        BasicText(
            text = tagline,
            modifier = Modifier
                .offset(y = 65.dp)
                .alpha(subtitle.value),
            style = TextStyle(
                color = BrandTextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        )

        // Pulserende laadbolletjes onderin als het laden wat langer duurt
        if (waiting.value > 0f) {
            PulsingDots(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-48).dp)
                    .alpha(waiting.value)
            )
        }
    }
}

@Composable
private fun PulsingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "schatkamer_splash_dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, delayMillis = index * 140, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "schatkamer_dot_$index"
            )
            val dotColor = if (index % 2 == 0) BrandCyan else BrandMagenta
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(scale)
                    .background(dotColor.copy(alpha = 0.5f + scale * 0.5f), CircleShape)
            )
        }
    }
}
