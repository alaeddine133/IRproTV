package com.irpro.tv.ui

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.size

import androidx.compose.ui.draw.shadow

import androidx.compose.foundation.layout.PaddingValues

import androidx.compose.ui.graphics.Brush

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.Channel
import com.irpro.tv.data.Epg
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.Program
import kotlinx.coroutines.delay

/** Rappels : « préviens-moi quand ce programme commence ». */
object Reminders {
    data class R(val channelId: String, val title: String, val start: Long)
    private val list = mutableListOf<R>()
    fun add(r: R) { list.removeAll { it.channelId == r.channelId && it.start == r.start }; list += r }
    fun has(chId: String, start: Long) = list.any { it.channelId == chId && it.start == start }
    /** Rappels dont l'heure est arrivée (retirés de la liste). */
    fun due(): List<R> { val now = System.currentTimeMillis(); val d = list.filter { it.start <= now + 60_000 }; list.removeAll(d); return d }
}

private const val WINDOW_H = 3          // heures visibles
private val CH_COL: Dp = 170.dp

@Composable
fun GuideScreen(
    playlist: Playlist,
    catalog: Catalog,
    categoryId: String?,
    focusChannel: String?,
    onPlay: (Channel, String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cats = remember(catalog) { listOf(CAT_FAV to "Favoris") + catalog.liveCategories.map { it.id to it.name } }
    var catIdx by remember { mutableStateOf(cats.indexOfFirst { it.first == categoryId }.takeIf { it >= 0 } ?: cats.indexOfFirst { it.first != CAT_FAV }.coerceAtLeast(0)) }
    val catId = cats.getOrNull(catIdx)?.first ?: CAT_FAV
    val channels = remember(catIdx) { channelsFor(catalog, playlist, catId).take(400) }
    val epg = remember(catIdx) { mutableStateMapOf<String, List<Program>>() }
    val focusId = remember(catIdx) { channels.firstOrNull { it.id == focusChannel }?.id ?: channels.firstOrNull()?.id }
    var selected by remember { mutableStateOf<Pair<Channel, Program?>?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val windowStart = now - (now % 1_800_000L)   // demi-heure en cours
    val windowEnd = windowStart + WINDOW_H * 3_600_000L
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }

    // Charge le guide des chaînes visibles (et un peu au-delà)
    LaunchedEffect(catIdx, listState.firstVisibleItemIndex) {
        val from = listState.firstVisibleItemIndex
        channels.drop(from).take(14).forEach { ch -> if (ch.id !in epg) epg[ch.id] = Epg.forChannel(playlist, ch) }
    }
    LaunchedEffect(catIdx) {
        val i = channels.indexOfFirst { it.id == focusId }
        if (i > 0) listState.scrollToItem((i - 2).coerceAtLeast(0))
        delay(80); runCatching { firstFocus.requestFocus() }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    MosaicBackground { Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        MosaicHeader("Guide TV", extra = {
            Spacer(Modifier.width(12.dp))
            var cf by remember { mutableStateOf(false) }
            val pill = RoundedCornerShape(50)
            Text(
                "◀  ${cats.getOrNull(catIdx)?.second ?: ""}  ▶", color = Mz.Bg, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title,
                modifier = Modifier.mosaicRing(cf, pill).clip(pill).background(Mz.Pill)
                    .onFocusChanged { cf = it.isFocused }
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) false
                        else when (e.key) {
                            Key.DirectionLeft -> { catIdx = (catIdx - 1 + cats.size) % cats.size; true }
                            Key.DirectionRight -> { catIdx = (catIdx + 1) % cats.size; true }
                            else -> false
                        }
                    }
                    .clickable { catIdx = (catIdx + 1) % cats.size }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Text("◀ ▶ changer de catégorie", color = Mz.Mute, fontSize = 9.sp, modifier = Modifier.padding(start = 10.dp))
        })
        Spacer(Modifier.height(12.dp))

        // Grande tuile : programme sélectionné
        val sel = selected
        Row(
            Modifier.fillMaxWidth().height(96.dp).mosaicAppear(0).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2A1250), Color(0xFF14104A), Color(0xFF0C2B3E))))
                .border(1.dp, Color(0x40A78BFA), RoundedCornerShape(15.dp)).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (sel == null) Text("Choisis un programme", color = Mz.Dim, fontSize = 13.sp)
            else {
                val (ch, p) = sel
                Box(Modifier.size(70.dp, 55.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Color(0xFF7C3CE6), Color(0xFF2A1470)))), contentAlignment = Alignment.Center) {
                    NetImage(ch.logo, initials(ch.name), 60.dp, 42.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ch.name + (p?.let { " · ${hhmm(it.start)} – ${hhmm(it.end)}" + if (it.isNow()) " · en cours" else "" } ?: ""),
                        color = Color(0xFFCFC9EA), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p?.title ?: "Programme non communiqué", color = Mz.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = IrFonts.title,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p != null && p.desc.isNotBlank()) Text(p.desc, color = Mz.Dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p != null) Text(if (p.start > now) "OK : me prévenir" else "OK : regarder", color = Mz.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        BoxWithConstraints(Modifier.fillMaxSize().mosaicAppear(1)) {
            val gridW = maxWidth - CH_COL
            val pxPerMs = gridW.value / (windowEnd - windowStart)
            Column {
                // En-tête des heures
                Row(Modifier.height(20.dp)) {
                    Spacer(Modifier.width(CH_COL))
                    for (h in 0 until WINDOW_H * 2) Text(hhmm(windowStart + h * 1_800_000L), color = Mz.Mute, fontSize = 10.sp, modifier = Modifier.width(gridW / (WINDOW_H * 2)))
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                        itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                            val progs = (epg[ch.id] ?: Epg.cached(ch).orEmpty()).filter { it.end > windowStart && it.start < windowEnd }
                            Row(Modifier.height(44.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.width(CH_COL - 6.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(Mz.Tile).padding(horizontal = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    NetImage(ch.logo, initials(ch.name), 34.dp, 26.dp)
                                    Text(ch.name, color = Mz.Text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.width(gridW).fillMaxHeight()) {
                                    // Une seule liste de cases (case « non communiqué » comprise) : le focus ne saute pas quand le guide arrive
                                    val unknown = progs.isEmpty()
                                    val cells = if (unknown) listOf(Program("Programme non communiqué", windowStart, windowEnd)) else progs
                                    cells.forEachIndexed { pi, p ->
                                        val s = maxOf(p.start, windowStart); val e = minOf(p.end, windowEnd)
                                        val x = ((s - windowStart) * pxPerMs).dp
                                        val w = (((e - s) * pxPerMs).dp - 6.dp).coerceAtLeast(20.dp)
                                        val isFirst = pi == 0 && ch.id == focusId
                                        GuideCell(p.title, if (unknown) "" else "${hhmm(p.start)} – ${hhmm(p.end)}", x, w, isFirst, firstFocus, live = !unknown && p.isNow(),
                                            onFocus = { selected = ch to if (unknown) null else p },
                                            onClick = {
                                                if (unknown || p.isNow() || p.start < now) onPlay(ch, catId)
                                                else { Reminders.add(Reminders.R(ch.id, p.title, p.start)); toast = "Rappel ajouté : ${p.title} à ${hhmm(p.start)}" }
                                            })
                                    }
                                }
                            }
                        }
                    }
                    // Trait rouge « maintenant »
                    Box(Modifier.offset(x = CH_COL + ((now - windowStart) * pxPerMs).dp).width(2.dp).fillMaxHeight()
                        .shadow(6.dp, RoundedCornerShape(1.dp), ambientColor = Mz.Live, spotColor = Mz.Live).background(Mz.Live))
                }
            }
        }
    } }
    toast?.let { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { ToastBox(it) } }
}

@Composable
private fun GuideCell(
    title: String, time: String, x: Dp, w: Dp, first: Boolean, firstFocus: FocusRequester, live: Boolean = false,
    onFocus: () -> Unit, onClick: () -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Column(
        Modifier.offset(x = x).width(w).fillMaxHeight()
            .then(if (first) Modifier.focusRequester(firstFocus) else Modifier)
            .mosaicRing(f, shape)
            .clip(shape)
            .background(if (f) Color.White else if (live) Color(0x388B5CF6) else Mz.Tile)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = if (f) Mz.Ink else Mz.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (time.isNotBlank()) Text(time, color = if (f) Color(0xFF4B4766) else Mz.Mute, fontSize = 8.sp, maxLines = 1)
    }
}
