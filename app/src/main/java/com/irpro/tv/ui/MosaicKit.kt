package com.irpro.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.R
import kotlinx.coroutines.delay

/* Pièces communes du style « Mosaïque » (accueil, direct, films, séries, guide). */

object Mz {
    val Bg = Color(0xFF07060D)
    val Text = Color(0xFFF5F3FF)
    val Soft = Color(0xFFE4E1F0)
    val Dim = Color(0xFFB4B0C8)
    val Mute = Color(0xFF8E89A8)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Ink = Color(0xFF0B0918)
    val Tile = Color(0x0AFFFFFF)
    val TileLine = Color(0x14FFFFFF)
    val Live = Color(0xFFF43F5E)
    val Pill = Brush.horizontalGradient(listOf(Color(0xFFA78BFA), Color(0xFF67E8F9)))
    val Bar = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF22D3EE)))
}

/** Fond noir avec deux lueurs (violette et cyan) qui dérivent lentement. */
@Composable
fun MosaicBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val inf = rememberInfiniteTransition(label = "bg")
    val d by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    Box(modifier.fillMaxSize().background(Mz.Bg)) {
        Box(Modifier.offset((-150 + 60 * d).dp, (-200 + 30 * d).dp).size(700.dp, 500.dp)
            .background(Brush.radialGradient(listOf(Color(0x528B5CF6), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomEnd).offset((200 - 60 * d).dp, (250 - 30 * d).dp).size(700.dp, 500.dp)
            .background(Brush.radialGradient(listOf(Color(0x2922D3EE), Color.Transparent))))
        content()
    }
}

/** En-tête : logo, titre, petite étiquette, éléments en plus, heure. */
@Composable
fun MosaicHeader(title: String, chip: String? = null, extra: @Composable RowScope.() -> Unit = {}) {
    var clock by remember { mutableStateOf(hhmm(System.currentTimeMillis())) }
    LaunchedEffect(Unit) { while (true) { clock = hhmm(System.currentTimeMillis()); delay(15_000) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), "IR PRO TV", Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)))
        Spacer(Modifier.width(9.dp))
        Text(title, color = Mz.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IrFonts.title)
        if (chip != null) Text(chip, color = Color(0xFFD6D3E4), fontSize = 10.sp,
            modifier = Modifier.padding(start = 10.dp).background(Color(0x12FFFFFF), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp))
        extra()
        Spacer(Modifier.weight(1f))
        Text(clock, color = Mz.Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Anneau cyan qui « respire » autour de l'élément sélectionné. */
@Composable
fun Modifier.mosaicRing(on: Boolean, shape: Shape, gap: Color = Mz.Bg): Modifier {
    if (!on) return this
    val inf = rememberInfiniteTransition(label = "ring")
    val g by inf.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "g")
    return this.shadow((12 + 14 * g).dp, shape, ambientColor = Mz.Cyan, spotColor = Mz.Cyan)
        .border(3.dp, Mz.Cyan.copy(alpha = 0.6f + 0.4f * g), shape).padding(2.dp).border(1.5.dp, gap, shape)
}

/** Apparition en cascade (index = ordre d'arrivée). */
@Composable
fun Modifier.mosaicAppear(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(40L + index * 110L); a.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = a.value
        val s = 0.94f + 0.06f * a.value
        scaleX = s; scaleY = s
        translationY = (1f - a.value) * 36f
    }
}

/** Point rouge qui clignote (direct). */
@Composable
fun LiveDot(size: androidx.compose.ui.unit.Dp = 6.dp) {
    val inf = rememberInfiniteTransition(label = "dot")
    val a by inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).graphicsLayer { alpha = a }.background(Mz.Live, androidx.compose.foundation.shape.CircleShape))
}
