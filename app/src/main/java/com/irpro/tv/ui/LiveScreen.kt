package com.irpro.tv.ui

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.ui.graphics.Brush

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
    pb: com.irpro.tv.player.Playback = rememberPlayback(),
) {
    BackHandler(onBack = onBack)
    val rev by UserData.rev.collectAsState()
    val cats = remember(catalog) { catalog.liveCategories }
    val counts = catalog.channelCountByCategory
    val catState = rememberLazyListState()
    var catId by remember { mutableStateOf(startCategory ?: cats.firstOrNull()?.id ?: CAT_FAV) }
    val channels = remember(catId, rev, catalog) { channelsFor(catalog, playlist, catId) }
    var focused by remember { mutableStateOf<Channel?>(channels.firstOrNull { it.id == startChannel } ?: channels.firstOrNull()) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Aperçu : se lance quand on reste ~0,7 s sur une chaîne
    LaunchedEffect(focused?.id) {
        val ch = focused ?: return@LaunchedEffect
        epg = Epg.cached(ch) ?: emptyList()
        delay(if (ch.id == startChannel) 0 else 700)
        pb.playIfNew(ch.url)
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

    MosaicBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 22.dp)) {
            val title = when (catId) { CAT_FAV -> "Favoris"; CAT_RECENT -> "Récentes"; else -> cats.firstOrNull { it.id == catId }?.name ?: "" }
            MosaicHeader("Direct", "${formatCount(catalog.channels.size)} chaînes · ${title.ifBlank { "—" }} · ${channels.size}")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                // 1) Catégories
                LazyColumn(
                    state = catState,
                    modifier = Modifier.width(190.dp).fillMaxHeight().mosaicAppear(0),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(4.dp),
                ) {
                    val special = listOf(CAT_FAV to "♥  Favoris", CAT_RECENT to "↺  Récentes")
                    itemsIndexed(special) { i, (id, label) ->
                        CatRow(label, null, id == catId, if (id == catId || (i == 0 && cats.isEmpty())) Modifier.focusRequester(catFocus) else Modifier, special = i == 0) { catId = id }
                    }
                    itemsIndexed(cats, key = { _, c -> c.id }) { i, c ->
                        CatRow(
                            c.name, counts[c.id], c.id == catId,
                            if (c.id == catId || (i == 0 && catId != CAT_FAV && catId != CAT_RECENT && cats.none { it.id == catId })) Modifier.focusRequester(catFocus) else Modifier,
                            fr = c.isFrench,
                        ) { catId = c.id }
                    }
                }

                // 2) Chaînes
                Column(Modifier.width(310.dp).fillMaxHeight().mosaicAppear(1)) {
                    if (channels.isEmpty()) {
                        Text(
                            if (catId == CAT_FAV) "Aucun favori. Appui long (ou touche Menu) sur une chaîne pour l'ajouter."
                            else "Aucune chaîne ici.",
                            color = Mz.Dim, fontSize = 13.sp, modifier = Modifier.padding(8.dp),
                        )
                    }
                    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
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

                // 3) Aperçu + programme
                Column(Modifier.weight(1f).fillMaxHeight().padding(4.dp).mosaicAppear(2), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).mosaicRing(true, shape).clip(shape).background(Color.Black)
                    ) {
                        VideoSurface(pb, Modifier.fillMaxSize())
                        Row(
                            Modifier.padding(10.dp).background(Color(0xB307060D), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) { LiveDot(); Text("APERÇU", color = Mz.Text, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                        if (pb.error != null) Text(pb.error!!, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(12.dp))
                    }
                    val now = epg.firstOrNull { it.isNow() }
                    val next = epg.filter { it.start >= (now?.end ?: System.currentTimeMillis()) }.take(3)
                    Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Mz.Tile).border(1.dp, Mz.TileLine, RoundedCornerShape(12.dp)).padding(13.dp)) {
                        Text(focused?.let { "${it.num} · ${it.name}" } ?: "", color = Color(0xFFA5F3FC), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(now?.title ?: "Programme non communiqué", color = Mz.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IrFonts.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (now != null) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(hhmm(now.start), color = Mz.Dim, fontSize = 9.sp)
                                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(now.progress()).background(Mz.Bar))
                                }
                                Text(hhmm(now.end), color = Mz.Dim, fontSize = 9.sp)
                            }
                        }
                        if (next.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Ensuite", color = Mz.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
                            next.forEach {
                                Row(Modifier.padding(top = 3.dp)) {
                                    Text(hhmm(it.start), color = Mz.Cyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(38.dp))
                                    Text(it.title, color = Mz.Soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("OK : plein écran · Appui long / Menu : favori", color = Mz.Mute, fontSize = 9.sp)
                    }
                }
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
private fun CatRow(label: String, count: Int?, selected: Boolean, modifier: Modifier, fr: Boolean = false, special: Boolean = false, onFocus: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .fillMaxWidth()
            .mosaicRing(f, shape)
            .clip(shape)
            .then(
                when {
                    selected -> Modifier.background(Mz.Pill)
                    special -> Modifier.background(Brush.linearGradient(listOf(Color(0xFF3B1C8C), Color(0xFF1A0E3D))))
                    else -> Modifier.background(Mz.Tile)
                }
            )
            .border(1.dp, if (selected) Color.Transparent else Mz.TileLine, shape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) onFocus() }
            .combinedClickable(onClick = onFocus)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fr) {
            Text("FR", color = if (selected) Mz.Text else IrColors.CyanDark, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(if (selected) Mz.Bg else IrColors.Cyan, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) Mz.Bg else if (f) Color.White else Mz.Soft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text("$count", color = if (selected) Color(0xCC07060D) else Mz.Mute, fontSize = 9.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    ch: Channel, fav: Boolean, program: Program?, modifier: Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .mosaicRing(f, shape)
            .clip(shape)
            .background(if (f) Color(0x2E8B5CF6) else Mz.Tile)
            .border(1.dp, Mz.TileLine, shape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) onFocus() }
            // Télécommande : touche Menu = favori (l'appui long sur OK est géré par combinedClickable)
            .onPreviewKeyEvent { e -> if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) { onLong(); true } else false }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${ch.num}", color = if (f) Mz.Cyan else Mz.Mute, fontSize = 9.sp, modifier = Modifier.width(24.dp))
        NetImage(ch.logo, initials(ch.name), 40.dp, 28.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.name, color = Mz.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (fav) Text("  ♥", color = Color(0xFFC084FC), fontSize = 10.sp)
            }
            if (program != null) {
                Text(program.title, color = Mz.Dim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color(0x1FFFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(program.progress()).background(Mz.Bar))
                }
            }
        }
    }
}
