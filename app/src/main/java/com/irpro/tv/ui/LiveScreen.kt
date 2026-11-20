package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.irpro.tv.player.VideoSurface
import com.irpro.tv.player.rememberPlayback
import kotlinx.coroutines.delay

/** Catégories spéciales en haut de la liste. */
const val CAT_FAV = "__fav"
const val CAT_RECENT = "__recent"

fun channelsFor(catalog: Catalog, playlist: Playlist, catId: String): List<Channel> = when (catId) {
    CAT_FAV -> UserData.favorites(playlist.id).mapNotNull { catalog.channelById[it] }.distinctBy { it.id }
    CAT_RECENT -> UserData.recents(playlist.id).mapNotNull { catalog.channelById[it] }.distinctBy { it.id }
    else -> catalog.channelsByCategory[catId].orEmpty()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(
    playlist: Playlist,
    catalog: Catalog,
    startCategory: String?,
    startChannel: String?,
    onPlay: (Channel, String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    val cats = remember(catalog) { catalog.liveCategories }
    val counts = catalog.channelCountByCategory
    val catState = rememberLazyListState()
    var catId by remember { mutableStateOf(startCategory ?: cats.firstOrNull()?.id ?: CAT_FAV) }
    val channels = remember(catId, rev, catalog) { channelsFor(catalog, playlist, catId) }
    var focused by remember { mutableStateOf<Channel?>(channels.firstOrNull { it.id == startChannel } ?: channels.firstOrNull()) }
    val pb = rememberPlayback()
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Aperçu : se lance quand on reste ~0,7 s sur une chaîne
    LaunchedEffect(focused?.id) {
        val ch = focused ?: return@LaunchedEffect
        epg = Epg.cached(ch) ?: emptyList()
        delay(700)
        pb.play(ch.url)
        epg = Epg.forChannel(playlist, ch)
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }
    val refocus by UiEvents.refocus.collectAsState()

    val catFocus = remember { FocusRequester() }
    LaunchedEffect(refocus) { if (refocus > 0) runCatching { catFocus.requestFocus() } }
    val chFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = channels.indexOfFirst { it.id == startChannel }
        if (i >= 0) { listState.scrollToItem((i - 2).coerceAtLeast(0)); delay(50); runCatching { chFocus.requestFocus() } }
        else {
            // Amène la catégorie choisie à l'écran avant de lui donner le focus
            val ci = cats.indexOfFirst { it.id == catId }
            if (ci >= 0) catState.scrollToItem((ci + 4 - 3).coerceAtLeast(0))
            delay(50); runCatching { catFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().background(IrColors.Night)) {
        Row(Modifier.fillMaxSize()) {
            // 1) Catégories
            LazyColumn(
                state = catState,
                modifier = Modifier.width(250.dp).fillMaxHeight().background(Color(0xFF061026)).padding(vertical = 14.dp),
            ) {
                item {
                    Text("Direct", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp))
                }
                val special = listOf(CAT_FAV to "♥  Favoris", CAT_RECENT to "↺  Récentes")
                itemsIndexed(special) { i, (id, label) ->
                    CatRow(label, null, id == catId, if (id == catId || (i == 0 && cats.isEmpty())) Modifier.focusRequester(catFocus) else Modifier) { catId = id }
                }
                item { Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(Color(0xFF1D2B55))) }
                itemsIndexed(cats, key = { _, c -> c.id }) { i, c ->
                    CatRow(
                        c.name, counts[c.id], c.id == catId,
                        if (c.id == catId || (i == 0 && catId != CAT_FAV && catId != CAT_RECENT && cats.none { it.id == catId })) Modifier.focusRequester(catFocus) else Modifier,
                        fr = c.isFrench,
                    ) { catId = c.id }
                }
            }

            // 2) Chaînes
            Column(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                val title = when (catId) { CAT_FAV -> "Favoris"; CAT_RECENT -> "Récentes"; else -> cats.firstOrNull { it.id == catId }?.name ?: "" }
                Text("$title · ${channels.size} chaînes", color = IrColors.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                if (channels.isEmpty()) {
                    Text(
                        if (catId == CAT_FAV) "Aucun favori. Appui long (ou touche Menu) sur une chaîne pour l'ajouter."
                        else "Aucune chaîne ici.",
                        color = Color(0xFFD5DBEA), fontSize = 15.sp, modifier = Modifier.padding(8.dp),
                    )
                }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { _, ch ->
                        val fav = remember(rev) { UserData.isFavorite(playlist.id, ch.id) }
                        val toggleFav = {
                            val now = UserData.toggleFavorite(playlist.id, ch.id)
                            toast = if (now) "♥ ${ch.name} ajoutée aux favoris" else "${ch.name} retirée des favoris"
                        }
                        ChannelRow(
                            ch = ch, fav = fav,
                            program = if (focused?.id == ch.id) epg.firstOrNull { it.isNow() } else Epg.cached(ch)?.firstOrNull { it.isNow() },
                            modifier = if (ch.id == (startChannel ?: channels.firstOrNull()?.id)) Modifier.focusRequester(chFocus) else Modifier,
                            onFocus = { focused = ch },
                            onClick = { onPlay(ch, catId) },
                            onLong = toggleFav,
                        )
                    }
                }
            }

            // 3) Aperçu
            Column(Modifier.width(360.dp).fillMaxHeight().padding(top = 14.dp, end = 16.dp, bottom = 14.dp)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x6622D3EE), RoundedCornerShape(12.dp)).background(Color.Black)
                ) {
                    VideoSurface(pb, Modifier.fillMaxSize())
                    Text(
                        "● DIRECT", color = IrColors.CyanDark, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).background(IrColors.Cyan, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    if (pb.error != null) Text(pb.error!!, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(12.dp))
                }
                val now = epg.firstOrNull { it.isNow() }
                val next = epg.filter { it.start >= (now?.end ?: System.currentTimeMillis()) }.take(3)
                Spacer(Modifier.height(10.dp))
                Text(focused?.name ?: "", color = IrColors.Cyan, fontSize = 13.sp)
                Text(now?.title ?: "Programme non communiqué", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (now != null) {
                    Text("${hhmm(now.start)} – ${hhmm(now.end)}", color = IrColors.TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { now.progress() }, modifier = Modifier.fillMaxWidth().height(3.dp), color = IrColors.Cyan, trackColor = Color(0xFF1D2B55))
                    if (now.desc.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(now.desc, color = Color(0xFFCCD2E0), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (next.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("À suivre", color = IrColors.Cyan, fontSize = 13.sp)
                    next.forEach { Text("${hhmm(it.start)}  ${it.title}", color = Color(0xFFCCD2E0), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Spacer(Modifier.weight(1f))
                Text("OK plein écran · appui long / Menu : favori · ◀ catégories", color = IrColors.TextDim, fontSize = 11.sp)
            }
        }
        toast?.let { ToastBox(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
fun ToastBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 15.sp,
        modifier = modifier.padding(bottom = 30.dp)
            .background(Color(0xEE050A1C), RoundedCornerShape(10.dp))
            .border(1.dp, IrColors.Cyan, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatRow(label: String, count: Int?, selected: Boolean, modifier: Modifier, fr: Boolean = false, onFocus: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) IrColors.Violet else if (f) Color(0x332E66A8) else Color.Transparent)
            .then(if (f) Modifier.border(2.dp, IrColors.Cyan) else Modifier)
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(onClick = onFocus)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fr) {
            Text("FR", color = IrColors.CyanDark, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(IrColors.Cyan, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected || f) Color.White else Color(0xFFAAB3C5), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text("$count", color = if (selected) Color.White else IrColors.TextDim, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    ch: Channel, fav: Boolean, program: Program?, modifier: Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (f) IrColors.Violet else Color(0x0FFFFFFF))
            .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(10.dp))
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) onFocus() }
            // Télécommande : touche Menu = favori (l'appui long sur OK est géré par combinedClickable)
            .onPreviewKeyEvent { e -> if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) { onLong(); true } else false }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${ch.num}", color = if (f) IrColors.Cyan else IrColors.TextDim, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        NetImage(ch.logo, initials(ch.name), 52.dp, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (fav) Text("  ♥", color = IrColors.Cyan, fontSize = 13.sp)
            }
            if (program != null) {
                Text(program.title, color = if (f) Color(0xFFE4DCFF) else IrColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(progress = { program.progress() }, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp), color = IrColors.Cyan, trackColor = Color(0xFF1D2B55))
            }
        }
    }
}
