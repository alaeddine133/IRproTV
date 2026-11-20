package com.irpro.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.R
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.Playlist
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Les sections de l'accueil, dans l'ordre des tuiles. */
enum class Section(val icon: IrIcon, val label: String, val title: String, val action: String, val bg: Color, val glow: Color) {
    LIVE(IrIcon.LIVE, "Direct", "Regarde la TV en direct", "Regarder", Color(0xFF24306B), Color(0xFF3B4AA0)),
    MOVIES(IrIcon.MOVIE, "Films", "Tous tes films", "Parcourir", Color(0xFF34215E), Color(0xFF5B3BB0)),
    SERIES(IrIcon.SERIES, "Séries", "Toutes tes séries", "Parcourir", Color(0xFF1C3A60), Color(0xFF2E66A8)),
    GUIDE(IrIcon.GUIDE, "Guide TV", "Le guide des programmes", "Ouvrir", Color(0xFF173F48), Color(0xFF1E7686)),
    FAVORITES(IrIcon.FAVORITE, "Favoris", "Tes chaînes préférées", "Ouvrir", Color(0xFF451C3C), Color(0xFF8A2E6E)),
}

private fun subtitle(s: Section, c: Catalog): String {
    val frLive = c.liveCategories.count { it.isFrench }
    return when (s) {
        Section.LIVE -> "${formatCount(c.channels.size)} chaînes" + if (frLive > 0) " · français en premier" else ""
        Section.MOVIES -> "${formatCount(c.movies.size)} films · ${c.movieCategories.size} catégories"
        Section.SERIES -> "${formatCount(c.series.size)} séries · ${c.seriesCategories.size} catégories"
        Section.GUIDE -> "Ce qui passe maintenant et ce soir"
        Section.FAVORITES -> "Appui long sur une chaîne pour l'ajouter"
    }
}

@Composable
fun HomeScreen(
    playlist: Playlist,
    catalog: Catalog,
    updating: Boolean,
    onOpen: (Section) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val section = Section.entries[selected]
    val bg by animateColorAsState(section.bg, tween(450), label = "bg")
    val glow by animateColorAsState(section.glow, tween(450), label = "glow")
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTile.requestFocus() }

    // Retour : une 1re fois prévient, la 2e quitte l'appli
    var exitHint by remember { mutableStateOf(false) }
    BackHandler {
        if (exitHint) activity?.finish() else exitHint = true
    }
    LaunchedEffect(exitHint) { if (exitHint) { delay(2500); exitHint = false } }

    var clock by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) { while (true) { clock = now(); delay(15_000) } }

    BoxWithConstraints(Modifier.fillMaxSize().background(IrColors.Night)) {
        val compact = maxHeight < 420.dp
        // Grand panneau coloré à droite (change selon la tuile)
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.6f)
                .align(Alignment.TopEnd)
                .background(bg)
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-60).dp, y = 10.dp)
                .size(if (compact) 200.dp else 300.dp)
                .clip(CircleShape)
                .background(glow)
        )
        IrIconView(
            section.icon, Color(0x55FFFFFF), if (compact) 110.dp else 160.dp,
            Modifier.align(Alignment.CenterEnd).offset(x = (-130).dp, y = 10.dp)
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = if (compact) 14.dp else 26.dp)) {
            // Barre du haut
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    buildAnnotatedString { append("IR PRO "); withStyle(SpanStyle(color = IrColors.Cyan)) { append("TV") } },
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (updating) Text("Mise à jour…", color = IrColors.Cyan, fontSize = 13.sp, modifier = Modifier.padding(end = 14.dp))
                RoundIconButton(IrIcon.SEARCH, onSearch)
                Spacer(Modifier.width(10.dp))
                RoundIconButton(IrIcon.SETTINGS, onSettings)
                Spacer(Modifier.width(16.dp))
                Text(clock, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(if (compact) 12.dp else 22.dp))

            // Rangée de tuiles façon console : la tuile sélectionnée grandit et affiche son nom
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Section.entries.forEachIndexed { i, s ->
                    ConsoleTile(
                        section = s,
                        big = if (compact) 72.dp else 92.dp,
                        small = if (compact) 48.dp else 58.dp,
                        modifier = if (i == 0) Modifier.focusRequester(firstTile) else Modifier,
                        onFocused = { selected = i },
                        onClick = { onOpen(s) },
                        selected = i == selected,
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 14.dp else 34.dp))

            // Texte de la section + bouton
            Column(Modifier.fillMaxWidth(0.5f)) {
                Text(subtitle(section, catalog), color = Color(0xFFB9B0FF), fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(section.title, color = Color.White, fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                IrButton("▶  ${section.action}", onClick = { onOpen(section) })
            }

            Spacer(Modifier.weight(1f))

            // Cartes du bas
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val latest = remember(catalog) { catalog.movies.maxByOrNull { it.added }?.takeIf { it.added > 0 } }
                InfoCard("Dernière chaîne", "Aucune pour l'instant", Modifier.weight(1f)) { onOpen(Section.LIVE) }
                InfoCard("Favoris", "Ajoute tes chaînes préférées", Modifier.weight(1f)) { onOpen(Section.FAVORITES) }
                InfoCard("Nouveau film", latest?.name ?: "—", Modifier.weight(1f)) { onOpen(Section.MOVIES) }
                InfoCard("Abonnement", subscriptionText(playlist), Modifier.weight(1f)) { onSettings() }
            }
        }

        if (exitHint) {
            Text(
                "Appuie encore sur Retour pour quitter",
                color = Color.White, fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .background(Color(0xE6050A1C), RoundedCornerShape(10.dp))
                    .border(1.dp, IrColors.Cyan, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

private fun now(): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())

fun subscriptionText(p: Playlist): String {
    val exp = p.expiresAt ?: return "Illimité ou non communiqué"
    val days = ((exp - System.currentTimeMillis()) / 86_400_000L).toInt()
    return when {
        days < 0 -> "Expiré"
        days == 0 -> "Expire aujourd'hui"
        days == 1 -> "1 jour restant"
        else -> "$days jours restants"
    }
}

@Composable
private fun ConsoleTile(
    section: Section,
    big: androidx.compose.ui.unit.Dp,
    small: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val size by animateDpAsState(if (selected) big else small, tween(220), label = "tile")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(if (selected) 20.dp else 14.dp))
                .background(if (selected) IrColors.Violet else Color(0xFF1D2B55))
                .border(
                    if (focused) 3.dp else 0.dp,
                    if (focused) IrColors.Cyan else Color.Transparent,
                    RoundedCornerShape(if (selected) 20.dp else 14.dp)
                )
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            IrIconView(section.icon, if (selected) Color.White else Color(0xFFC9D4FF), size * 0.46f)
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Text(section.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoundIconButton(icon: IrIcon, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) IrColors.Violet else Color(0x22FFFFFF))
            .border(if (focused) 2.dp else 0.dp, if (focused) IrColors.Cyan else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { IrIconView(icon, Color.White, 20.dp) }
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) IrColors.Violet else Color(0x1AFFFFFF))
            .border(if (focused) 2.dp else 1.dp, if (focused) IrColors.Cyan else Color(0x26FFFFFF), RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (focused) Color(0xFFE4DCFF) else Color(0xFFB9B0FF), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
