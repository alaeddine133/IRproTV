package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.Channel
import com.irpro.tv.data.Epg
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.Program
import com.irpro.tv.data.UserData
import com.irpro.tv.player.TrackOpt
import com.irpro.tv.player.VideoSurface
import com.irpro.tv.player.rememberPlayback
import kotlinx.coroutines.delay

private fun isCenter(k: Key) = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter

/* =====================================================================
 *  LECTEUR TV EN DIRECT : bandeau, zapping haut/bas, liste rapide (OK)
 * ===================================================================== */
@Composable
fun LivePlayerScreen(
    playlist: Playlist,
    catalog: Catalog,
    startChannel: Channel,
    categoryId: String,
    onGuide: (Channel, String) -> Unit,
    onExit: (Channel, String) -> Unit,
    pb: com.irpro.tv.player.Playback = rememberPlayback(),
    /** OK : retour au menu Direct (catégories / bouquets), la chaîne continue dans l'aperçu. */
    onMenu: (Channel, String) -> Unit = onExit,
) {
    var catId by remember { mutableStateOf(categoryId) }
    var list by remember { mutableStateOf(channelsFor(catalog, playlist, catId).ifEmpty { listOf(startChannel) }) }
    var current by remember { mutableStateOf(startChannel) }
    var banner by remember { mutableIntStateOf(1) }       // change = réaffiche le bandeau
    var showBanner by remember { mutableStateOf(true) }
    var quick by remember { mutableStateOf(false) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var clock by remember { mutableStateOf(hhmm(System.currentTimeMillis())) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(current.id) {
        UserData.addRecent(playlist.id, current.id)
        pb.playIfNew(current.url)
        epg = Epg.cached(current) ?: emptyList()
        epg = Epg.forChannel(playlist, current)
    }
    LaunchedEffect(banner) { showBanner = true; delay(4000); showBanner = false }
    LaunchedEffect(toast) { if (toast != null) { delay(2000); toast = null } }
    LaunchedEffect(Unit) { while (true) { clock = hhmm(System.currentTimeMillis()); delay(15_000) } }
    LaunchedEffect(quick) { if (!quick) runCatching { focus.requestFocus() } }
    val refocus by UiEvents.refocus.collectAsState()
    LaunchedEffect(refocus) { if (refocus > 0 && !quick) runCatching { focus.requestFocus() } }

    // Thème 2 : une nouvelle tentative automatique après 5 s (une fois par chaîne), OK = réessayer
    var retryIn by remember { mutableStateOf<Int?>(null) }
    var autoRetried by remember(current.id) { mutableStateOf(false) }
    LaunchedEffect(pb.error, current.id) {
        retryIn = null
        if (pb.error != null && true && !autoRetried) {
            for (s in 5 downTo 1) { retryIn = s; delay(1000) }
            retryIn = null; autoRetried = true; pb.play(current.url)
        }
    }

    fun zap(d: Int) {
        val i = list.indexOfFirst { it.id == current.id }.let { if (it < 0) 0 else it }
        current = list[(i + d + list.size) % list.size]
        banner++
    }

    BackHandler { if (quick) quick = false else onExit(current, catId) }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onKeyEvent { e ->
                if (quick || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    Key.DirectionLeft -> { onGuide(current, catId); true }
                    Key.DirectionRight, Key.Info -> { banner++; true }
                    Key.Menu -> {
                        val f = UserData.toggleFavorite(playlist.id, current.id)
                        toast = if (f) "♥ Ajoutée aux favoris" else "Retirée des favoris"; true
                    }
                    else -> if (isCenter(e.key)) { if (pb.error != null) pb.play(current.url) else onMenu(current, catId); true } else false
                }
            }
            .clickable { if (!quick) onMenu(current, catId) }
    ) {
        VideoSurface(pb, Modifier.fillMaxSize())

        if (pb.buffering && pb.error == null) CircularProgressIndicator(color = IrColors.Cyan, modifier = Modifier.align(Alignment.Center).size(46.dp))
        if (true) pb.error?.let { ProPlaybackError(current.name, epg.firstOrNull { val t = System.currentTimeMillis(); t in it.start until it.end }?.title ?: "", live = true, detail = it, autoRetryIn = retryIn) }
        else pb.error?.let {
            Column(Modifier.align(Alignment.Center).background(Color(0xDD050A1C), RoundedCornerShape(14.dp)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.name, color = IrColors.Cyan, fontSize = 15.sp)
                Text(it, color = Color.White, fontSize = 16.sp)
                Text("▲▼ pour changer de chaîne", color = IrColors.TextDim, fontSize = 13.sp)
            }
        }

        // Petit logo + heure (discrets)
        Row(
            Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color(0x99050A1C), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text(clock, color = Color.White, fontSize = 14.sp) }

        if (showBanner && !quick) LiveBanner(current, epg, UserData.isFavorite(playlist.id, current.id), Modifier.align(Alignment.BottomCenter))

        if (quick) QuickList(
            playlist = playlist, catalog = catalog, catId = catId, current = current,
            onCategory = { catId = it },
            onPick = { ch, cid ->
                catId = cid; list = channelsFor(catalog, playlist, cid).ifEmpty { listOf(ch) }
                current = ch; quick = false; banner++
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        toast?.let { ToastBox(it, Modifier.align(Alignment.TopCenter).padding(top = 20.dp)) }
    }
}

@Composable
private fun LiveBanner(ch: Channel, epg: List<Program>, fav: Boolean, modifier: Modifier) {
    val now = epg.firstOrNull { it.isNow() }
    val next = epg.firstOrNull { it.start >= (now?.end ?: Long.MAX_VALUE) }
    Row(
        modifier.padding(20.dp).fillMaxWidth()
            .background(Color(0xE0050A1C), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x5922D3EE), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(ch.logo, initials(ch.name), 76.dp, 52.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ch.num}", color = IrColors.Cyan, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(ch.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                Text("EN DIRECT", color = IrColors.CyanDark, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(IrColors.Cyan, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                if (fav) Text("  ♥", color = IrColors.Cyan, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            if (now != null) {
                Text("${hhmm(now.start)} – ${hhmm(now.end)} · ${now.title}", color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { now.progress() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(4.dp), color = IrColors.Cyan, trackColor = Color(0xFF1D2B55))
            } else Text("Programme non communiqué", color = IrColors.TextDim, fontSize = 15.sp)
            next?.let { Text("À suivre · ${hhmm(it.start)} ${it.title}", color = IrColors.TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("▲▼ zapper", color = IrColors.TextDim, fontSize = 12.sp)
            Text("OK menu", color = IrColors.TextDim, fontSize = 12.sp)
            Text("◀ guide", color = IrColors.TextDim, fontSize = 12.sp)
            Text("Menu ♥", color = IrColors.TextDim, fontSize = 12.sp)
        }
    }
}

/** Liste rapide sur le côté : ◀▶ catégorie, ▲▼ chaîne, OK regarder. La vidéo continue derrière. */
@Composable
private fun QuickList(
    playlist: Playlist, catalog: Catalog, catId: String, current: Channel,
    onCategory: (String) -> Unit, onPick: (Channel, String) -> Unit, modifier: Modifier,
) {
    val cats = remember(catalog) { listOf(CAT_FAV to "Favoris", CAT_RECENT to "Récentes") + catalog.liveCategories.map { it.id to it.name } }
    var ciRaw by remember(catalog) { mutableIntStateOf(cats.indexOfFirst { it.first == catId }.coerceAtLeast(0)) }
    val ci = ciRaw.coerceIn(0, (cats.size - 1).coerceAtLeast(0))
    val chans = remember(ci, catalog) { channelsFor(catalog, playlist, cats[ci].first) }
    val state = rememberLazyListState()
    val first = remember { FocusRequester() }
    LaunchedEffect(ci) {
        val i = chans.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        if (chans.isNotEmpty()) state.scrollToItem((i - 3).coerceAtLeast(0))
        delay(30); runCatching { first.requestFocus() }
    }
    Column(
        modifier.fillMaxHeight().width(400.dp).background(Color(0xF0050A1C)).border(1.dp, Color(0x4D22D3EE)).padding(vertical = 14.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { ciRaw = (ci - 1 + cats.size) % cats.size; onCategory(cats[ciRaw].first); true }
                    Key.DirectionRight -> { ciRaw = (ci + 1) % cats.size; onCategory(cats[ciRaw].first); true }
                    else -> false
                }
            },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◀", color = IrColors.Cyan, fontSize = 14.sp)
            Text(cats[ci].second, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp).background(IrColors.Violet, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            Text("▶", color = IrColors.Cyan, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (chans.isEmpty()) Text("Aucune chaîne", color = IrColors.TextDim, modifier = Modifier.padding(16.dp).focusRequester(first).focusable())
        LazyColumn(state = state) {
            itemsIndexed(chans, key = { _, c -> c.id }) { _, ch ->
                var f by remember { mutableStateOf(false) }
                val isCur = ch.id == current.id
                Row(
                    (if (isCur || (chans.none { it.id == current.id } && ch == chans.first())) Modifier.focusRequester(first) else Modifier)
                        .fillMaxWidth()
                        .background(if (f) IrColors.Violet else Color.Transparent)
                        .then(if (f) Modifier.border(2.dp, IrColors.Cyan) else Modifier)
                        .onFocusChanged { f = it.isFocused }
                        .clickable { onPick(ch, cats[ci].first) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${ch.num}", color = if (isCur) IrColors.Cyan else IrColors.TextDim, fontSize = 13.sp, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ch.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Epg.cached(ch)?.firstOrNull { it.isNow() }?.let {
                            Text(it.title, color = IrColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (isCur) Text("♪", color = IrColors.Cyan, fontSize = 14.sp)
                }
            }
        }
    }
}

/* =====================================================================
 *  LECTEUR FILMS ET SÉRIES : reprise, commandes, langue/sous-titres,
 *  épisode suivant automatique
 * ===================================================================== */
data class VodEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val url: String,
    val kind: String,      // "movie" ou "episode"
    val parentId: String,  // série
)

@Composable
fun VodPlayerScreen(
    playlist: Playlist,
    queue: List<VodEntry>,
    startIndex: Int,
    startMs: Long? = null,
    onExit: () -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex) }
    val item = queue[index]
    val next = queue.getOrNull(index + 1)
    val prev = queue.getOrNull(index - 1)
    val pb = rememberPlayback()
    val settings by UserData.settings.collectAsState()

    var askResume by remember(item.id) { mutableStateOf(if (index == startIndex && startMs != null) null else UserData.resumeOf(playlist.id, item.id)?.takeIf { it.pos > 30_000 }) }
    var started by remember(item.id) { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableIntStateOf(1) }     // change = réaffiche les commandes
    var showControls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(false) }
    var nextCountdown by remember(item.id) { mutableStateOf<Int?>(null) }
    var nextDismissed by remember(item.id) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun save() {
        if (pb.engine.duration() > 0) UserData.saveResume(
            playlist.id,
            UserData.Resume(item.id, pb.engine.position(), pb.engine.duration(), item.title + if (item.subtitle.isNotBlank()) " · ${item.subtitle}" else "", item.kind, item.parentId, System.currentTimeMillis())
        )
    }
    var vodRetryIn by remember { mutableStateOf<Int?>(null) }
    var vodRetried by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(pb.error, item.id) {
        vodRetryIn = null
        if (pb.error != null && true && !vodRetried) {
            for (s in 5 downTo 1) { vodRetryIn = s; delay(1000) }
            vodRetryIn = null; vodRetried = true; pb.play(item.url, pos)
        }
    }
    fun start(fromMs: Long) { started = true; askResume = null; pb.play(item.url, fromMs); controls++ }
    fun goNext() { save(); if (next != null) { index++ } else onExit() }

    LaunchedEffect(item.id) { if (askResume == null) start(if (index == startIndex && startMs != null) startMs else 0) }
    pb.onEnded = { if (next != null && settings.autoNextEpisode && !nextDismissed) goNext() else { save(); onExit() } }
    pb.onBackground = { save() }
    val refocus by UiEvents.refocus.collectAsState()
    LaunchedEffect(refocus) { if (refocus > 0 && !panel && askResume == null) runCatching { if (showControls) playFocus.requestFocus() else rootFocus.requestFocus() } }
    DisposableEffect(item.id) { onDispose { save() } }

    // Suivi de la position + sauvegarde toutes les 10 s + carte « épisode suivant »
    LaunchedEffect(item.id, started) {
        var tick = 0
        while (started) {
            pos = pb.engine.position(); dur = pb.engine.duration(); playing = pb.engine.isPlaying()
            if (++tick % 20 == 0) save()
            if (next != null && settings.autoNextEpisode && dur > 60_000 && dur - pos in 1..20_000 && !nextDismissed && nextCountdown == null) nextCountdown = 10
            delay(500)
        }
    }
    LaunchedEffect(nextCountdown) {
        val c = nextCountdown ?: return@LaunchedEffect
        if (c <= 0) goNext() else { delay(1000); if (nextCountdown == c) nextCountdown = c - 1 }
    }
    LaunchedEffect(controls) { showControls = true; delay(5000); if (!panel) showControls = false }
    LaunchedEffect(showControls, panel, askResume) {
        if (askResume == null && !panel) {
            delay(30)
            runCatching { if (showControls) playFocus.requestFocus() else rootFocus.requestFocus() }
        }
    }

    BackHandler {
        when {
            panel -> panel = false
            nextCountdown != null -> { nextCountdown = null; nextDismissed = true }
            showControls && askResume == null -> showControls = false
            else -> { save(); onExit() }
        }
    }

    fun seekBy(d: Long) { val p = (pb.engine.position() + d).coerceIn(0, (pb.engine.duration() - 1000).coerceAtLeast(0)); pb.engine.seekTo(p); pos = p }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { e ->
                // Touches média de la télécommande : toujours actives
                if (e.type != KeyEventType.KeyDown || askResume != null) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { if (pb.engine.isPlaying()) pb.engine.pause() else pb.engine.resume(); controls++; true }
                    Key.MediaRewind -> { seekBy(-10_000); controls++; true }
                    Key.MediaFastForward -> { seekBy(10_000); controls++; true }
                    else -> false
                }
            }
            .onKeyEvent { e ->
                if (showControls || panel || askResume != null || nextCountdown != null || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { seekBy(if (e.nativeKeyEvent.repeatCount > 3) -30_000 else -10_000); true }
                    Key.DirectionRight -> { seekBy(if (e.nativeKeyEvent.repeatCount > 3) 30_000 else 10_000); true }
                    else -> if (isCenter(e.key) || e.key == Key.DirectionUp || e.key == Key.DirectionDown) { controls++; true } else false
                }
            }
            .clickable { controls++ }
    ) {
        VideoSurface(pb, Modifier.fillMaxSize())
        if (pb.buffering && started && pb.error == null) CircularProgressIndicator(color = IrColors.Cyan, modifier = Modifier.align(Alignment.Center).size(46.dp))
        if (true) pb.error?.let { ProPlaybackError(item.title, item.subtitle, live = false, detail = it, autoRetryIn = vodRetryIn) }
        else pb.error?.let {
            Text(it, color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center).background(Color(0xDD050A1C), RoundedCornerShape(12.dp)).padding(20.dp))
        }

        if (showControls && askResume == null) {
            // Haut : titre
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xC0050A1C)).padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.subtitle.isNotBlank()) Text(item.subtitle, color = IrColors.TextDim, fontSize = 14.sp)
                }
                Text(pb.engine.name, color = IrColors.Cyan, fontSize = 12.sp, modifier = Modifier.border(1.dp, IrColors.Cyan, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
            }
            // Bas : barre + boutons
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xD9050A1C)).padding(horizontal = 24.dp, vertical = 14.dp)) {
                LinearProgressIndicator(progress = { if (dur > 0) pos.toFloat() / dur else 0f }, modifier = Modifier.fillMaxWidth().height(5.dp), color = IrColors.Cyan, trackColor = Color(0xFF1D2B55))
                Row(Modifier.padding(top = 6.dp)) {
                    Text(clockTime(pos), color = Color(0xFFCCD2E0), fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    if (dur > 0) Text("${clockTime(dur)} · fin à ${hhmm(System.currentTimeMillis() + (dur - pos))}", color = Color(0xFFCCD2E0), fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    val bump: () -> Unit = { controls++ }
                    if (prev != null) CtlButton("⏮", onFocus = bump) { save(); index-- }
                    CtlButton("−10", onFocus = bump) { seekBy(-10_000); controls++ }
                    CtlButton(if (playing) "❚❚" else "▶", big = true, modifier = Modifier.focusRequester(playFocus), onFocus = bump) {
                        if (pb.engine.isPlaying()) pb.engine.pause() else pb.engine.resume(); controls++
                    }
                    CtlButton("+10", onFocus = bump) { seekBy(10_000); controls++ }
                    if (next != null) CtlButton("⏭", onFocus = bump) { goNext() }
                    Spacer(Modifier.width(30.dp))
                    CtlButton("Langue / Sous-titres", onFocus = bump) { panel = true }
                }
            }
        }

        if (panel) {
            var tr by remember { mutableIntStateOf(0) }
            LaunchedEffect(tr) { if (tr > 0) { delay(400); tr = -tr } }   // relit les pistes après le changement
            TracksPanel(remember(tr) { pb.engine.audioTracks() }, remember(tr) { pb.engine.subtitleTracks() },
                onAudio = { pb.engine.selectAudio(it); tr = kotlin.math.abs(tr) + 1 }, onSub = { pb.engine.selectSubtitle(it); tr = kotlin.math.abs(tr) + 1 },
                modifier = Modifier.align(Alignment.CenterEnd))
        }

        askResume?.let { r ->
            ResumeDialog(clockTime(r.pos), onResume = { start(r.pos) }, onRestart = { start(0) }, modifier = Modifier.align(Alignment.Center))
        }

        val nc = nextCountdown
        if (nc != null && next != null) NextCard(next, nc, onNow = { goNext() }, onCancel = { nextCountdown = null; nextDismissed = true }, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun CtlButton(text: String, big: Boolean = false, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier
            .then(if (big) Modifier.size(58.dp) else Modifier.height(42.dp))
            .clip(if (big) CircleShape else RoundedCornerShape(21.dp))
            .background(if (f) IrColors.Violet else if (big) Color(0xFF3A2A7A) else Color(0x26FFFFFF))
            .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, if (big) CircleShape else RoundedCornerShape(21.dp))
            .onFocusChanged { if (it.isFocused && !f) onFocus(); f = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = if (big) 0.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = if (big) 20.sp else 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ResumeDialog(at: String, onResume: () -> Unit, onRestart: () -> Unit, modifier: Modifier) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { f.requestFocus() } }
    Column(
        modifier.background(Color(0xF0050A1C), RoundedCornerShape(16.dp)).border(1.dp, IrColors.Cyan, RoundedCornerShape(16.dp)).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tu t'étais arrêté à $at", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IrButton("Reprendre à $at", modifier = Modifier.focusRequester(f), onClick = onResume)
            IrButton("Recommencer", primary = false, onClick = onRestart)
        }
    }
}

@Composable
private fun NextCard(next: VodEntry, seconds: Int, onNow: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { f.requestFocus() } }
    Column(
        modifier.padding(24.dp).width(420.dp).background(Color(0xF0050A1C), RoundedCornerShape(16.dp)).border(1.dp, IrColors.Cyan, RoundedCornerShape(16.dp)).padding(18.dp)
    ) {
        Text("Épisode suivant dans $seconds s", color = IrColors.Cyan, fontSize = 14.sp)
        Text(next.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (next.subtitle.isNotBlank()) Text(next.subtitle, color = IrColors.TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IrButton("Lire maintenant", modifier = Modifier.focusRequester(f), onClick = onNow)
            IrButton("Annuler", primary = false, onClick = onCancel)
        }
    }
}

@Composable
private fun TracksPanel(audio: List<TrackOpt>, subs: List<TrackOpt>, onAudio: (String) -> Unit, onSub: (String?) -> Unit, modifier: Modifier) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { f.requestFocus() } }
    var sel by remember { mutableIntStateOf(0) }
    Row(
        modifier.fillMaxHeight().width(560.dp).background(Color(0xF0050A1C)).border(1.dp, Color(0x4D22D3EE)).padding(22.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Langue", color = IrColors.Cyan, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            if (audio.isEmpty()) Text("Une seule langue", color = IrColors.TextDim, fontSize = 14.sp, modifier = Modifier.focusRequester(f).focusable())
            audio.forEachIndexed { i, t ->
                OptRow(t.label, t.selected, if (i == 0) Modifier.focusRequester(f) else Modifier) { onAudio(t.id); sel++ }
            }
        }
        Column(Modifier.weight(1f)) {
            Text("Sous-titres", color = IrColors.Cyan, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OptRow("Aucun", subs.none { it.selected }, Modifier) { onSub(null); sel++ }
            subs.forEach { t -> OptRow(t.label, t.selected, Modifier) { onSub(t.id); sel++ } }
            Spacer(Modifier.height(10.dp))
            Text("Taille : Paramètres > Lecteur", color = IrColors.TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OptRow(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val picked = selected
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
            .background(if (f) IrColors.Violet else Color(0xFF12204A))
            .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { f = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (picked) Text("✓", color = IrColors.Cyan, fontSize = 15.sp)
    }
}
