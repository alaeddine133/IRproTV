package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.irpro.tv.BuildConfig
import com.irpro.tv.R
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.Channel
import com.irpro.tv.data.Details
import com.irpro.tv.data.Epg
import com.irpro.tv.data.EpisodeItem
import com.irpro.tv.data.Movie
import com.irpro.tv.data.MovieDetail
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.Series
import com.irpro.tv.data.SeriesDetail
import com.irpro.tv.data.UpdateInfo
import com.irpro.tv.data.Updater
import com.irpro.tv.data.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Thème 2 · Pro — écrans fidèles à la maquette « IR PRO TV – Thème 2 · Pro ».
 * Maquette en 1920×1080 px ; ici 1 px de maquette = 0,5 dp.
 */

private object P {
    val Bg = Color(0xFF07060D)
    val Card = Color(0xFF14121F)
    val Dialog = Color(0xFF12101D)
    val Key = Color(0xFF1C1930)
    val Text = Color(0xFFF5F3FF)
    val Soft = Color(0xFFE4E1F0)
    val Dim = Color(0xFFB4B0C8)
    val Dot = Color(0xFF8E89A8)
    val Violet = Color(0xFF8B5CF6)
    val VioletLight = Color(0xFFA78BFA)
    val Cyan = Color(0xFF22D3EE)
    val CyanText = Color(0xFF67E8F9)
    val Ink = Color(0xFF0B0918)
    val Grad = Brush.horizontalGradient(listOf(Violet, Cyan))
}

/** Anneau cyan des éléments sélectionnés (comme dans la maquette). */
private fun Modifier.proRing(on: Boolean, shape: androidx.compose.ui.graphics.Shape, gap: Color = P.Bg): Modifier =
    if (!on) this else this
        .shadow(18.dp, shape, ambientColor = P.Cyan, spotColor = P.Cyan)
        .border(4.dp, P.Cyan, shape)
        .padding(2.dp)
        .border(2.dp, gap, shape)

@Composable
private fun ProLogo(size: Dp = 26.dp) {
    Image(painterResource(R.drawable.logo), contentDescription = "IR PRO TV", modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
}

@Composable
private fun rememberClock(): String {
    var c by remember { mutableStateOf(hhmm(System.currentTimeMillis())) }
    LaunchedEffect(Unit) { while (true) { c = hhmm(System.currentTimeMillis()); delay(15_000) } }
    return c
}

/** Barre du haut : logo, nom ou titre, fil d'Ariane, heure. */
@Composable
private fun ProTopBar(title: String? = null, crumb: String = "") {
    val clock = rememberClock()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ProLogo()
        Spacer(Modifier.width(9.dp))
        if (title != null) Text(title, color = P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
        else Text("IR PRO TV", color = P.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title, letterSpacing = 1.sp)
        if (crumb.isNotBlank()) Text(crumb, color = P.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 18.dp).weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        Text(clock, color = P.Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private enum class ChipKind { ACCENT, PLAIN, FRENCH }

@Composable
private fun ProChip(text: String, kind: ChipKind = ChipKind.PLAIN) {
    val shape = RoundedCornerShape(5.dp)
    val m = when (kind) {
        ChipKind.ACCENT -> Modifier.background(Color(0x388B5CF6), shape).border(1.dp, Color(0x99A78BFA), shape)
        ChipKind.FRENCH -> Modifier.background(Color(0x2922D3EE), shape).border(1.dp, Color(0x9922D3EE), shape)
        ChipKind.PLAIN -> Modifier.background(Color(0x14FFFFFF), shape)
    }
    Text(text, color = if (kind == ChipKind.FRENCH) Color(0xFFA5F3FC) else P.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = m.padding(horizontal = 8.dp, vertical = 4.dp))
}

/** Bouton « pilule » : blanc pour l'action principale, translucide sinon ; anneau cyan quand il est sélectionné. */
@Composable
private fun ProButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    height: Dp = 42.dp,
    fontSize: TextUnit = 15.sp,
    play: Boolean = false,
    gap: Color = P.Bg,
    onClick: () -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val white = primary || f
    Row(
        modifier
            .proRing(f, shape, gap)
            .height(height).clip(shape)
            .background(if (white) Color.White else Color(0x14FFFFFF))
            .border(if (white) 0.dp else 1.dp, if (white) Color.Transparent else Color(0x59FFFFFF), shape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (play) Text("▶", color = if (white) P.Ink else P.Text, fontSize = fontSize)
        Text(text, color = if (white) P.Ink else P.Text, fontSize = fontSize, fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold, fontFamily = IrFonts.title, maxLines = 1)
    }
}

/** Bouton rond « + / ✓ » (Ma liste). */
@Composable
private fun ProRoundButton(symbol: String, size: Dp = 42.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier.proRing(f, CircleShape).size(size).clip(CircleShape)
            .background(if (f) Color.White else Color(0x14FFFFFF))
            .border(if (f) 0.dp else 1.dp, Color(0x59FFFFFF), CircleShape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = if (f) P.Ink else P.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

/** Pilule d'onglet (saisons, filtres) : dégradé violet→cyan quand elle est active. */
@Composable
private fun ProTab(text: String, active: Boolean, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier.proRing(f, shape).height(30.dp).clip(shape)
            .then(if (active) Modifier.background(P.Grad) else Modifier.border(1.dp, Color(0x40FFFFFF), shape))
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) onFocus() }
            .clickable { Sfx.select(); onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (active) P.Bg else Color(0xFFD6D3E4), fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold, fontFamily = IrFonts.title)
    }
}

@Composable
private fun ProgressLine(value: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(P.Grad))
    }
}

/** Grande image en haut à droite, fondue dans le fond noir (fiches film et série). */
@Composable
private fun BoxScope.ProBackdrop(url: String, widthFrac: Float, heightDp: Dp, fadeStart: Float) {
    if (url.isBlank()) {
        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(widthFrac).height(heightDp)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1040), Color(0xFF6B2A6E), Color(0xFF3A1F4F)))))
    } else {
        NetImage(url, "", 2000.dp, 2000.dp, Modifier.align(Alignment.TopEnd).fillMaxWidth(widthFrac).height(heightDp), crop = true)
    }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to P.Bg, fadeStart to P.Bg, (fadeStart + 0.14f) to Color(0xD907060D), (fadeStart + 0.38f) to Color(0x0007060D))))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Transparent, 0.62f to P.Bg)))
}

private fun langOf(name: String): String? {
    val n = name.uppercase()
    return when {
        Regex("""\bVOSTFR\b|\bVOST\b""").containsMatchIn(n) -> "VOSTFR"
        Regex("""\bMULTI\b""").containsMatchIn(n) -> "MULTI"
        Regex("""\bVF\b|\bFRENCH\b|\bTRUEFRENCH\b|^FR\b|\[FR]""").containsMatchIn(n) -> "VF"
        else -> null
    }
}

private fun qualityOf(name: String): String? {
    val n = name.uppercase()
    return when {
        Regex("""\b(4K|UHD|2160P?)\b""").containsMatchIn(n) -> "4K"
        Regex("""\b(FHD|1080P?)\b""").containsMatchIn(n) -> "FHD"
        Regex("""\b(HD|720P?)\b""").containsMatchIn(n) -> "HD"
        else -> null
    }
}

/** Durée lisible : 6720000 ms → « 1 h 52 » ; 1500000 → « 25 min ». */
private fun humanDur(ms: Long): String {
    val m = (ms / 60_000).coerceAtLeast(0)
    return if (m >= 60) "${m / 60} h ${String.format(java.util.Locale.FRANCE, "%02d", m % 60)}" else "$m min"
}

/** « 01:52:00 », « 112 min », « 6720 » → « 1 h 52 » quand c'est possible. */
private fun prettyDuration(raw: String): String {
    val s = raw.trim()
    if (s.isBlank()) return ""
    Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?$""").matchEntire(s)?.let { m ->
        val a = m.groupValues[1].toLong(); val b = m.groupValues[2].toLong()
        return humanDur(if (m.groupValues[3].isNotEmpty()) (a * 60 + b) * 60_000 else a * 60_000 + b * 1000)
    }
    Regex("""^(\d+)\s*(min|mn)?$""", RegexOption.IGNORE_CASE).matchEntire(s)?.let { return humanDur(it.groupValues[1].toLong() * 60_000) }
    return s
}

/* ======================= Fiche film ======================= */

@Composable
fun ProMovieDetailScreen(playlist: Playlist, catalog: Catalog, movie: Movie, onPlay: (Long) -> Unit, onOpen: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    var d by remember { mutableStateOf<MovieDetail?>(null) }
    LaunchedEffect(movie.id) { d = Details.movie(playlist, movie) }
    val resume = remember(rev) { UserData.resumeOf(playlist.id, movie.id) }
    val inList = remember(rev) { movie.id in UserData.myList(playlist.id) }
    val cat = remember(catalog) { catalog.movieCategories.firstOrNull { it.id == movie.categoryId } }
    val related = remember(catalog, movie.id) { catalog.movies.asSequence().filter { it.categoryId == movie.categoryId && it.id != movie.id }.distinctBy { it.id }.take(14).toList() }
    val first = remember(movie.id) { FocusRequester() }
    LaunchedEffect(movie.id) { delay(60); runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(P.Bg)) {
        ProBackdrop(d?.backdrop?.ifBlank { null } ?: movie.poster, 0.72f, 380.dp, 0.34f)
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 30.dp)) {
            ProTopBar(crumb = listOfNotNull("Films", cat?.name).joinToString("  ›  "))
            Column(Modifier.padding(top = 20.dp).width(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProChip("FILM", ChipKind.ACCENT)
                    langOf(movie.name)?.let { ProChip(it) }
                    qualityOf(movie.name)?.let { ProChip(it) }
                    if (cat?.isFrench == true) ProChip("Contenu français", ChipKind.FRENCH)
                }
                Text(movie.name.uppercase(), color = P.Text, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.5).sp)
                val meta = listOfNotNull(d?.genre?.split(',', '/')?.firstOrNull()?.trim()?.ifBlank { null }, d?.year?.take(4)?.ifBlank { null },
                    d?.duration?.let { prettyDuration(it) }?.ifBlank { null }, (d?.rating ?: movie.rating).ifBlank { null }?.let { "★ $it" })
                if (meta.isNotEmpty()) MetaRow(meta)
                if (d == null) CircularProgressIndicator(color = P.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Text(d?.plot?.ifBlank { null } ?: "Pas de résumé fourni par ta playlist.", color = P.Dim, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (resume != null) {
                        ProButton("Reprendre · ${clockTime(resume.pos)}", Modifier.focusRequester(first), primary = true, play = true) { onPlay(resume.pos) }
                        ProButton("Depuis le début", fontSize = 13.sp) { onPlay(0) }
                    } else ProButton("Regarder", Modifier.focusRequester(first), primary = true, play = true) { onPlay(0) }
                    ProRoundButton(if (inList) "✓" else "+") { UserData.toggleMyList(playlist.id, movie.id) }
                }
                if (resume != null && resume.dur > 0) ProgressLine(resume.pos.toFloat() / resume.dur, Modifier.width(280.dp), 4.dp)
            }
            Spacer(Modifier.weight(1f))
            if (related.isNotEmpty()) {
                Text("Dans la même catégorie", color = P.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    items(related, key = { it.id }) { m -> TitleTile(m.name, m.poster, 95.dp, 80.dp) { onOpen(m.id) } }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(parts: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEachIndexed { i, s ->
            if (i > 0) Text("·", color = P.Dot, fontSize = 13.sp)
            Text(s, color = P.Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Vignette avec image et titre en majuscules par-dessus (rangées « même catégorie », favoris). */
@Composable
private fun TitleTile(title: String, image: String, w: Dp, h: Dp, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.proRing(f, shape).size(w, h).clip(shape).background(P.Card)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() }
    ) {
        if (image.isNotBlank()) NetImage(image, "", w, h, crop = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE607060D))))
        Text(title.uppercase(), color = P.Text, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(7.dp))
    }
}

/* ======================= Fiche série ======================= */

@Composable
fun ProSeriesDetailScreen(
    playlist: Playlist,
    catalog: Catalog,
    series: Series,
    onPlay: (List<EpisodeItem>, Int, Long?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    var d by remember { mutableStateOf<SeriesDetail?>(null) }
    LaunchedEffect(series.id) { d = Details.series(playlist, series) }
    val det = d
    val eps = det?.episodes.orEmpty()
    val watched = remember(rev) { UserData.watched(playlist.id) }
    val resume = remember(rev, det) { UserData.resumes(playlist.id).firstOrNull { it.kind == "episode" && it.parentId == series.id } }
    var season by remember(det) { mutableIntStateOf(eps.firstOrNull { it.id == resume?.id }?.season ?: det?.seasons?.firstOrNull() ?: 1) }
    val inList = remember(rev) { series.id in UserData.myList(playlist.id) }
    val cat = remember(catalog) { catalog.seriesCategories.firstOrNull { it.id == series.categoryId } }
    val first = remember(series.id) { FocusRequester() }
    val listBtn = remember(series.id) { FocusRequester() }
    LaunchedEffect(series.id) { delay(60); runCatching { listBtn.requestFocus() } }
    LaunchedEffect(det) { if (det != null && det.episodes.isNotEmpty()) { delay(60); runCatching { first.requestFocus() } } }

    Box(Modifier.fillMaxSize().background(P.Bg)) {
        ProBackdrop(det?.let { it.backdrop.ifBlank { it.cover } }?.ifBlank { null } ?: series.cover, 0.68f, 320.dp, 0.30f)
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 30.dp)) {
            ProTopBar(crumb = listOfNotNull("Séries", cat?.name).joinToString("  ›  "))
            Column(Modifier.padding(top = 30.dp).width(450.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProChip("SÉRIE", ChipKind.ACCENT)
                    langOf(series.name)?.let { ProChip(it) }
                    det?.seasons?.size?.takeIf { it > 0 }?.let { ProChip(if (it > 1) "$it saisons" else "1 saison") }
                    if (cat?.isFrench == true) ProChip("Contenu français", ChipKind.FRENCH)
                }
                Text(series.name.uppercase(), color = P.Text, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.5).sp)
                Text(det?.plot?.ifBlank { null } ?: series.plot.ifBlank { if (det != null) "Pas de résumé fourni par ta playlist." else "" },
                    color = P.Dim, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val resIdx = eps.indexOfFirst { it.id == resume?.id }
                    if (resIdx >= 0) {
                        val e = eps[resIdx]
                        ProButton("Reprendre S${e.season} · É${e.number}", primary = true, play = true, height = 38.dp, fontSize = 13.sp) { onPlay(eps, resIdx, resume?.pos) }
                    } else if (eps.isNotEmpty()) {
                        val nextUnseen = eps.indexOfFirst { it.id !in watched }.coerceAtLeast(0)
                        val e = eps[nextUnseen]
                        ProButton("Regarder S${e.season} · É${e.number}", primary = true, play = true, height = 38.dp, fontSize = 13.sp) { onPlay(eps, nextUnseen, null) }
                    } else if (det == null) CircularProgressIndicator(color = P.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    ProRoundButton(if (inList) "✓" else "+", 38.dp, Modifier.focusRequester(listBtn)) { UserData.toggleMyList(playlist.id, series.id) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (det != null && det.seasons.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(4.dp)) {
                    items(det.seasons) { sn -> ProTab("Saison $sn", sn == season, onFocus = { season = sn }) { season = sn } }
                }
                Spacer(Modifier.height(10.dp))
            }
            val list = eps.withIndex().filter { it.value.season == season }.distinctBy { it.value.id }
            if (det != null && list.isEmpty()) Text("Aucun épisode fourni par le serveur.", color = P.Dim, fontSize = 13.sp)
            val focusIdx = list.indexOfFirst { it.value.id == resume?.id }.takeIf { it >= 0 }
                ?: list.indexOfFirst { it.value.id !in watched }.takeIf { it >= 0 } ?: 0
            val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(season, det) { if (list.isNotEmpty()) rowState.scrollToItem((focusIdx - 1).coerceAtLeast(0)) }
            LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(list.size, key = { list[it].value.id }) { li ->
                    val (idx, e) = list[li]
                    val r = remember(rev) { UserData.resumeOf(playlist.id, e.id) }
                    val seen = e.id in watched
                    val dur = prettyDuration(e.duration)
                    val meta = when {
                        r != null -> "En cours · reste ${humanDur(r.dur - r.pos)}"
                        seen -> listOf("Vu", dur).filter { it.isNotBlank() }.joinToString(" · ")
                        else -> dur
                    }
                    EpisodeCard(
                        badge = "É${e.number}", title = e.title.ifBlank { "Épisode ${e.number}" }, meta = meta, image = e.image.ifBlank { det?.cover ?: series.cover },
                        progress = r?.let { if (it.dur > 0) it.pos.toFloat() / it.dur else 0f } ?: if (seen) 1f else null,
                        modifier = if (li == focusIdx) Modifier.focusRequester(first) else Modifier,
                    ) { onPlay(eps, idx, null) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(badge: String, title: String, meta: String, image: String, progress: Float?, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier.proRing(f, shape).size(160.dp, 90.dp).clip(shape).background(P.Card)
                .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
                .clickable { Sfx.select(); onClick() }
        ) {
            if (image.isNotBlank()) NetImage(image, "", 160.dp, 90.dp, crop = true)
            Text(badge, color = P.Text, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(6.dp).background(Color(0xBF07060D), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            progress?.let { ProgressLine(it, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), 3.dp) }
        }
        Text(title, color = if (f) Color.White else P.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta.isNotBlank()) Text(meta, color = P.Dim, fontSize = 10.sp, maxLines = 1)
    }
}

/* ======================= Recherche ======================= */

private val KEYS = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789").map { it.toString() }

@Composable
fun ProSearchScreen(
    catalog: Catalog,
    onChannel: (String) -> Unit,
    onMovie: (String) -> Unit,
    onSeries: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var q by rememberSaveable { mutableStateOf("") }
    var recents by remember { mutableStateOf(UserData.recentSearches()) }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { firstKey.requestFocus() } }
    val index by produceState<List<List<String>>?>(null, catalog) {
        value = withContext(Dispatchers.Default) {
            listOf(catalog.channels.map { normName(it.name) }, catalog.movies.map { normName(it.name) }, catalog.series.map { normName(it.name) })
        }
    }
    var ch by remember { mutableStateOf(emptyList<Channel>()) }
    var mv by remember { mutableStateOf(emptyList<Movie>()) }
    var se by remember { mutableStateOf(emptyList<Series>()) }
    val query = normName(q.trim())
    LaunchedEffect(query, index) {
        val idx = index ?: return@LaunchedEffect
        delay(200)
        if (query.length < 2) { ch = emptyList(); mv = emptyList(); se = emptyList(); return@LaunchedEffect }
        withContext(Dispatchers.Default) {
            Triple(
                catalog.channels.filterIndexed { i, _ -> idx[0][i].contains(query) }.distinctBy { it.id }.take(30),
                catalog.movies.filterIndexed { i, _ -> idx[1][i].contains(query) }.distinctBy { it.id }.take(30),
                catalog.series.filterIndexed { i, _ -> idx[2][i].contains(query) }.distinctBy { it.id }.take(30),
            )
        }.let { (c, m, s) -> ch = c; mv = m; se = s }
    }
    fun saveRecent() { if (q.isNotBlank()) { UserData.addRecentSearch(q.trim()); recents = UserData.recentSearches() } }
    val movieCat = remember(catalog) { catalog.movieCategories.associateBy { it.id } }
    val seriesCat = remember(catalog) { catalog.seriesCategories.associateBy { it.id } }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0x388B5CF6), Color.Transparent), center = androidx.compose.ui.geometry.Offset(150f, 0f), radius = 1400f))
            .background(P.Bg)
            .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ProTopBar(title = "Recherche")
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            // Clavier
            Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Film, série ou chaîne", color = P.Dim, fontSize = 11.sp)
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(P.Card)
                    .border(1.dp, Color(0xB3A78BFA), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (q.isEmpty()) "_" else q.uppercase() + "_", color = if (q.isEmpty()) P.Dot else P.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = IrFonts.title, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                KEYS.chunked(7).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { c, k ->
                            KeyButton(k, Modifier.weight(1f), if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier) { if (q.length < 40) q += k.lowercase() }
                        }
                        repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyButton("Espace", Modifier.weight(1f), small = true) { if (q.isNotEmpty() && !q.endsWith(" ")) q += " " }
                    KeyButton("Effacer", Modifier.weight(1f), small = true) { q = q.dropLast(1) }
                    KeyButton("Tout effacer", Modifier.weight(1f), small = true) { q = "" }
                }
                Text("Astuce : tu peux aussi dicter avec le micro de ta télécommande.", color = P.Dim, fontSize = 11.sp)
            }
            // Résultats
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                if (query.length < 2) item { Text("Tape au moins 2 lettres.", color = P.Dim, fontSize = 13.sp) }
                else if (index == null) item { CircularProgressIndicator(color = P.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
                else if (ch.isEmpty() && mv.isEmpty() && se.isEmpty()) item { Text("Aucun résultat pour « $q ».", color = P.Dim, fontSize = 13.sp) }
                if (mv.isNotEmpty()) item {
                    SectionTitle("Films · ${mv.size}")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                        items(mv, key = { it.id }) { m ->
                            WideResult(highlight(m.name, query), listOfNotNull("Film", movieCat[m.categoryId]?.name, langOf(m.name)).joinToString(" · "), m.poster) { saveRecent(); onMovie(m.id) }
                        }
                    }
                }
                if (se.isNotEmpty()) item {
                    SectionTitle("Séries · ${se.size}")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                        items(se, key = { it.id }) { s ->
                            RowResult(highlight(s.name, query), listOfNotNull("Série", seriesCat[s.categoryId]?.name, langOf(s.name)).joinToString(" · "), s.cover, 75.dp, 42.dp) { saveRecent(); onSeries(s.id) }
                        }
                    }
                }
                if (ch.isNotEmpty()) item {
                    SectionTitle("Chaînes · ${ch.size}")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(6.dp)) {
                        items(ch, key = { it.id }) { c ->
                            RowResult(highlight(c.name, query), "Chaîne en direct", c.logo, 60.dp, 42.dp, fit = true) { saveRecent(); onChannel(c.id) }
                        }
                    }
                }
                if (recents.isNotEmpty()) item {
                    SectionTitle("Recherches récentes")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(4.dp)) {
                        recents.take(5).forEach { r -> RecentChip(r) { q = r } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = P.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title, modifier = Modifier.padding(bottom = 6.dp))
}

/** Met en couleur la partie du nom qui correspond à la recherche. */
private fun highlight(name: String, query: String): AnnotatedString {
    val n = normName(name)
    val i = if (query.isBlank() || n.length != name.length) -1 else n.indexOf(query)
    return buildAnnotatedString {
        if (i < 0) append(name) else {
            append(name.substring(0, i))
            withStyle(SpanStyle(color = P.CyanText)) { append(name.substring(i, i + query.length)) }
            append(name.substring(i + query.length))
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier, focusMod: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier.then(focusMod).proRing(f, shape).height(34.dp).clip(shape)
            .background(if (f) Color.White else P.Key)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (f) P.Ink else P.Text, fontSize = if (small) 11.sp else 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = if (small) IrFonts.body else IrFonts.title, maxLines = 1)
    }
}

@Composable
private fun WideResult(title: AnnotatedString, meta: String, image: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Column(Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.proRing(f, shape).size(190.dp, 107.dp).clip(shape).background(P.Card)
                .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
                .clickable { Sfx.select(); onClick() }
        ) { if (image.isNotBlank()) NetImage(image, "", 190.dp, 107.dp, crop = true) }
        Text(title, color = P.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, color = P.Dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowResult(title: AnnotatedString, meta: String, image: String, iw: Dp, ih: Dp, fit: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.proRing(f, shape).width(300.dp).clip(shape).background(if (f) Color(0xFF221E36) else P.Card)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        NetImage(image, title.text.take(3).uppercase(), iw, ih, crop = !fit)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = P.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = P.Dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentChip(text: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Text(text, color = if (f) P.Ink else P.Text, fontSize = 11.sp,
        modifier = Modifier.proRing(f, shape).clip(shape).background(if (f) Color.White else Color(0x14FFFFFF))
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

/* ======================= Ma liste ======================= */

private data class Cont(val key: String, val title: String, val meta: String, val image: String, val progress: Float, val series: Boolean, val target: String)

@Composable
fun ProMyListScreen(
    playlist: Playlist,
    catalog: Catalog,
    onMovie: (String) -> Unit,
    onSeries: (String) -> Unit,
    onChannel: (Channel) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }       // 0 Tout, 1 Films, 2 Séries, 3 Chaînes
    val moviesById = remember(catalog) { catalog.movies.associateBy { it.id } }
    val seriesById = remember(catalog) { catalog.series.associateBy { it.id } }
    val chById = remember(catalog) { catalog.channels.associateBy { it.id } }
    val resumes = remember(rev) { UserData.resumes(playlist.id) }
    val mine = remember(rev) { UserData.myList(playlist.id) }
    val favCh = remember(rev, catalog) { UserData.favorites(playlist.id).mapNotNull { chById[it] } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }

    // « Continuer à regarder » : un film = sa fiche ; un épisode = sa série (une seule fois par série)
    val cont = remember(resumes, catalog) {
        val seen = HashSet<String>()
        resumes.mapNotNull { r ->
            val left = "reste ${humanDur(r.dur - r.pos)}"
            val p = if (r.dur > 0) r.pos.toFloat() / r.dur else 0f
            if (r.kind == "episode") {
                val s = seriesById[r.parentId] ?: return@mapNotNull null
                if (!seen.add(s.id)) return@mapNotNull null
                val se = Regex("""S(\d+)\s*·\s*E(\d+)""").find(r.title)?.let { "S${it.groupValues[1]} · É${it.groupValues[2]} · " } ?: ""
                Cont("s" + s.id, s.name, se + left, s.cover, p, true, s.id)
            } else {
                val m = moviesById[r.id] ?: return@mapNotNull null
                Cont("m" + m.id, m.name, "Film · $left", m.poster, p, false, m.id)
            }
        }.take(12)
    }
    val favItems = remember(mine, catalog) {
        mine.mapNotNull { id -> moviesById[id]?.let { Triple(it.id, it.name to it.poster, false) } ?: seriesById[id]?.let { Triple(it.id, it.name to it.cover, true) } }
    }
    val showCont = cont.filter { filter == 0 || (filter == 1 && !it.series) || (filter == 2 && it.series) }.takeIf { filter != 3 }.orEmpty()
    val showFav = favItems.filter { filter == 0 || (filter == 1 && !it.third) || (filter == 2 && it.third) }.takeIf { filter != 3 }.orEmpty()
    val showCh = if (filter == 0 || filter == 3) favCh else emptyList()

    Column(
        Modifier.fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0x2422D3EE), Color.Transparent), center = androidx.compose.ui.geometry.Offset(3000f, 0f), radius = 1300f))
            .background(P.Bg)
            .padding(start = 48.dp, end = 48.dp, top = 28.dp),
    ) {
        ProTopBar(title = "Ma liste")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(4.dp)) {
            listOf("Tout", "Films", "Séries", "Chaînes").forEachIndexed { i, t ->
                ProTab(t, filter == i, if (i == 0) Modifier.focusRequester(first) else Modifier) { filter = i }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 30.dp)) {
            if (showCont.isEmpty() && showFav.isEmpty() && showCh.isEmpty()) item {
                Text("Rien ici pour l'instant. Ajoute des films et séries avec le bouton « + » de leur fiche, et des chaînes en favoris avec la touche Menu en direct.",
                    color = P.Dim, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.width(520.dp))
            }
            if (showCont.isNotEmpty()) item {
                SectionTitle("Continuer à regarder")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(6.dp)) {
                    items(showCont, key = { it.key }) { c ->
                        ContinueCard(c.title, c.meta, c.image, c.progress) { if (c.series) onSeries(c.target) else onMovie(c.target) }
                    }
                }
            }
            if (showFav.isNotEmpty()) item {
                SectionTitle("Favoris")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(6.dp)) {
                    items(showFav, key = { it.first }) { (id, info, isSeries) ->
                        TitleTile(info.first, info.second, 75.dp, 112.dp) { if (isSeries) onSeries(id) else onMovie(id) }
                    }
                }
            }
            if (showCh.isNotEmpty()) item {
                SectionTitle("Chaînes favorites")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(4.dp)) {
                    showCh.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { c -> FavChannelCard(playlist, c) { onChannel(c) } } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(title: String, meta: String, image: String, progress: Float, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.proRing(f, shape).size(200.dp, 112.dp).clip(shape).background(P.Card)
                .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
                .clickable { Sfx.select(); onClick() }
        ) {
            if (image.isNotBlank()) NetImage(image, "", 200.dp, 112.dp, crop = true)
            ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth(), 4.dp)
        }
        Text(title, color = P.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, color = P.Dim, fontSize = 10.sp, maxLines = 1)
    }
}

private val CH_GRADS = listOf(
    listOf(Color(0xFF7C3CE6), Color(0xFF2A1470)), listOf(Color(0xFF2296C8), Color(0xFF0F2850)),
    listOf(Color(0xFF9646D2), Color(0xFF1E1950)), listOf(Color(0xFF3A3A55), Color(0xFF191926)),
)

@Composable
private fun FavChannelCard(playlist: Playlist, c: Channel, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val now by produceState<String?>(null, c.id) {
        value = runCatching { Epg.forChannel(playlist, c) }.getOrNull()
            ?.firstOrNull { val t = System.currentTimeMillis(); t in it.start until it.end }?.title
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.proRing(f, shape).width(250.dp).height(50.dp).clip(shape).background(if (f) Color(0xFF221E36) else P.Card)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(CH_GRADS[(c.num.coerceAtLeast(0)) % CH_GRADS.size])), contentAlignment = Alignment.Center) {
            if (c.logo.isNotBlank()) NetImage(c.logo, if (c.num > 0) "${c.num}" else initials(c.name), 30.dp, 30.dp)
            else Text(if (c.num > 0) "${c.num}" else initials(c.name), color = P.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
        }
        Column(Modifier.weight(1f)) {
            Text(c.name, color = P.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now?.let { "En ce moment : $it" } ?: "Chaîne en direct", color = P.Dim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ======================= Mise à jour ======================= */

@Composable
fun ProUpdateDialog(info: UpdateInfo, onLater: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { f.requestFocus() } }
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(490.dp).shadow(40.dp, RoundedCornerShape(18.dp), ambientColor = P.Violet, spotColor = P.Violet)
                .clip(RoundedCornerShape(18.dp)).background(P.Dialog)
                .border(1.dp, Color(0x59A78BFA), RoundedCornerShape(18.dp))
                .padding(horizontal = 36.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProLogo(60.dp)
            Text("Nouvelle version disponible", color = P.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title, textAlign = TextAlign.Center)
            Text("IR PRO TV version ${info.versionName} · installée : ${BuildConfig.VERSION_NAME}", color = P.Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (info.notes.isNotBlank() && progress == null) Text(info.notes, color = P.Dim, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            progress?.let { p ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Téléchargement…", color = P.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${(p * 100).toInt()} %", color = P.CyanText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ProgressLine(p, Modifier.fillMaxWidth(), 7.dp)
                }
            }
            val failure by Updater.status.collectAsState()
            failure?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 12.sp, textAlign = TextAlign.Center) }
            if (failure == null) msg?.let { Text(it, color = P.CyanText, fontSize = 12.sp, textAlign = TextAlign.Center) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                ProButton(if (progress == null) "Installer maintenant" else "Téléchargement…", Modifier.focusRequester(f), primary = true, fontSize = 14.sp, gap = P.Dialog) {
                    if (progress != null) return@ProButton
                    if (!Updater.canInstall(ctx)) {
                        msg = "Autorise IR PRO TV à installer des applis (une seule fois), puis reviens et appuie sur Installer."
                        Updater.openInstallPermission(ctx)
                        return@ProButton
                    }
                    scope.launch {
                        progress = 0f
                        runCatching { Updater.downloadAndInstall(ctx, info) { p -> progress = p } }
                            .onSuccess { progress = null; msg = "Appuie sur « Installer » dans la fenêtre d'Android." }
                            .onFailure {
                                // Ancienne méthode en secours (fichier téléchargé puis installateur classique)
                                runCatching { Updater.download(ctx, info) { p -> progress = p } }
                                    .onSuccess { f -> progress = null; msg = if (Updater.install(ctx, f)) "Appuie sur « Installer » dans la fenêtre d'Android." else "Impossible d'ouvrir l'installateur d'Android sur cette box." }
                                    .onFailure { progress = null; msg = "Téléchargement impossible. Vérifie la connexion et réessaie." }
                            }
                    }
                }
                ProButton("Plus tard", fontSize = 13.sp, gap = P.Dialog, onClick = onLater)
            }
            Text("Tes playlists, ta liste et tes réglages sont conservés.", color = Color(0xFFA29DBB), fontSize = 10.sp)
        }
    }
}

/* ======================= Erreur de lecture ======================= */

/**
 * Carte d'erreur de lecture du Thème 2. Pas de boutons à sélectionner (la télécommande pilote le lecteur) :
 * on affiche les touches à utiliser, et on réessaie tout seul une fois après 5 secondes.
 */
@Composable
fun ProPlaybackError(
    title: String,
    subtitle: String,
    live: Boolean,
    detail: String,
    autoRetryIn: Int?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color(0xC007060D))) {
        if (title.isNotBlank()) Column(Modifier.padding(start = 48.dp, top = 28.dp)) {
            Text(title, color = P.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
            if (subtitle.isNotBlank()) Text(subtitle, color = P.Dim, fontSize = 11.sp)
        }
        Column(
            Modifier.align(Alignment.Center).width(480.dp).clip(RoundedCornerShape(18.dp)).background(P.Dialog)
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 36.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(Modifier.size(48.dp).border(2.dp, Color(0xFFFBBF24), CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = Color(0xFFFBBF24), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (live) "La chaîne ne répond pas" else "La vidéo ne répond pas", color = P.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title, textAlign = TextAlign.Center)
            Text("Le flux est peut-être coupé par ton fournisseur, ou ta connexion est instable. Le lecteur de secours a déjà été essayé.",
                color = P.Dim, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
            if (detail.isNotBlank()) Text(detail, color = Color(0xFF7D7896), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (autoRetryIn != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("↻", color = P.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Nouvelle tentative automatique dans $autoRetryIn s", color = Color(0xFFCFFAFE), fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                KeyHint("OK", "Réessayer", true)
                if (live) { KeyHint("▼", "Chaîne suivante"); KeyHint("◀", "Guide TV") } else KeyHint("Retour", "Quitter")
            }
        }
    }
}

@Composable
private fun KeyHint(key: String, label: String, main: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp).clip(shape).background(if (main) Color.White else Color(0x0FFFFFFF))
            .border(if (main) 0.dp else 1.dp, Color(0x4DFFFFFF), shape).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(key, color = if (main) P.Ink else P.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.border(1.dp, if (main) Color(0x330B0918) else Color(0x6622D3EE), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
        Text(label, color = if (main) P.Ink else P.Text, fontSize = 12.sp, fontWeight = if (main) FontWeight.Bold else FontWeight.SemiBold, fontFamily = IrFonts.title)
    }
}
