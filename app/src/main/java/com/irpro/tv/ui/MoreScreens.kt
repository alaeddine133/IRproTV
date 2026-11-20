package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.BuildConfig
import com.irpro.tv.data.Catalog
import com.irpro.tv.data.CheckResult
import com.irpro.tv.data.LoadState
import com.irpro.tv.data.PhoneForm
import com.irpro.tv.data.PhoneLink
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.PlaylistCheck
import com.irpro.tv.data.PlaylistRules
import com.irpro.tv.data.PlaylistType
import com.irpro.tv.data.UpdateInfo
import com.irpro.tv.data.Updater
import com.irpro.tv.data.UserData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ================= Ajout avec le téléphone (QR code) ================= */

@Composable
fun PhoneAddScreen(onBack: () -> Unit, onSaved: (Playlist) -> Unit) {
    BackHandler(onBack = onBack)
    var status by remember { mutableStateOf<String?>(null) }
    var received by remember { mutableStateOf<Playlist?>(null) }
    val link = remember {
        PhoneLink { f: PhoneForm ->
            status = "Formulaire reçu, vérification…"
            val r = if (f.type == "m3u") PlaylistCheck.checkM3u(f.url) else PlaylistCheck.checkXtream(f.server, f.user, f.pass)
            when (r) {
                is CheckResult.Ok -> {
                    received = if (f.type == "m3u") Playlist(name = f.name.ifBlank { "Ma TV" }, type = PlaylistType.M3U, url = PlaylistRules.normalizeUrl(f.url))
                    else Playlist(name = f.name.ifBlank { "Ma TV" }, type = PlaylistType.XTREAM, server = r.server, username = f.user.trim(), password = f.pass.trim(), expiresAt = r.expiresAt)
                    null
                }
                is CheckResult.Error -> { status = r.message; r.message }
            }
        }
    }
    val address = remember { link.start() }
    DisposableEffect(Unit) { onDispose { link.stop() } }
    LaunchedEffect(received) { received?.let { delay(400); onSaved(it) } }
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { back.requestFocus() } }

    Row(Modifier.fillMaxSize().background(IrBackground).padding(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Avec ton téléphone", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            listOf(
                "1. Connecte ton téléphone au même Wi-Fi que la box",
                "2. Scanne le QR code avec l'appareil photo",
                "3. Remplis le formulaire sur ton téléphone et appuie sur « Envoyer à la TV »",
            ).forEach { Text(it, color = Color(0xFFD5DBEA), fontSize = 17.sp, modifier = Modifier.padding(vertical = 4.dp)) }
            Spacer(Modifier.height(10.dp))
            if (address != null) Text("Ou tape dans le navigateur : $address", color = IrColors.Cyan, fontSize = 15.sp)
            status?.let { Spacer(Modifier.height(12.dp)); Text(it, color = if (received != null) IrColors.Success else IrColors.Cyan, fontSize = 16.sp) }
            Spacer(Modifier.height(20.dp))
            IrButton("Retour", primary = false, modifier = Modifier.focusRequester(back), onClick = onBack)
        }
        Spacer(Modifier.width(30.dp))
        Box(Modifier.size(300.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp), contentAlignment = Alignment.Center) {
            if (address != null) {
                val bmp = remember(address) { PhoneLink.qr(address).asImageBitmap() }
                Image(bmp, "QR code", Modifier.fillMaxSize())
            } else Text("Box non connectée au réseau", color = IrColors.Night, fontSize = 15.sp)
        }
    }
}

/* ================= Recherche ================= */

@Composable
fun SearchScreen(
    catalog: Catalog,
    onChannel: (String) -> Unit,
    onMovie: (String) -> Unit,
    onSeries: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var q by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    // Noms « simplifiés » calculés une fois, hors de l'écran (rapide même avec 50 000 titres)
    val index by androidx.compose.runtime.produceState<List<List<String>>?>(null, catalog) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            listOf(catalog.channels.map { normName(it.name) }, catalog.movies.map { normName(it.name) }, catalog.series.map { normName(it.name) })
        }
    }
    var ch by remember { mutableStateOf(emptyList<com.irpro.tv.data.Channel>()) }
    var mv by remember { mutableStateOf(emptyList<com.irpro.tv.data.Movie>()) }
    var se by remember { mutableStateOf(emptyList<com.irpro.tv.data.Series>()) }
    val query = normName(q.trim())
    LaunchedEffect(query, index) {
        val idx = index ?: return@LaunchedEffect
        delay(250)
        if (query.length < 2) { ch = emptyList(); mv = emptyList(); se = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val c = catalog.channels.filterIndexed { i, _ -> idx[0][i].contains(query) }.take(40)
            val m = catalog.movies.filterIndexed { i, _ -> idx[1][i].contains(query) }.take(40)
            val s2 = catalog.series.filterIndexed { i, _ -> idx[2][i].contains(query) }.take(40)
            Triple(c, m, s2)
        }.let { (c, m, s2) -> ch = c; mv = m; se = s2 }
    }

    Column(Modifier.fillMaxSize().background(IrColors.Night).padding(horizontal = 36.dp, vertical = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IrIconView(IrIcon.SEARCH, IrColors.Cyan, 26.dp)
            Spacer(Modifier.width(12.dp))
            IrField(q, { q = it }, "Rechercher une chaîne, un film, une série", "ex. TF1, Avatar…", Modifier.weight(1f).focusRequester(field), last = true)
        }
        Spacer(Modifier.height(10.dp))
        if (query.length < 2) Text("Tape au moins 2 lettres.", color = IrColors.TextDim)
        else if (ch.isEmpty() && mv.isEmpty() && se.isEmpty()) Text("Aucun résultat pour « $q ».", color = IrColors.TextDim)
        LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
            if (ch.isNotEmpty()) item {
                Text("Chaînes · ${ch.size}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ch.distinctBy { it.id }, key = { it.id }) { c ->
                        var f by remember { mutableStateOf(false) }
                        Column(
                            Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).background(if (f) IrColors.Violet else IrColors.Card)
                                .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(10.dp))
                                .onFocusChanged { f = it.isFocused }.clickable { onChannel(c.id) }.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            NetImage(c.logo, initials(c.name), 90.dp, 56.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(c.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (mv.isNotEmpty()) item {
                Text("Films · ${mv.size}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mv.distinctBy { it.id }, key = { it.id }) { m -> PosterCard(Poster(m.id, m.name, m.poster)) { onMovie(m.id) } }
                }
            }
            if (se.isNotEmpty()) item {
                Text("Séries · ${se.size}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(se.distinctBy { it.id }, key = { it.id }) { s -> PosterCard(Poster(s.id, s.name, s.cover)) { onSeries(s.id) } }
                }
            }
        }
    }
}

private val MARKS = Regex("\\p{Mn}+")
fun normName(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(MARKS, "").lowercase()

/* ================= Paramètres ================= */

@Composable
fun SettingsScreen(
    playlist: Playlist,
    playlistsCount: Int,
    state: LoadState,
    onPlaylists: () -> Unit,
    onRefresh: () -> Unit,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val s by UserData.settings.collectAsState()
    val sections = listOf("Playlists", "Mises à jour", "Langue et priorité", "Lecteur", "Guide TV", "Apparence et sons", "À propos")
    var sec by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().background(IrColors.Night)) {
        Column(Modifier.width(280.dp).fillMaxHeight().background(Color(0xFF061026)).padding(vertical = 20.dp)) {
            Text("Paramètres", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 22.dp, bottom = 14.dp))
            sections.forEachIndexed { i, label ->
                var f by remember { mutableStateOf(false) }
                Text(label, color = Color.White, fontSize = 15.sp,
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth()
                        .background(if (i == sec) IrColors.Violet else if (f) Color(0x332E66A8) else Color.Transparent)
                        .then(if (f) Modifier.border(2.dp, IrColors.Cyan) else Modifier)
                        .onFocusChanged { f = it.isFocused; if (it.isFocused) sec = i }
                        .clickable { sec = i }
                        .padding(horizontal = 22.dp, vertical = 11.dp))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sections[sec], color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            when (sec) {
                0 -> {
                    InfoLine("Playlist active", "${playlist.name} · ${if (playlist.type == PlaylistType.XTREAM) "Xtream" else "M3U"}")
                    InfoLine("Abonnement", subscriptionText(playlist))
                    InfoLine("Playlists enregistrées", "$playlistsCount")
                    ActionRow("Gérer mes playlists (ajouter, changer, supprimer)", onPlaylists)
                }
                1 -> {
                    InfoLine("Dernière mise à jour du contenu", state.catalog?.updatedAt?.let { java.text.SimpleDateFormat("dd/MM 'à' HH:mm", java.util.Locale.FRANCE).format(java.util.Date(it)) } ?: "—")
                    ActionRow(if (state.loading) "Mise à jour en cours… ${state.step}" else "Mettre à jour le contenu maintenant") { if (!state.loading) onRefresh() }
                    ToggleRow("Mettre à jour le contenu à chaque lancement", s.refreshOnLaunch) { v -> UserData.updateSettings { it.copy(refreshOnLaunch = v) } }
                    InfoLine("Version installée", "IR PRO TV ${BuildConfig.VERSION_NAME}")
                    ActionRow("Vérifier si une nouvelle version existe", onCheckUpdate)
                }
                2 -> {
                    ToggleRow("Contenu français en premier", s.frenchFirst) { v -> UserData.updateSettings { it.copy(frenchFirst = v) }; com.irpro.tv.data.ContentRepository.resort(ctx) }
                    ChoiceRow("Langue audio préférée", listOf("fr" to "Français", "en" to "Anglais", "ar" to "Arabe", "es" to "Espagnol"), s.audioLang) { v -> UserData.updateSettings { it.copy(audioLang = v) } }
                }
                3 -> {
                    ChoiceRow("Lecteur", listOf("exo" to "Rapide (ExoPlayer)", "vlc" to "Compatible (VLC)"), s.engine) { v -> UserData.updateSettings { it.copy(engine = v) } }
                    Text("Si une chaîne refuse de se lancer, IR PRO TV essaie automatiquement l'autre lecteur.", color = IrColors.TextDim, fontSize = 13.sp)
                    ChoiceRow("Mémoire tampon", listOf("normal" to "Normale", "large" to "Grande (connexion instable)"), s.buffer) { v -> UserData.updateSettings { it.copy(buffer = v) } }
                    ToggleRow("Sous-titres activés par défaut", s.subtitlesDefault) { v -> UserData.updateSettings { it.copy(subtitlesDefault = v) } }
                    ChoiceRow("Taille des sous-titres", listOf("0" to "Petite", "1" to "Moyenne", "2" to "Grande"), s.subtitleSize.toString()) { v -> UserData.updateSettings { it.copy(subtitleSize = v.toInt()) } }
                    ToggleRow("Épisode suivant automatique", s.autoNextEpisode) { v -> UserData.updateSettings { it.copy(autoNextEpisode = v) } }
                    ChoiceRow("Décalage du son (lecteur compatible)", listOf("-500" to "−0,5 s", "-200" to "−0,2 s", "0" to "Aucun", "200" to "+0,2 s", "500" to "+0,5 s"), s.audioDelayMs.toString()) { v -> UserData.updateSettings { it.copy(audioDelayMs = v.toInt()) } }
                }
                4 -> {
                    var link by remember { mutableStateOf(s.epgUrl) }
                    IrField(link, { link = it }, "Lien EPG (XMLTV) personnalisé", "laisser vide = guide du fournisseur", Modifier.fillMaxWidth(), last = true,
                        onDone = { UserData.updateSettings { it.copy(epgUrl = link.trim()) }; com.irpro.tv.data.Epg.clear() })
                    ActionRow("Enregistrer le lien EPG") { UserData.updateSettings { it.copy(epgUrl = link.trim()) }; com.irpro.tv.data.Epg.clear() }
                    ChoiceRow("Décalage horaire du guide", listOf("-2" to "−2 h", "-1" to "−1 h", "0" to "Aucun", "1" to "+1 h", "2" to "+2 h"), s.epgOffsetHours.toString()) { v ->
                        UserData.updateSettings { it.copy(epgOffsetHours = v.toInt()) }; com.irpro.tv.data.Epg.clear()
                    }
                }
                5 -> {
                    ToggleRow("Sons de navigation", s.navSounds) { v -> UserData.updateSettings { it.copy(navSounds = v) } }
                    ActionRow("Revoir l'intro complète au prochain lancement") { com.irpro.tv.IntroPrefs.reset(ctx); note = "C'est noté : intro complète au prochain lancement." }
                    note?.let { Text(it, color = IrColors.Cyan, fontSize = 14.sp) }
                }
                else -> {
                    InfoLine("Application", "IR PRO TV ${BuildConfig.VERSION_NAME}")
                    InfoLine("Serveur", if (playlist.type == PlaylistType.XTREAM) playlist.server else "Liste M3U")
                    InfoLine("Lecteurs", "ExoPlayer (avec décodeur audio FFmpeg) · VLC")
                    InfoLine("Contenu", "${formatCount(state.catalog?.channels?.size ?: 0)} chaînes · ${formatCount(state.catalog?.movies?.size ?: 0)} films · ${formatCount(state.catalog?.series?.size ?: 0)} séries")
                }
            }
        }
    }
}


@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(IrColors.Card).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, color = Color(0xFFD5DBEA), fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = IrColors.Cyan, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun rowMod(f: Boolean) = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    .background(if (f) IrColors.Violet else IrColors.Card)
    .border(if (f) 2.dp else 0.dp, if (f) IrColors.Cyan else Color.Transparent, RoundedCornerShape(10.dp))

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Row(rowMod(f).onFocusChanged { f = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp)) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("›", color = IrColors.Cyan, fontSize = 18.sp)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var f by remember { mutableStateOf(false) }
    Row(rowMod(f).onFocusChanged { f = it.isFocused }.clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (value) "Activé" else "Désactivé", color = if (value) IrColors.Cyan else IrColors.TextDim, fontSize = 15.sp)
    }
}

/** OK fait défiler les choix. */
@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    var f by remember { mutableStateOf(false) }
    val i = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    Row(rowMod(f).onFocusChanged { f = it.isFocused }.clickable { onChange(options[(i + 1) % options.size].first) }.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("‹ ${options[i].second} ›", color = IrColors.Cyan, fontSize = 15.sp)
    }
}

/* ================= Fenêtre de mise à jour de l'appli ================= */

@Composable
fun UpdateDialog(info: UpdateInfo, onLater: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { f.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onLater) {
        Column(Modifier.width(520.dp).background(IrColors.Card, RoundedCornerShape(18.dp)).border(1.dp, IrColors.Cyan, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Text("Nouvelle version ${info.versionName}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Tu as la ${BuildConfig.VERSION_NAME}", color = IrColors.TextDim, fontSize = 14.sp)
            if (info.notes.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(info.notes, color = Color(0xFFD5DBEA), fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(14.dp))
            progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(5.dp), color = IrColors.Cyan, trackColor = Color(0xFF1D2B55)); Spacer(Modifier.height(10.dp)) }
            msg?.let { Text(it, color = IrColors.Cyan, fontSize = 14.sp); Spacer(Modifier.height(10.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IrButton(if (progress == null) "Installer" else "Téléchargement…", modifier = Modifier.focusRequester(f)) {
                    if (progress != null) return@IrButton
                    if (!Updater.canInstall(ctx)) {
                        msg = "Autorise IR PRO TV à installer des applis (une seule fois), puis reviens et appuie sur Installer."
                        Updater.openInstallPermission(ctx)
                        return@IrButton
                    }
                    scope.launch {
                        progress = 0f
                        runCatching { Updater.download(ctx, info) { p -> progress = p } }
                            .onSuccess { progress = null; msg = if (Updater.install(ctx, it)) "Appuie sur « Installer » dans la fenêtre d'Android." else "Impossible d'ouvrir l'installateur d'Android sur cette box." }
                            .onFailure { progress = null; msg = "Téléchargement impossible. Réessaie plus tard." }
                    }
                }
                IrButton("Plus tard", primary = false, onClick = onLater)
            }
        }
    }
}

