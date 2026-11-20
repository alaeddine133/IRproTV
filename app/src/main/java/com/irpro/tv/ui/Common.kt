package com.irpro.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(ms))

/** 1:02:03 ou 02:03 */
fun clockTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(Locale.FRANCE, "%d:%02d:%02d", h, m, s) else String.format(Locale.FRANCE, "%02d:%02d", m, s)
}

/** Initiales d'un nom de chaîne (quand il n'y a pas de logo). */
fun initials(name: String): String =
    name.replace(Regex("""^(FR|UK|US|AR|DE|IT|ES)\s*[|:\-\]]\s*""", RegexOption.IGNORE_CASE), "")
        .split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(2) }.uppercase().take(4)

/** Logo de chaîne / affiche avec repli élégant si l'image manque. */
@Composable
fun NetImage(url: String, fallback: String, width: Dp, height: Dp, modifier: Modifier = Modifier, crop: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.size(width, height).clip(shape).background(Color(0xFF1D2B55)),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Text(fallback, color = Color(0xFFC9D4FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.size(width, height),
                error = { Box(Modifier.size(width, height), contentAlignment = Alignment.Center) { Text(fallback, color = Color(0xFFC9D4FF), fontSize = 12.sp, fontWeight = FontWeight.Bold) } },
                loading = { Box(Modifier.size(width, height)) },
            )
        }
    }
}
