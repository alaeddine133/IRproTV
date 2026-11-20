package com.irpro.tv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.R
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.Channel
import com.irpro.tv.data.Epg
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.Profiles
import com.irpro.tv.data.UserData
import kotlinx.coroutines.delay

/*
 * Accueil « Mosaïque » (proposition C validée) : grandes tuiles vivantes.
 * Maquette 1920×1080 px ; ici 1 px = 0,5 dp.
 */

private val M_BG = Color(0xFF07060D)
private val M_TXT = Color(0xFFF5F3FF)
private val M_CYAN = Color(0xFF22D3EE)

@Composable
fun MosaicHomeScreen(
    playlist: Playlist,
    catalog: Catalog,
    updating: Boolean,
    onOpen: (Section) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onMyList: () -> Unit,
    onProfiles: () -> Unit,
    lastChannel: Channel? = null,
    onPlayChannel: (Channel) -> Unit = {},
    onMovie: (String) -> Unit = {},
) {
    val activity = LocalContext.current as? Activity
    val rev by UserData.rev.collectAsState()
    val profRev by Profiles.rev.collectAsState()
    val profile = remember(profRev) { Profiles.current() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }

    var exitHint by remember { mutableStateOf(false) }
    BackHandler { if (exitHint) activity?.finish() else exitHint = true }
    LaunchedEffect(exitHint) { if (exitHint) { delay(2500); exitHint = false } }
    var clock by remember { mutableStateOf(hhmm(System.currentTimeMillis())) }
    LaunchedEffect(Unit) { while (true) { clock = hhmm(System.currentTimeMillis()); delay(15_000) } }

    // Données des tuiles
    val latest = remember(catalog) {
        val withDate = catalog.movies.filter { it.added > 0 }
        (if (withDate.isNotEmpty()) withDate.sortedByDescending { it.added } else catalog.movies).distinctBy { it.id }.take(3)
    }
    val seriesCovers = remember(catalog) { catalog.series.asSequence().filter { it.cover.isNotBlank() }.take(3).toList() }
    val seriesResume = remember(rev, catalog) {
        val r = UserData.resumes(playlist.id).firstOrNull { it.kind == "episode" } ?: return@remember null
        val s = catalog.series.firstOrNull { it.id == r.parentId } ?: return@remember null
        val se = Regex("""S(\d+)\s*·\s*E(\d+)""").find(r.title)?.let { " · S${it.groupValues[1]} É${it.groupValues[2]}" } ?: ""
        "${s.name}$se à reprendre"
    }
    val listCount = remember(rev) { UserData.myList(playlist.id).size }
    val favCount = remember(rev) { UserData.favorites(playlist.id).size }
    val nowOn by produceState<com.irpro.tv.data.Program?>(null, lastChannel?.id) {
        val ch = lastChannel ?: return@produceState
        val t = System.currentTimeMillis()
        value = runCatching { Epg.forChannel(playlist, ch) }.getOrNull()?.firstOrNull { t in it.start until it.end }
    }

    // Lueurs qui dérivent dans le fond
    val inf = rememberInfiniteTransition(label = "home")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")

    BoxWithConstraints(Modifier.fillMaxSize().background(M_BG)) {
        val compact = maxHeight < 420.dp
        Box(Modifier.offset((-150 + 60 * drift).dp, (-200 + 30 * drift).dp).size(700.dp, 500.dp)
            .background(Brush.radialGradient(listOf(Color(0x598B5CF6), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomEnd).offset((200 - 60 * drift).dp, (250 - 30 * drift).dp).size(700.dp, 500.dp)
            .background(Brush.radialGradient(listOf(Color(0x2E22D3EE), Color.Transparent))))

        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 24.dp else 48.dp, vertical = if (compact) 14.dp else 24.dp)) {
            // Barre du haut
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), "IR PRO TV", Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.width(8.dp))
                Text("IR PRO TV", color = M_TXT, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title, letterSpacing = 1.sp)
                Text("Abonnement · ${subscriptionText(playlist)}", color = Color(0xFFD6D3E4), fontSize = 10.sp,
                    modifier = Modifier.padding(start = 12.dp).background(Color(0x12FFFFFF), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp))
                Spacer(Modifier.weight(1f))
                if (updating) Text("Mise à jour…", color = M_CYAN, fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp))
                TopIcon(IrIcon.SETTINGS, "Paramètres", onSettings)
                Spacer(Modifier.width(8.dp))
                TopAvatar(profile.name.take(1).uppercase(), profile.color, onProfiles)
                Spacer(Modifier.width(10.dp))
                Text(clock, color = Color(0xFFE4E1F0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))

            // Mosaïque : Direct (grand) | Films · Séries / Guide · (Ma liste + Recherche)
            val gap = if (compact) 8.dp else 13.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(Modifier.weight(2f).fillMaxHeight(), index = 0, focusMod = Modifier.focusRequester(first), radius = 16.dp,
                    bg = Brush.verticalGradient(listOf(Color(0xFF0B1E3B), Color(0xFF14507A), Color(0xFF1C8F99))),
                    onClick = { if (lastChannel != null) onPlayChannel(lastChannel) else onOpen(Section.LIVE) }) {
                    LiveTileContent(catalog, lastChannel, nowOn, compact)
                }
                Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Tile(Modifier.weight(1f).fillMaxHeight(), index = 1,
                            bg = Brush.linearGradient(listOf(Color(0xFF2A1250), Color(0xFF120A26))), border = Color(0x40A78BFA),
                            onClick = { onOpen(Section.MOVIES) }) {
                            PosterFan(latest.map { it.poster to it.name }, compact)
                            TileLabel("Films", latest.firstOrNull()?.let { "Nouveau : ${it.name}" } ?: "${formatCount(catalog.movies.size)} films", Color(0xFFC9C2E8), compact)
                        }
                        Tile(Modifier.weight(1f).fillMaxHeight(), index = 2,
                            bg = Brush.linearGradient(listOf(Color(0xFF0C2B3E), Color(0xFF071722))), border = Color(0x3822D3EE),
                            onClick = { onOpen(Section.SERIES) }) {
                            SeriesStrips(seriesCovers.map { it.cover to it.name }, compact)
                            TileLabel("Séries", seriesResume ?: "${formatCount(catalog.series.size)} séries", Color(0xFFBFE8F2), compact)
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Tile(Modifier.weight(1f).fillMaxHeight(), index = 3,
                            bg = Brush.linearGradient(listOf(Color(0xFF1C1733), Color(0xFF0D0B18))), border = Color(0x14FFFFFF),
                            onClick = { onOpen(Section.GUIDE) }) {
                            GuideBars(compact)
                            TileLabel("Guide TV", "Ce soir sur tes chaînes", Color(0xFFC9C5DC), compact)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                            Tile(Modifier.weight(1f).fillMaxWidth(), index = 4,
                                bg = Brush.linearGradient(listOf(Color(0xFF3B1C8C), Color(0xFF1A0E3D))), border = Color(0x4DA78BFA),
                                onClick = onMyList) {
                                SmallTileRow(IrIcon.FAVORITE, Color(0xFFC084FC), "Ma liste", "$listCount titre(s) · $favCount chaîne(s)", compact)
                            }
                            Tile(Modifier.weight(1f).fillMaxWidth(), index = 5,
                                bg = Brush.linearGradient(listOf(Color(0x0DFFFFFF), Color(0x0DFFFFFF))), border = Color(0x1AFFFFFF),
                                onClick = onSearch) {
                                SmallTileRow(IrIcon.SEARCH, M_CYAN, "Recherche", "Film, série ou chaîne", compact)
                            }
                        }
                    }
                }
            }
        }

        if (exitHint) {
            Text("Appuie encore sur Retour pour quitter", color = Color.White, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    .background(Color(0xE607060D), RoundedCornerShape(10.dp)).border(1.dp, M_CYAN, RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

/** Une tuile : apparition en cascade, anneau cyan qui respire quand elle est sélectionnée. */
@Composable
private fun Tile(
    modifier: Modifier,
    index: Int,
    bg: Brush,
    border: Color = Color.Transparent,
    radius: Dp = 14.dp,
    focusMod: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(60L + index * 120L); appear.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    val sc by animateFloatAsState(if (f) 1.025f else 1f, tween(220), label = "tile")
    val inf = rememberInfiniteTransition(label = "ring")
    val glow by inf.animateFloat(0.45f, 0.85f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow")
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .graphicsLayer {
                alpha = appear.value
                val s = (0.92f + 0.08f * appear.value) * sc
                scaleX = s; scaleY = s
                translationY = (1f - appear.value) * 40f
            }
            .then(if (f) Modifier.shadow((14 + 14 * glow).dp, shape, ambientColor = M_CYAN, spotColor = M_CYAN)
                .border(4.dp, M_CYAN.copy(alpha = 0.6f + 0.4f * glow), shape).padding(2.dp).border(2.dp, M_BG, shape) else Modifier)
            .then(focusMod)
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() },
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.TileLabel(title: String, sub: String, subColor: Color, compact: Boolean) {
    Column(Modifier.align(Alignment.BottomStart).padding(if (compact) 10.dp else 15.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = M_TXT, fontSize = if (compact) 15.sp else 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IrFonts.title)
        Text(sub, color = subColor, fontSize = if (compact) 8.sp else 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.LiveTileContent(catalog: Catalog, last: Channel?, now: com.irpro.tv.data.Program?, compact: Boolean) {
    // Silhouette de ville + logo de la dernière chaîne
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f).background(Color(0xFF0A2236)))
    if (last != null && last.logo.isNotBlank()) {
        Box(Modifier.align(Alignment.Center).offset(y = (-30).dp).alpha(0.9f)) {
            NetImage(last.logo, initials(last.name), if (compact) 90.dp else 150.dp, if (compact) 60.dp else 100.dp)
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x3307060D), 0.3f to Color.Transparent, 1f to Color(0xF207060D))))

    val inf = rememberInfiniteTransition(label = "live")
    val dot by inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "dot")
    Row(
        Modifier.align(Alignment.TopStart).padding(if (compact) 10.dp else 17.dp).background(Color(0xB307060D), RoundedCornerShape(6.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).alpha(dot).background(Color(0xFFF43F5E), CircleShape))
        Text("DIRECT · ${formatCount(catalog.channels.size)} chaînes", color = M_TXT, fontSize = if (compact) 8.sp else 10.sp, fontWeight = FontWeight.Bold)
    }
    // Petit égaliseur animé
    Row(Modifier.align(Alignment.TopEnd).padding(if (compact) 12.dp else 18.dp).height(18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(0, 200, 400).forEach { d ->
            val h by inf.animateFloat(6f, 17f, infiniteRepeatable(tween(450, delayMillis = d), RepeatMode.Reverse), label = "eq$d")
            Box(Modifier.width(3.dp).height(h.dp).background(M_CYAN, RoundedCornerShape(2.dp)))
        }
    }
    Column(Modifier.align(Alignment.BottomStart).padding(if (compact) 12.dp else 18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
        Text(if (last != null) "Dernière chaîne · ${last.name}" else "Regarde la TV en direct", color = Color(0xFFA5F3FC),
            fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(now?.title ?: last?.name ?: "Toutes tes chaînes", color = M_TXT, fontSize = if (compact) 20.sp else 28.sp, lineHeight = if (compact) 22.sp else 30.sp,
            fontWeight = FontWeight.ExtraBold, fontFamily = IrFonts.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        now?.let { p ->
            val t = System.currentTimeMillis()
            val prog = ((t - p.start).toFloat() / (p.end - p.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(hhmm(p.start), color = Color(0xFFC9C5DC), fontSize = 9.sp)
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(prog).background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), M_CYAN))))
                }
                Text(hhmm(p.end), color = Color(0xFFC9C5DC), fontSize = 9.sp)
            }
        }
        Row(
            Modifier.padding(top = 3.dp).height(if (compact) 26.dp else 32.dp).background(Color.White, RoundedCornerShape(50)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("▶", color = Color(0xFF0B0918), fontSize = 10.sp)
            Text(if (last != null) "Reprendre le direct" else "Ouvrir le direct", color = Color(0xFF0B0918), fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
        }
    }
}

/** Trois affiches en éventail qui bougent doucement. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PosterFan(posters: List<Pair<String, String>>, compact: Boolean) {
    val inf = rememberInfiniteTransition(label = "fan")
    val a by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "fanA")
    val w = if (compact) 42.dp else 60.dp
    val h = if (compact) 62.dp else 88.dp
    Box(Modifier.align(Alignment.TopEnd).padding(top = if (compact) 10.dp else 16.dp, end = if (compact) 10.dp else 16.dp).size(w * 2f, h + 8.dp)) {
        val p = posters + List((3 - posters.size).coerceAtLeast(0)) { "" to "" }
        FanCard(p[1], w, h, Modifier.align(Alignment.TopStart).offset(x = (-4 * a).dp).rotate(-9f - 4 * a), Color(0xFF4F72C9))
        FanCard(p[2], w, h, Modifier.align(Alignment.TopEnd).offset(x = (4 * a).dp).rotate(7f + 4 * a), Color(0xFFC27A2C))
        FanCard(p[0], w + 4.dp, h + 6.dp, Modifier.align(Alignment.TopCenter), Color(0xFFB85A68))
    }
}

@Composable
private fun FanCard(p: Pair<String, String>, w: Dp, h: Dp, modifier: Modifier, tint: Color) {
    Box(modifier.shadow(10.dp, RoundedCornerShape(6.dp)).size(w, h).clip(RoundedCornerShape(6.dp))
        .background(Brush.verticalGradient(listOf(Color(0xFF1B1040), tint)))) {
        if (p.first.isNotBlank()) NetImage(p.first, "", w, h, crop = true)
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SeriesStrips(covers: List<Pair<String, String>>, compact: Boolean) {
    val tints = listOf(listOf(Color(0xFF0B2140), Color(0xFF3C6EA8)), listOf(Color(0xFF14113A), Color(0xFF5B47B8)), listOf(Color(0xFF0A2A2E), Color(0xFF1F8C8E)))
    Column(Modifier.align(Alignment.TopEnd).padding(top = if (compact) 10.dp else 17.dp, end = if (compact) 10.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            Box(Modifier.size(if (compact) 80.dp else 115.dp, if (compact) 18.dp else 28.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(tints[i]))) {
                covers.getOrNull(i)?.let { NetImage(it.first, "", if (compact) 80.dp else 115.dp, if (compact) 18.dp else 28.dp, crop = true) }
            }
        }
    }
}

/** Grille de programmes qui défile lentement. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.GuideBars(compact: Boolean) {
    val inf = rememberInfiniteTransition(label = "guide")
    val x by inf.animateFloat(0f, -90f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "gx")
    val rows = listOf(
        listOf(110 to Color(0xFF8B5CF6), 80 to Color(0xFF2A2540), 130 to Color(0xFF2A2540)),
        listOf(65 to Color(0xFF2A2540), 140 to M_CYAN, 100 to Color(0xFF2A2540)),
        listOf(150 to Color(0xFF2A2540), 90 to Color(0xFF2A2540), 75 to Color(0xFFC084FC)),
    )
    val k = if (compact) 0.7f else 1f
    Column(Modifier.align(Alignment.TopStart).padding(start = 15.dp, top = if (compact) 10.dp else 18.dp).offset(x = x.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                r.forEach { (w, c) -> Box(Modifier.size((w * k).dp, (19 * k).dp).background(c, RoundedCornerShape(4.dp))) }
            }
        }
    }
}

@Composable
private fun SmallTileRow(icon: IrIcon, tint: Color, title: String, sub: String, compact: Boolean) {
    Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IrIconView(icon, tint, if (compact) 16.dp else 20.dp)
        Column {
            Text(title, color = M_TXT, fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
            Text(sub, color = Color(0xFFCFC9EA), fontSize = if (compact) 8.sp else 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TopIcon(icon: IrIcon, label: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(if (f) Color.White else Color(0x1AFFFFFF))
            .border(if (f) 2.dp else 0.dp, M_CYAN, CircleShape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) { IrIconView(icon, if (f) Color(0xFF0B0918) else M_TXT, 13.dp) }
}

@Composable
private fun TopAvatar(initial: String, color: Int, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val grads = listOf(
        listOf(Color(0xFF8B5CF6), Color(0xFF3B1C8C)), listOf(Color(0xFF22D3EE), Color(0xFF0E4A66)),
        listOf(Color(0xFF6B6785), Color(0xFF24222F)), listOf(Color(0xFFC084FC), Color(0xFF22D3EE)),
    )
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(grads[color.mod(grads.size)]))
            .border(if (f) 2.dp else 0.dp, M_CYAN, RoundedCornerShape(6.dp))
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(initial, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title) }
}
