package com.irpro.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Couleurs validées pour IR PRO TV */
object IrColors {
    val Night = Color(0xFF0A1430)
    val Black = Color(0xFF060607)
    val Violet = Color(0xFF7C3AED)
    val Cyan = Color(0xFF22D3EE)
    val TextDim = Color(0xFF8D8D96)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Intro vidéo (complète la 1re fois, courte ensuite), puis l'appli
            var introDone by rememberSaveable { mutableStateOf(false) }
            if (!introDone) IntroScreen(onFinished = { introDone = true }) else StartScreen()
        }
    }
}

/**
 * Étape 1 : écran de démarrage simple pour vérifier l'installation.
 * L'intro vidéo validée arrive à l'étape 2.
 */
@Composable
fun StartScreen() {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(durationMillis = 900)) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1B1726), IrColors.Black),
                    radius = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Le logo s'adapte à la hauteur de l'écran (petit téléphone ou grande TV)
        val logoSize = (maxHeight * 0.38f).coerceIn(90.dp, 200.dp)
        val gap = (maxHeight * 0.04f).coerceIn(8.dp, 28.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.alpha(appear.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo IR PRO TV",
                modifier = Modifier
                    .size(logoSize)
                    .scale(0.85f + 0.15f * appear.value)
            )
            Spacer(Modifier.height(gap))
            Text(
                text = buildAnnotatedString {
                    append("IR PRO ")
                    withStyle(SpanStyle(color = Color(0xFFA78BFA))) { append("TV") }
                },
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "IPTV · 4K",
                color = IrColors.Cyan,
                fontSize = 16.sp,
                letterSpacing = 8.sp
            )
            Spacer(Modifier.height(gap * 1.5f))
            // Placé sous le reste (plus en bas d'écran) : ne peut plus chevaucher le texte
            Text(
                text = "Prochaine étape : ajout de ta playlist",
                color = Color(0xFFC9C9D1),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                color = IrColors.TextDim,
                fontSize = 12.sp
            )
        }
    }
}
