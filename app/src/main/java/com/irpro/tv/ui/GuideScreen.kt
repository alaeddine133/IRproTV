package com.irpro.tv.ui

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
    val now = System.currentTimeMillis()
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

    Column(Modifier.fillMaxSize().background(IrColors.Night).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IrIconView(IrIcon.GUIDE, IrColors.Cyan, 26.dp)
            Spacer(Modifier.width(10.dp))
            Text("Guide TV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(18.dp))
            IrButton("◀  ${cats.getOrNull(catIdx)?.second ?: ""}  ▶", primary = false, onClick = { catIdx = (catIdx + 1) % cats.size })
            Spacer(Modifier.weight(1f))
            Text("Aujourd'hui · ${hhmm(now)}", color = IrColors.TextDim, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1.7f).fillMaxHeight()) {
                val gridW = maxWidth - CH_COL
                val pxPerMs = gridW.value / (windowEnd - windowStart)
                Column {
                    // En-tête des heures
                    Row(Modifier.height(24.dp)) {
                        Spacer(Modifier.width(CH_COL))
                        for (h in 0 until WINDOW_H * 2) Text(hhmm(windowStart + h * 1_800_000L), color = IrColors.TextDim, fontSize = 12.sp, modifier = Modifier.width(gridW / (WINDOW_H * 2)))
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState) {
                            itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                                val progs = (epg[ch.id] ?: Epg.cached(ch).orEmpty()).filter { it.end > windowStart && it.start < windowEnd }
                                Row(Modifier.height(44.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${ch.num}  ${ch.name}", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(CH_COL).fillMaxHeight().padding(end = 4.dp).background(Color(0xFF12204A), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 11.dp))
                                    Box(Modifier.width(gridW).fillMaxHeight()) {
                                        if (progs.isEmpty()) {
                                            GuideCell("Programme non communiqué", 0.dp, gridW, ch.id == focusId, firstFocus,
                                                onFocus = { selected = ch to null }, onClick = { onPlay(ch, catId) })
                                        } else progs.forEachIndexed { pi, p ->
                                            val s = maxOf(p.start, windowStart); val e = minOf(p.end, windowEnd)
                                            val x = ((s - windowStart) * pxPerMs).dp
                                            val w = (((e - s) * pxPerMs).dp - 3.dp).coerceAtLeast(20.dp)
                                            val isFirst = pi == 0 && ch.id == focusId
                                            GuideCell(p.title, x, w, isFirst, firstFocus, live = p.isNow(),
                                                onFocus = { selected = ch to p },
                                                onClick = {
                                                    if (p.isNow() || p.start < now) onPlay(ch, catId)
                                                    else { Reminders.add(Reminders.R(ch.id, p.title, p.start)); toast = "Rappel ajouté : ${p.title} à ${hhmm(p.start)}" }
                                                })
                                        }
                                    }
                                }
                            }
                        }
                        // Trait « maintenant »
                        Box(Modifier.offset(x = CH_COL + ((now - windowStart) * pxPerMs).dp).width(2.dp).fillMaxHeight().background(IrColors.Cyan))
                    }
                }
            }
            // Fiche du programme
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp)
                    .background(Color(0xFF12204A), RoundedCornerShape(14.dp)).border(1.dp, Color(0x5922D3EE), RoundedCornerShape(14.dp)).padding(16.dp)
            ) {
                val sel = selected
                if (sel == null) Text("Choisis un programme", color = IrColors.TextDim)
                else {
                    val (ch, p) = sel
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NetImage(ch.logo, initials(ch.name), 60.dp, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(ch.name, color = IrColors.Cyan, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(p?.title ?: "Programme non communiqué", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    if (p != null) {
                        Text("${hhmm(p.start)} – ${hhmm(p.end)}" + if (p.isNow()) " · en cours" else "", color = IrColors.TextDim, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(p.desc.ifBlank { "Pas de résumé." }, color = Color(0xFFCCD2E0), fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(10.dp))
                        Text(if (p.start > now) "OK : me prévenir quand ça commence" else "OK : regarder", color = IrColors.Cyan, fontSize = 13.sp)
                    }
                }
            }
        }
    }
    toast?.let { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { ToastBox(it) } }
}

@Composable
private fun GuideCell(
    title: String, x: Dp, w: Dp, first: Boolean, firstFocus: FocusRequester, live: Boolean = false,
    onFocus: () -> Unit, onClick: () -> Unit,
) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.offset(x = x).width(w).fillMaxHeight()
            .then(if (first) Modifier.focusRequester(firstFocus) else Modifier)
            .clip(RoundedCornerShape(6.dp))
            .background(if (f) IrColors.Violet else if (live) Color(0xFF1C2E66) else Color(0xFF16244F))
            .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(6.dp))
            .onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

