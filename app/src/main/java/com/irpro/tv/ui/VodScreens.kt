package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.Category
import com.irpro.tv.data.Details
import com.irpro.tv.data.EpisodeItem
import com.irpro.tv.data.Movie
import com.irpro.tv.data.MovieDetail
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.Series
import com.irpro.tv.data.SeriesDetail
import com.irpro.tv.data.UserData
import kotlinx.coroutines.delay

/** Une carte affichable (film ou série). */
data class Poster(val id: String, val title: String, val image: String, val sub: String = "", val progress: Float? = null)

private fun Movie.poster(fr: Boolean) = Poster(id, name, poster, if (fr) "FR" else "")
private fun Series.poster(fr: Boolean) = Poster(id, name, cover, if (fr) "FR" else "")

data class Shelf(val title: String, val items: List<Poster>, val categoryId: String? = null, val total: Int = items.size)

/** Construit les rangées : Reprendre, Ma liste, Français, Nouveautés, puis chaque catégorie (françaises d'abord). */
fun buildShelves(playlist: Playlist, catalog: Catalog, series: Boolean): List<Shelf> {
    val cats: List<Category> = if (series) catalog.seriesCategories else catalog.movieCategories
    val frCats = cats.filter { it.isFrench }.map { it.id }.toHashSet()
    val all: List<Pair<Poster, String>> = if (series) catalog.series.map { it.poster(it.categoryId in frCats) to it.categoryId }
    else catalog.movies.map { it.poster(it.categoryId in frCats) to it.categoryId }
    val byId = all.associateBy({ it.first.id }, { it.first })
    val out = ArrayList<Shelf>()

    val resumes = UserData.resumes(playlist.id).filter { if (series) it.kind == "episode" else it.kind == "movie" }
    val resumeItems = resumes.mapNotNull { r ->
        val key = if (series) r.parentId else r.id
        byId[key]?.copy(sub = r.title, progress = if (r.dur > 0) r.pos.toFloat() / r.dur else null)
    }.distinctBy { it.id }
    if (resumeItems.isNotEmpty()) out += Shelf("Reprendre", resumeItems)
    val mine = UserData.myList(playlist.id).mapNotNull { byId[it] }
    if (mine.isNotEmpty()) out += Shelf("Ma liste", mine)
    val fr = all.filter { it.second in frCats }.map { it.first }
    if (fr.isNotEmpty()) out += Shelf(if (series) "Séries françaises" else "Films français", fr.take(60), "__fr", fr.size)
    if (!series) {
        val newest = catalog.movies.filter { it.added > 0 }.sortedWith(compareByDescending<Movie> { it.categoryId in frCats }.thenByDescending { it.added })
            .take(60).map { it.poster(it.categoryId in frCats) }
        if (newest.isNotEmpty()) out += Shelf("Nouveautés · VF d'abord", newest)
    }
    val grouped = all.groupBy({ it.second }, { it.first })
    cats.forEach { c -> grouped[c.id]?.let { l -> out += Shelf(c.name, l.take(40), c.id, l.size) } }
    return out
}

/* ---------------- Catalogue (films ou séries) ---------------- */

@Composable
fun VodCatalogScreen(
    playlist: Playlist,
    catalog: Catalog,
    series: Boolean,
    onOpen: (String) -> Unit,
    onSeeAll: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    // Rangées calculées hors de l'écran (rapide même avec des dizaines de milliers de titres)
    val shelves by androidx.compose.runtime.produceState(emptyList<Shelf>(), catalog, rev, series) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { buildShelves(playlist, catalog, series) }
    }
    var hero by remember { mutableStateOf<Poster?>(null) }
    val first = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(shelves) {
        if (hero == null) hero = shelves.firstOrNull()?.items?.firstOrNull()
        if (!focusedOnce && shelves.isNotEmpty()) { delay(60); runCatching { first.requestFocus() }; focusedOnce = true }
    }
    val heroSeries = remember(hero) { if (series) catalog.series.firstOrNull { it.id == hero?.id } else null }

    Box(Modifier.fillMaxSize().background(IrColors.Night)) {
        // Fond : affiche de l'élément sélectionné, en grand à droite
        hero?.let {
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.55f).fillMaxHeight(0.62f)) {
                NetImage(it.image, "", 2000.dp, 2000.dp, Modifier.fillMaxSize(), crop = true)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(IrColors.Night, Color(0x660A1430), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x330A1430), IrColors.Night))))
            }
        }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 40.dp, top = 24.dp, end = 40.dp).fillMaxWidth(0.5f).height(170.dp)) {
                Text(if (series) "Séries" else "Films", color = IrColors.Cyan, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(hero?.title ?: "", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!hero?.sub.isNullOrBlank() && hero?.sub != "FR") Text(hero?.sub ?: "", color = IrColors.TextDim, fontSize = 14.sp, maxLines = 1)
                heroSeries?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFCCD2E0), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(4.dp))
                Text("OK : ouvrir la fiche", color = IrColors.TextDim, fontSize = 12.sp)
            }
            if (shelves.isEmpty()) Text("Aucun contenu dans cette playlist.", color = Color.White, modifier = Modifier.padding(40.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                itemsIndexed(shelves, key = { i, s -> "$i:${s.title}" }) { si, shelf ->
                    Column(Modifier.padding(top = 10.dp)) {
                        Row(Modifier.padding(start = 40.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (shelf.items.firstOrNull()?.sub == "FR" && shelf.categoryId != null) Text("●  ", color = IrColors.Cyan, fontSize = 13.sp)
                            Text(shelf.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            if (shelf.total > shelf.items.size) Text("  · ${formatCount(shelf.total)}", color = IrColors.TextDim, fontSize = 13.sp)
                        }
                        LazyRow(contentPadding = PaddingValues(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(shelf.items.distinctBy { it.id }, key = { _, p -> p.id }) { i, p ->
                                PosterCard(p, if (si == 0 && i == 0) Modifier.focusRequester(first) else Modifier, onFocus = { hero = p }) { onOpen(p.id) }
                            }
                            if (shelf.categoryId != null && shelf.total > shelf.items.size) item {
                                SeeAllCard(shelf.total) { onSeeAll(shelf.categoryId) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(p: Poster, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (f) 1.08f else 1f, label = "poster")
    Column(modifier.width(128.dp)) {
        Box(
            Modifier.scale(sc).size(128.dp, 186.dp).clip(RoundedCornerShape(10.dp))
                .border(if (f) 3.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(10.dp))
                .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) onFocus() }
                .clickable { Sfx.select(); onClick() }
        ) {
            NetImage(p.image, p.title.take(14), 128.dp, 186.dp, crop = true)
            if (p.sub == "FR") Text("FR", color = IrColors.CyanDark, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(5.dp).background(IrColors.Cyan, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
            p.progress?.let {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp), color = IrColors.Cyan, trackColor = Color(0x991D2B55))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(p.title, color = if (f) Color.White else Color(0xFFCCD2E0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeeAllCard(total: Int, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.size(128.dp, 186.dp).clip(RoundedCornerShape(10.dp))
            .background(if (f) IrColors.Violet else IrColors.Card)
            .border(if (f) 3.dp else 1.dp, if (f) IrColors.Cyan else Color(0x22FFFFFF), RoundedCornerShape(10.dp))
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text("Tout voir\n${formatCount(total)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

/* ---------------- Grille d'une catégorie ---------------- */

@Composable
fun VodGridScreen(playlist: Playlist, catalog: Catalog, series: Boolean, categoryId: String, onOpen: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val cats = if (series) catalog.seriesCategories else catalog.movieCategories
    val frCats = remember(catalog) { cats.filter { it.isFrench }.map { it.id }.toHashSet() }
    val title = if (categoryId == "__fr") (if (series) "Séries françaises" else "Films français") else cats.firstOrNull { it.id == categoryId }?.name ?: ""
    val items = remember(categoryId, catalog) {
        val inCat: (String) -> Boolean = { if (categoryId == "__fr") it in frCats else it == categoryId }
        if (series) catalog.series.filter { inCat(it.categoryId) }.map { it.poster(it.categoryId in frCats) }
        else catalog.movies.filter { inCat(it.categoryId) }.map { it.poster(it.categoryId in frCats) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().background(IrColors.Night).padding(start = 36.dp, end = 36.dp, top = 22.dp)) {
        Text("$title · ${formatCount(items.size)}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(GridCells.Adaptive(140.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 40.dp)) {
            itemsIndexed(items.distinctBy { it.id }, key = { _, p -> p.id }) { i, p ->
                PosterCard(p, if (i == 0) Modifier.focusRequester(first) else Modifier) { onOpen(p.id) }
            }
        }
    }
}

/* ---------------- Fiche film ---------------- */

@Composable
fun MovieDetailScreen(playlist: Playlist, movie: Movie, onPlay: (Long) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    var d by remember { mutableStateOf<MovieDetail?>(null) }
    LaunchedEffect(movie.id) { d = Details.movie(playlist, movie) }
    val resume = remember(rev) { UserData.resumeOf(playlist.id, movie.id) }
    val inList = remember(rev) { movie.id in UserData.myList(playlist.id) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }

    DetailFrame(backdrop = d?.backdrop?.ifBlank { movie.poster } ?: movie.poster) {
        Row {
            NetImage(movie.poster, movie.name.take(14), 190.dp, 280.dp, crop = true)
            Spacer(Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                Text(movie.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(d?.year?.ifBlank { null }, d?.genre?.ifBlank { null }, d?.duration?.ifBlank { null },
                    (d?.rating ?: movie.rating).ifBlank { null }?.let { "★ $it" }).joinToString(" · ")
                Text(meta, color = IrColors.Cyan, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                if (d == null) CircularProgressIndicator(color = IrColors.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(d?.plot?.ifBlank { null } ?: if (d != null) "Pas de résumé fourni." else "", color = Color(0xFFD5DBEA), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                d?.cast?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(8.dp)); Text("Avec : $it", color = IrColors.TextDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (resume != null) {
                        IrButton("▶  Reprendre à ${clockTime(resume.pos)}", modifier = Modifier.focusRequester(first)) { onPlay(resume.pos) }
                        IrButton("Recommencer", primary = false) { onPlay(0) }
                    } else IrButton("▶  Regarder", modifier = Modifier.focusRequester(first)) { onPlay(0) }
                    IrButton(if (inList) "✓ Dans ma liste" else "+ Ma liste", primary = false) { UserData.toggleMyList(playlist.id, movie.id) }
                }
            }
        }
    }
}

/* ---------------- Fiche série ---------------- */

@Composable
fun SeriesDetailScreen(
    playlist: Playlist,
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
    val first = remember { FocusRequester() }
    val listBtn = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { listBtn.requestFocus() } }      // pendant le chargement
    LaunchedEffect(det) { if (det != null && det.episodes.isNotEmpty()) { delay(60); runCatching { first.requestFocus() } } }

    DetailFrame(backdrop = det?.let { it.backdrop.ifBlank { it.cover } }?.ifBlank { null } ?: series.cover) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                Row {
                    NetImage(det?.cover?.ifBlank { series.cover } ?: series.cover, series.name.take(14), 130.dp, 190.dp, crop = true)
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(series.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(det?.year?.ifBlank { null }, det?.genre?.ifBlank { null },
                            (det?.rating ?: series.rating).ifBlank { null }?.let { "★ $it" }, det?.let { "${it.seasons.size} saison(s)" }).joinToString(" · ")
                        Text(meta, color = IrColors.Cyan, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(det?.plot?.ifBlank { null } ?: series.plot, color = Color(0xFFD5DBEA), fontSize = 14.sp, lineHeight = 21.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                if (det == null) CircularProgressIndicator(color = IrColors.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val resIdx = eps.indexOfFirst { it.id == resume?.id }
                    if (resIdx >= 0) {
                        val e = eps[resIdx]
                        IrButton("▶  Reprendre S${e.season} · E${e.number}", modifier = Modifier.focusRequester(first)) { onPlay(eps, resIdx, resume?.pos) }
                    } else if (eps.isNotEmpty()) {
                        val nextUnseen = eps.indexOfFirst { it.id !in watched }.coerceAtLeast(0)
                        val e = eps[nextUnseen]
                        IrButton("▶  Regarder S${e.season} · E${e.number}", modifier = Modifier.focusRequester(first)) { onPlay(eps, nextUnseen, null) }
                    }
                    IrButton(if (inList) "✓ Ma liste" else "+ Ma liste", primary = false, modifier = Modifier.focusRequester(listBtn)) { UserData.toggleMyList(playlist.id, series.id) }
                }
                Spacer(Modifier.height(16.dp))
                if (det != null && det.seasons.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(det.seasons) { sn ->
                        var f by remember { mutableStateOf(false) }
                        Text("Saison $sn", color = Color.White, fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (sn == season) IrColors.Violet else IrColors.Card)
                                .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(16.dp))
                                .onFocusChanged { f = it.isFocused; if (it.isFocused) season = sn }
                                .clickable { season = sn }
                                .padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            // Épisodes de la saison
            LazyColumn(
                Modifier.weight(1.1f).fillMaxHeight().background(Color(0x99050A1C), RoundedCornerShape(14.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val list = eps.withIndex().filter { it.value.season == season }
                if (det != null && list.isEmpty()) item { Text("Aucun épisode fourni par le serveur.", color = IrColors.TextDim, modifier = Modifier.padding(12.dp)) }
                items(list.distinctBy { it.value.id }, key = { it.value.id }) { (idx, e) ->
                    var f by remember { mutableStateOf(false) }
                    val r = remember(rev) { UserData.resumeOf(playlist.id, e.id) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (f) IrColors.Violet else Color.Transparent)
                            .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(10.dp))
                            .onFocusChanged { f = it.isFocused }
                            .clickable { onPlay(eps, idx, null) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            NetImage(e.image, "E${e.number}", 104.dp, 58.dp, crop = true)
                            r?.let { LinearProgressIndicator(progress = { if (it.dur > 0) it.pos.toFloat() / it.dur else 0f }, modifier = Modifier.align(Alignment.BottomCenter).width(104.dp).height(3.dp), color = IrColors.Cyan, trackColor = Color(0x991D2B55)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("E${e.number} · ${e.title}", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val info = listOfNotNull(e.duration.ifBlank { null }, r?.let { "${clockTime((it.dur - it.pos).coerceAtLeast(0))} restantes" }).joinToString(" · ")
                            if (info.isNotBlank()) Text(info, color = if (f) Color(0xFFE4DCFF) else IrColors.TextDim, fontSize = 12.sp)
                        }
                        if (e.id in watched) Text("✓", color = IrColors.Cyan, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailFrame(backdrop: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(IrColors.Night)) {
        if (backdrop.isNotBlank()) {
            Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.6f).fillMaxHeight(0.7f)) {
                NetImage(backdrop, "", 2000.dp, 2000.dp, Modifier.fillMaxSize(), crop = true)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(IrColors.Night, Color(0xAA0A1430), Color(0x550A1430)))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, IrColors.Night))))
            }
        }
        Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp)) { content() }
    }
}
