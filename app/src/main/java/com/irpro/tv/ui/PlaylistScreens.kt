package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.BuildConfig
import com.irpro.tv.R
import com.irpro.tv.data.CheckResult
import com.irpro.tv.data.LoadState
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.PlaylistCheck
import com.irpro.tv.data.PlaylistRules
import com.irpro.tv.data.PlaylistType
import kotlinx.coroutines.launch

@Composable
private fun Brand(small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(if (small) 30.dp else 40.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                append("IR PRO ")
                withStyle(SpanStyle(color = IrColors.Cyan)) { append("TV") }
            },
            color = Color.White, fontSize = if (small) 16.sp else 20.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(IrBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) { content() }
}

/* ---------------- Bienvenue : 3 tuiles ---------------- */

@Composable
fun WelcomeScreen(
    canGoBack: Boolean,
    onBack: () -> Unit,
    onXtream: () -> Unit,
    onM3u: () -> Unit,
) {
    if (canGoBack) BackHandler(onBack = onBack)
    var note by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    ScreenFrame {
        Column {
            Brand()
            Spacer(Modifier.height(28.dp))
            Text(if (canGoBack) "Ajouter une playlist" else "Bienvenue", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Ajoute ta playlist pour commencer", color = IrColors.TextDim, fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                FocusTile(
                    modifier = Modifier.weight(1f).heightIn(min = 170.dp).focusRequester(first),
                    onClick = onXtream,
                ) { f ->
                    TileText("⚿", "Xtream Codes", "Serveur, utilisateur, mot de passe", f, badge = "Recommandé")
                }
                FocusTile(modifier = Modifier.weight(1f).heightIn(min = 170.dp), onClick = onM3u) { f ->
                    TileText("∞", "Lien M3U", "Colle l'adresse de ta liste", f)
                }
                FocusTile(
                    modifier = Modifier.weight(1f).heightIn(min = 170.dp),
                    onClick = { note = "L'ajout avec le téléphone (QR code) arrive dans une prochaine version." },
                ) { f ->
                    TileText("▩", "Avec mon téléphone", "Scanne un QR code et tape tout sur ton téléphone", f, badge = "Bientôt")
                }
            }
            note?.let {
                Spacer(Modifier.height(18.dp))
                Text(it, color = IrColors.Cyan, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TileText(icon: String, title: String, sub: String, focused: Boolean, badge: String? = null) {
    Text(icon, color = IrColors.Cyan, fontSize = 28.sp)
    Spacer(Modifier.height(10.dp))
    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(sub, color = if (focused) Color(0xFFE4DCFF) else IrColors.TextDim, fontSize = 14.sp)
    if (badge != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            badge, color = IrColors.CyanDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(IrColors.Cyan, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/* ---------------- Formulaires ---------------- */

@Composable
private fun FormLayout(
    title: String,
    onBack: () -> Unit,
    tip: String,
    fields: @Composable () -> Unit,
) {
    BackHandler(onBack = onBack)
    ScreenFrame {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("←  ", color = Color.White, fontSize = 20.sp)
                Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Brand(small = true)
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) { fields() }
                Column(
                    Modifier
                        .weight(1f)
                        .background(IrColors.Card, RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0x5522D3EE), RoundedCornerShape(18.dp))
                        .padding(22.dp)
                ) {
                    Text("Astuce", color = IrColors.Cyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(tip, color = Color(0xFFD5DBEA), fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(busy: Boolean, error: String?) {
    if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = IrColors.Cyan, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text("Vérification en cours…", color = IrColors.Cyan, fontSize = 15.sp)
    }
    if (!busy && error != null) Text(error, color = IrColors.Danger, fontSize = 15.sp)
}

@Composable
fun XtreamScreen(onBack: () -> Unit, onSaved: (Playlist) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Ma TV") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    val connect: () -> Unit = {
        if (!busy) scope.launch {
            busy = true; error = null
            when (val r = PlaylistCheck.checkXtream(server, user, pass)) {
                is CheckResult.Ok -> onSaved(
                    Playlist(
                        name = name.ifBlank { "Ma TV" }.trim(), type = PlaylistType.XTREAM,
                        server = r.server, username = user.trim(), password = pass.trim(), expiresAt = r.expiresAt,
                    )
                )
                is CheckResult.Error -> error = r.message
            }
            busy = false
        }
    }

    FormLayout(
        title = "Xtream Codes",
        onBack = onBack,
        tip = "Ces informations te sont données par ton fournisseur IPTV. L'adresse du serveur ressemble à " +
            "« http://monserveur.com:8080 ». IR PRO TV vérifie tes identifiants avant d'enregistrer la playlist.",
    ) {
        IrField(name, { name = it }, "Nom de la playlist", "Ma TV", Modifier.fillMaxWidth().focusRequester(first))
        IrField(server, { server = it }, "Serveur", "http://monserveur.com:8080", Modifier.fillMaxWidth(), keyboard = KeyboardType.Uri)
        IrField(user, { user = it }, "Nom d'utilisateur", "", Modifier.fillMaxWidth())
        IrField(pass, { pass = it }, "Mot de passe", "", Modifier.fillMaxWidth(), password = true, last = true, onDone = connect)
        Spacer(Modifier.height(4.dp))
        IrButton("Connexion", enabled = !busy, onClick = connect)
        StatusLine(busy, error)
    }
}

@Composable
fun M3uScreen(onBack: () -> Unit, onSaved: (Playlist) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Ma TV") }
    var link by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }

    val connect: () -> Unit = {
        if (!busy) scope.launch {
            busy = true; error = null
            when (val r = PlaylistCheck.checkM3u(link)) {
                is CheckResult.Ok -> onSaved(
                    Playlist(name = name.ifBlank { "Ma TV" }.trim(), type = PlaylistType.M3U, url = PlaylistRules.normalizeUrl(link))
                )
                is CheckResult.Error -> error = r.message
            }
            busy = false
        }
    }

    FormLayout(
        title = "Lien M3U",
        onBack = onBack,
        tip = "Colle le lien complet donné par ton fournisseur, par exemple " +
            "« http://monserveur.com/get.php?username=…&password=…&type=m3u_plus ». " +
            "Si ton fournisseur te donne aussi des identifiants Xtream, préfère Xtream : tu auras les affiches et le guide TV.",
    ) {
        IrField(name, { name = it }, "Nom de la playlist", "Ma TV", Modifier.fillMaxWidth().focusRequester(first))
        IrField(link, { link = it }, "Lien de la liste M3U", "http://…", Modifier.fillMaxWidth(), keyboard = KeyboardType.Uri, last = true, onDone = connect)
        Spacer(Modifier.height(4.dp))
        IrButton("Ajouter", enabled = !busy, onClick = connect)
        StatusLine(busy, error)
    }
}

/* ---------------- Chargement + playlist prête (en attendant l'accueil de l'étape 5) ---------------- */

@Composable
private fun CountLine(label: String, count: Int?, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        val mark = when {
            count != null -> "✓"
            active -> "…"
            else -> "·"
        }
        Text(mark, color = if (count != null) IrColors.Success else IrColors.Cyan, fontSize = 18.sp, modifier = Modifier.width(28.dp))
        Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.width(190.dp))
        Text(count?.let { formatCount(it) } ?: "", color = IrColors.Cyan, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun formatCount(n: Int): String = String.format(java.util.Locale.FRANCE, "%,d", n).replace(' ', ' ').replace(' ', ' ')

@Composable
fun LoadingScreen(state: LoadState, onRetry: () -> Unit, onPlaylists: () -> Unit) {
    val retry = remember { FocusRequester() }
    ScreenFrame {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                if (state.error == null) "Chargement de ta playlist" else "Chargement impossible",
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            if (state.error == null) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = IrColors.Cyan, strokeWidth = 3.dp, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(state.step, color = IrColors.Cyan, fontSize = 15.sp)
            } else Text(state.error, color = IrColors.Danger, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            Column {
                CountLine("Chaînes en direct", state.liveCount, state.loading)
                CountLine("Films", state.movieCount, state.loading && state.liveCount != null)
                CountLine("Séries", state.seriesCount, state.loading && state.movieCount != null)
            }
            if (state.error != null) {
                LaunchedEffect(Unit) { retry.requestFocus() }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IrButton("Réessayer", modifier = Modifier.focusRequester(retry), onClick = onRetry)
                    IrButton("Mes playlists", primary = false, onClick = onPlaylists)
                }
            }
        }
    }
}

@Composable
fun PlaylistReadyScreen(
    playlist: Playlist,
    count: Int,
    state: LoadState,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    LaunchedEffect(playlist.id) { first.requestFocus() }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = state.catalog

    ScreenFrame {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(84.dp))
            Spacer(Modifier.height(10.dp))
            Text(playlist.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            val kind = if (playlist.type == PlaylistType.XTREAM) "Xtream" else "M3U"
            val exp = playlist.expiresAt?.let { " · expire le ${PlaylistRules.formatDate(it)}" } ?: ""
            val many = if (count > 1) " · $count playlists enregistrées" else ""
            Text("$kind$exp$many", color = IrColors.TextDim, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            if (c != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatBox("Chaînes", c.channels.size)
                    StatBox("Films", c.movies.size)
                    StatBox("Séries", c.series.size)
                }
                Spacer(Modifier.height(14.dp))
                val fr = c.liveCategories.filter { it.isFrench }
                Text(
                    if (fr.isNotEmpty()) "En premier (français) : " + fr.take(5).joinToString(" · ") { it.name } +
                        (if (fr.size > 5) " · +${fr.size - 5}" else "")
                    else "Aucune catégorie française repérée dans cette playlist.",
                    color = Color(0xFFD5DBEA), fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                val upd = java.text.SimpleDateFormat("dd/MM 'à' HH:mm", java.util.Locale.FRANCE).format(java.util.Date(c.updatedAt))
                Text("Dernière mise à jour : $upd", color = IrColors.TextDim, fontSize = 13.sp)
            }
            if (state.loading) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = IrColors.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mise à jour en cours · ${state.step}", color = IrColors.Cyan, fontSize = 13.sp)
                }
            }
            state.error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = IrColors.Danger, fontSize = 14.sp) }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IrButton("Mettre à jour maintenant", modifier = Modifier.focusRequester(first), enabled = !state.loading, onClick = onRefresh)
                IrButton("Ajouter une playlist", primary = false, onClick = onAdd)
                IrButton(
                    if (confirmDelete) "Confirmer la suppression" else "Supprimer",
                    primary = false,
                    onClick = { if (confirmDelete) { confirmDelete = false; onDelete() } else confirmDelete = true },
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Paramètres · Mes playlists", color = Color(0xFFC9C9D1), fontSize = 13.sp)
            Text("Version ${BuildConfig.VERSION_NAME}", color = IrColors.TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatBox(label: String, n: Int) {
    Column(
        Modifier
            .background(IrColors.Card, RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x3322D3EE), RoundedCornerShape(14.dp))
            .padding(horizontal = 26.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(formatCount(n), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = IrColors.Cyan, fontSize = 14.sp)
    }
}
