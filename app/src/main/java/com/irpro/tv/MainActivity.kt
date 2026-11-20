package com.irpro.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.data.Channel
import com.irpro.tv.data.ContentRepository
import com.irpro.tv.data.Epg
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.PlaylistStore
import com.irpro.tv.data.UpdateInfo
import com.irpro.tv.data.Updater
import com.irpro.tv.data.UserData
import com.irpro.tv.ui.CAT_FAV
import com.irpro.tv.ui.CAT_RECENT
import com.irpro.tv.ui.GuideScreen
import com.irpro.tv.ui.HomeScreen
import com.irpro.tv.ui.IrButton
import com.irpro.tv.ui.IrColors
import com.irpro.tv.ui.LivePlayerScreen
import com.irpro.tv.ui.LiveScreen
import com.irpro.tv.ui.LoadingScreen
import com.irpro.tv.ui.M3uScreen
import com.irpro.tv.ui.MovieDetailScreen
import com.irpro.tv.ui.PhoneAddScreen
import com.irpro.tv.ui.PlaylistReadyScreen
import com.irpro.tv.ui.Reminders
import com.irpro.tv.ui.SearchScreen
import com.irpro.tv.ui.Section
import com.irpro.tv.ui.SeriesDetailScreen
import com.irpro.tv.ui.SettingsScreen
import com.irpro.tv.ui.UpdateDialog
import com.irpro.tv.ui.VodCatalogScreen
import com.irpro.tv.ui.VodEntry
import com.irpro.tv.ui.VodGridScreen
import com.irpro.tv.ui.VodPlayerScreen
import com.irpro.tv.ui.WelcomeScreen
import com.irpro.tv.ui.XtreamScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserData.init(this)
        com.irpro.tv.ui.Sfx.init(this)
        // L'écran ne se met pas en veille pendant qu'on regarde
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { App() }
    }
}

/** Où l'on revient avec « Retour » depuis une fiche ou un lecteur. */
private class Nav {
    var liveCat: String? = null
    var liveChannel: String? = null
    var vodQueue: List<VodEntry> = emptyList()
    var vodIndex: Int = 0
    var vodBack: String = "home"
    var detailBack: String = "home"
    var gridKind: Boolean = false
    var gridCat: String = ""
    var guideBack: String = "home"
    var playBack: String = "live"
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember(ctx) { PlaylistStore(ctx) }
    var screen by rememberSaveable { mutableStateOf("intro") }
    var version by remember { mutableIntStateOf(0) }
    val content by ContentRepository.state.collectAsState()
    val settings by UserData.settings.collectAsState()
    val nav = remember { Nav() }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateChecked by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf<Reminders.R?>(null) }

    // Contenu rechargé pendant l'intro (réglage « à chaque lancement »)
    LaunchedEffect(Unit) {
        store.active()?.let { if (content.playlistId != it.id) ContentRepository.load(ctx, it, refresh = settings.refreshOnLaunch) }
    }
    // Guide XMLTV (listes M3U, ou lien personnalisé)
    LaunchedEffect(content.catalog, settings.epgUrl) {
        val c = content.catalog ?: return@LaunchedEffect
        val link = settings.epgUrl.ifBlank { c.epgUrl }
        if (link.isNotBlank()) Epg.loadXmltv(link, c.channels)
    }
    // Rappels du guide
    LaunchedEffect(Unit) { while (true) { delay(30_000); Reminders.due().firstOrNull()?.let { reminder = it } } }

    fun afterIntro() = if (store.hasAny()) "home" else "welcome"

    val onSaved: (Playlist) -> Unit = {
        store.save(it); version++
        ContentRepository.load(ctx, it, refresh = true)
        screen = "home"
    }

    when (screen) {
        "intro" -> IntroScreen(channelCount = content.catalog?.channels?.size ?: 0, onFinished = { screen = afterIntro() })
        "welcome" -> WelcomeScreen(
            canGoBack = store.hasAny(), onBack = { screen = "playlists" },
            onXtream = { screen = "xtream" }, onM3u = { screen = "m3u" }, onPhone = { screen = "phone" },
        )
        "xtream" -> XtreamScreen(onBack = { screen = "welcome" }, onSaved = onSaved)
        "m3u" -> M3uScreen(onBack = { screen = "welcome" }, onSaved = onSaved)
        "phone" -> PhoneAddScreen(onBack = { screen = "welcome" }, onSaved = onSaved)
        else -> {
            val active = remember(version) { store.active() }
            val catalog = content.catalog
            if (active == null) {
                LaunchedEffect(Unit) { screen = "welcome" }
            } else if (catalog == null || content.playlistId != active.id) {
                LaunchedEffect(active.id) { if (content.playlistId != active.id) ContentRepository.load(ctx, active, refresh = true) }
                LoadingScreen(state = content, onRetry = { ContentRepository.load(ctx, active, refresh = true) }, onPlaylists = { screen = "welcome" })
            } else {
                val byId = remember(catalog) { catalog.channels.associateBy { it.id } }
                fun playChannel(ch: Channel, cat: String, back: String) {
                    nav.liveChannel = ch.id; nav.liveCat = cat; nav.playBack = back; screen = "liveplay"
                }
                fun openMovie(id: String, back: String) { nav.detailBack = back; screen = "movie:$id" }
                fun openSeries(id: String, back: String) { nav.detailBack = back; screen = "show:$id" }

                when {
                    screen == "playlists" -> PlaylistReadyScreen(
                        playlist = active, count = remember(version) { store.all().size }, state = content,
                        onRefresh = { ContentRepository.load(ctx, active, refresh = true) },
                        onAdd = { screen = "welcome" },
                        onDelete = {
                            ContentRepository.clear(ctx, active.id); store.delete(active.id); version++
                            store.active()?.let { ContentRepository.load(ctx, it, refresh = true) }
                            screen = afterIntro()
                        },
                        onBack = { screen = "settings" },
                    )
                    screen == "settings" -> SettingsScreen(
                        playlist = active, playlistsCount = remember(version) { store.all().size }, state = content,
                        onPlaylists = { screen = "playlists" },
                        onRefresh = { ContentRepository.load(ctx, active, refresh = true) },
                        onCheckUpdate = { updateChecked = false; screen = "home" },
                        onBack = { screen = "home" },
                    )
                    screen == "search" -> SearchScreen(
                        catalog,
                        onChannel = { id -> byId[id]?.let { playChannel(it, it.categoryId, "search") } },
                        onMovie = { openMovie(it, "search") },
                        onSeries = { openSeries(it, "search") },
                        onBack = { screen = "home" },
                    )
                    screen == "live" -> LiveScreen(
                        active, catalog, nav.liveCat, nav.liveChannel,
                        onPlay = { ch, cat -> playChannel(ch, cat, "live") },
                        onBack = { screen = "home" },
                    )
                    screen == "liveplay" -> {
                        val ch = byId[nav.liveChannel]
                        if (ch == null) LaunchedEffect(Unit) { screen = "live" }
                        else LivePlayerScreen(
                            active, catalog, ch, nav.liveCat ?: ch.categoryId,
                            onGuide = { c, cat -> nav.liveChannel = c.id; nav.liveCat = cat; nav.guideBack = "liveplay"; screen = "guide" },
                            onExit = { c, cat ->
                                nav.liveChannel = c.id; nav.liveCat = cat
                                screen = if (nav.playBack == "home" || nav.playBack == "search") nav.playBack else "live"
                            },
                        )
                    }
                    screen == "guide" -> GuideScreen(
                        active, catalog, nav.liveCat, nav.liveChannel,
                        onPlay = { ch, cat -> playChannel(ch, cat, "live") },
                        onBack = { screen = nav.guideBack },
                    )
                    screen == "movies" || screen == "series" -> {
                        val isSeries = screen == "series"
                        VodCatalogScreen(
                            active, catalog, series = isSeries,
                            onOpen = { id -> if (isSeries) openSeries(id, "series") else openMovie(id, "movies") },
                            onSeeAll = { cat -> nav.gridKind = isSeries; nav.gridCat = cat; screen = "grid" },
                            onBack = { screen = "home" },
                        )
                    }
                    screen == "grid" -> VodGridScreen(
                        active, catalog, nav.gridKind, nav.gridCat,
                        onOpen = { id -> if (nav.gridKind) openSeries(id, "grid") else openMovie(id, "grid") },
                        onBack = { screen = if (nav.gridKind) "series" else "movies" },
                    )
                    screen.startsWith("movie:") -> {
                        val here = screen
                        val m = catalog.movies.firstOrNull { it.id == here.removePrefix("movie:") }
                        if (m == null) LaunchedEffect(Unit) { screen = "movies" }
                        else MovieDetailScreen(active, m,
                            onPlay = { from ->
                                if (from == 0L) UserData.saveResume(active.id, UserData.Resume(m.id, 0, 0, m.name, "movie", "", System.currentTimeMillis()))
                                nav.vodQueue = listOf(VodEntry(m.id, m.name, "", m.url, "movie", ""))
                                nav.vodIndex = 0; nav.vodBack = here; screen = "vod"
                            },
                            onBack = { screen = nav.detailBack })
                    }
                    screen.startsWith("show:") -> {
                        val here = screen
                        val s = catalog.series.firstOrNull { it.id == here.removePrefix("show:") }
                        if (s == null) LaunchedEffect(Unit) { screen = "series" }
                        else SeriesDetailScreen(active, s,
                            onPlay = { eps, idx ->
                                nav.vodQueue = eps.map { VodEntry(it.id, s.name, "S${it.season} · E${it.number} · ${it.title}", it.url, "episode", s.id) }
                                nav.vodIndex = idx; nav.vodBack = here; screen = "vod"
                            },
                            onBack = { screen = nav.detailBack })
                    }
                    screen == "vod" -> {
                        if (nav.vodQueue.isEmpty()) LaunchedEffect(Unit) { screen = "home" }
                        else VodPlayerScreen(active, nav.vodQueue, nav.vodIndex, onExit = { screen = nav.vodBack })
                    }
                    else -> {
                        val rev by UserData.rev.collectAsState()
                        val last = remember(rev, catalog) { UserData.recents(active.id).firstNotNullOfOrNull { byId[it] } }
                        val favs = remember(rev) { UserData.favorites(active.id).size }
                        HomeScreen(
                            playlist = active, catalog = catalog, updating = content.loading,
                            onOpen = {
                                when (it) {
                                    Section.LIVE -> screen = "live"
                                    Section.MOVIES -> screen = "movies"
                                    Section.SERIES -> screen = "series"
                                    Section.GUIDE -> { nav.guideBack = "home"; screen = "guide" }
                                    Section.FAVORITES -> { nav.liveCat = CAT_FAV; nav.liveChannel = null; screen = "live" }
                                }
                            },
                            onSearch = { screen = "search" },
                            onSettings = { screen = "settings" },
                            lastChannel = last, favoritesCount = favs,
                            onPlayChannel = { ch -> playChannel(ch, CAT_RECENT, "home") },
                            onMovie = { openMovie(it, "home") },
                        )
                        // Nouvelle version de l'appli ? (vérifié en arrivant sur l'accueil)
                        LaunchedEffect(updateChecked) {
                            if (!updateChecked) { updateChecked = true; delay(2500); update = Updater.check() }
                        }
                        update?.let { UpdateDialog(it, onLater = { update = null }) }
                    }
                }

                // Rappel d'un programme qui commence
                reminder?.let { r ->
                    val ch = byId[r.channelId]
                    ReminderBanner(r.title, ch?.name ?: "", onWatch = { reminder = null; ch?.let { playChannel(it, it.categoryId, "home") } }, onClose = { reminder = null })
                }
            }
        }
    }
}

@Composable
private fun ReminderBanner(title: String, channel: String, onWatch: () -> Unit, onClose: () -> Unit) {
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { f.requestFocus() } }
    LaunchedEffect(Unit) { delay(20_000); onClose() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(top = 24.dp).background(Color(0xF0050A1C), RoundedCornerShape(16.dp))
                .border(1.dp, IrColors.Cyan, RoundedCornerShape(16.dp)).padding(18.dp)
        ) {
            Text("C'est l'heure !", color = IrColors.Cyan, fontSize = 14.sp)
            Text("$title · $channel", color = Color.White, fontSize = 18.sp)
            Row(Modifier.padding(top = 10.dp)) {
                IrButton("Regarder", modifier = Modifier.focusRequester(f), onClick = onWatch)
                Spacer(Modifier.width(12.dp))
                IrButton("Fermer", primary = false, onClick = onClose)
            }
        }
    }
}
