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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.irpro.tv.R
import com.irpro.tv.data.Parental
import com.irpro.tv.data.Playlist
import com.irpro.tv.data.PlaylistType
import com.irpro.tv.data.Profile
import com.irpro.tv.data.Profiles
import kotlinx.coroutines.delay

/* Écrans des profils et du code parental (mêmes écrans dans les deux thèmes). */

private val BG = Color(0xFF07060D)
private val CARD = Color(0xFF14121F)
private val KEY = Color(0xFF1C1930)
private val TXT = Color(0xFFF5F3FF)
private val DIM = Color(0xFFB4B0C8)
private val CYAN = Color(0xFF22D3EE)
private val INK = Color(0xFF0B0918)

private val AVATARS = listOf(
    listOf(Color(0xFF8B5CF6), Color(0xFF3B1C8C)),
    listOf(Color(0xFF22D3EE), Color(0xFF0E4A66)),
    listOf(Color(0xFF6B6785), Color(0xFF24222F)),
    listOf(Color(0xFFC084FC), Color(0xFF22D3EE)),
)

private fun Modifier.ring(on: Boolean, shape: Shape): Modifier =
    if (!on) this else this.shadow(22.dp, shape, ambientColor = CYAN, spotColor = CYAN).border(4.dp, CYAN, shape).padding(2.dp).border(2.dp, BG, shape)

private val screenBg = Modifier
    .background(Brush.verticalGradient(listOf(Color(0x598B5CF6), Color.Transparent), endY = 700f))
    .background(BG)

@Composable
fun ProfileAvatar(p: Profile, size: Dp, focused: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size / 5)
    Box(
        modifier.ring(focused, shape).size(size).clip(shape).background(Brush.linearGradient(AVATARS[p.color.mod(AVATARS.size)])),
        contentAlignment = Alignment.Center,
    ) {
        Text(p.name.take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
        if (p.locked) Box(
            Modifier.align(Alignment.BottomEnd).padding(size / 16).size(size / 4.2f).background(BG, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("🔒", fontSize = (size.value / 9f).sp) }
    }
}

/** « Qui regarde ? » : choix du profil, et de la playlist de chaque profil. */
@Composable
fun ProfilePickerScreen(
    playlists: List<Playlist>,
    activePlaylistId: String?,
    onPicked: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val rev by Profiles.rev.collectAsState()
    val profiles = remember(rev) { Profiles.all() }
    var focusedId by remember { mutableStateOf(Profiles.current().id) }
    val focusedProfile = profiles.firstOrNull { it.id == focusedId } ?: profiles.first()
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }

    fun pick(p: Profile) { if (p.locked && Parental.hasCode) pinFor = p else onPicked(p) }

    Column(
        Modifier.fillMaxSize().then(screenBg).padding(vertical = 34.dp, horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Image(painterResource(R.drawable.logo), "IR PRO TV", Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
            Text("IR PRO TV", color = TXT, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title, letterSpacing = 1.5.sp)
        }
        Text("Qui regarde ?", color = TXT, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(10.dp)) {
            items(profiles, key = { it.id }) { p ->
                var f by remember { mutableStateOf(false) }
                Column(
                    (if (p.id == Profiles.current().id) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused; if (it.isFocused) focusedId = p.id }
                        .clickable { Sfx.select(); pick(p) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProfileAvatar(p, 110.dp, f)
                    Text(p.name, color = if (f) Color.White else TXT, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title)
                    if (p.kids) Text("Enfants", color = CYAN, fontSize = 10.sp)
                }
            }
            item {
                var f by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier.onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }.clickable { Sfx.select(); adding = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.ring(f, shape).size(110.dp).clip(shape).background(if (f) Color(0x228B5CF6) else Color.Transparent)
                        .border(2.dp, Color(0x4DFFFFFF), shape), contentAlignment = Alignment.Center) {
                        Text("+", color = DIM, fontSize = 42.sp)
                    }
                    Text("Ajouter", color = DIM, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title)
                }
            }
        }
        if (playlists.size > 1) {
            Text("PLAYLIST DU PROFIL ${focusedProfile.name.uppercase()}", color = DIM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            val chosen = focusedProfile.playlistId.takeIf { id -> playlists.any { it.id == id } } ?: activePlaylistId
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(8.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    PlaylistChoice(pl, pl.id == chosen) { Profiles.update(focusedProfile.copy(playlistId = pl.id)) }
                }
            }
            Text("Choisis une playlist ici : elle s'ouvrira avec ce profil.", color = Color(0xFF8E89A8), fontSize = 11.sp)
        }
    }

    pinFor?.let { p ->
        PinDialog(
            title = "Profil ${p.name}", subtitle = "Entre le code parental à 4 chiffres", profile = p,
            onDismiss = { pinFor = null },
            onCode = { code -> if (Parental.check(code)) { pinFor = null; onPicked(p); null } else "Code incorrect" },
        )
    }
    if (adding) AddProfileDialog(onDismiss = { adding = false }) { name, kids, locked ->
        val p = Profiles.add(name, kids, locked); adding = false; focusedId = p.id
    }
}

@Composable
private fun PlaylistChoice(pl: Playlist, selected: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Row(
        Modifier.ring(f, shape).width(220.dp).clip(shape)
            .background(if (selected) Color(0x298B5CF6) else CARD)
            .border(1.dp, if (selected) Color(0xFFA78BFA) else Color(0x1AFFFFFF), shape)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val tag = if (pl.type == PlaylistType.XTREAM) "XTREAM" else "M3U"
        Text(tag, color = if (selected) BG else TXT, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(5.dp))
                .then(if (selected) Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), CYAN))) else Modifier.background(Color(0x1FFFFFFF)))
                .padding(horizontal = 7.dp, vertical = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(pl.name, color = TXT, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (selected) "Choisie pour ce profil" else "OK pour la choisir", color = if (selected) Color(0xFFCFC9EA) else DIM, fontSize = 10.sp)
        }
    }
}

/* ---------------- Code parental ---------------- */

/**
 * Pavé de code à 4 chiffres, en plein écran. onCode renvoie null si le code est accepté,
 * sinon le message d'erreur à afficher. Les touches chiffrées de la télécommande marchent aussi.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    profile: Profile? = null,
    footnote: String = "Code oublié ? Il faut effacer les données de l'appli dans les paramètres Android (les playlists seront à rajouter).",
    onDismiss: () -> Unit,
    onCode: (String) -> String?,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        key(title, subtitle) { PinPad(title, subtitle, profile, footnote, onDismiss, onCode) }
    }
}

@Composable
private fun PinPad(title: String, subtitle: String, profile: Profile?, footnote: String, onDismiss: () -> Unit, onCode: (String) -> String?) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }

    fun type(d: String) {
        if (code.length >= 4) return
        error = null
        code += d
        if (code.length == 4) {
            val c = code
            val err = onCode(c)
            if (err != null) { error = err; code = "" }
        }
    }

    Column(
        Modifier.fillMaxSize().then(screenBg)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val d = when (e.key) {
                    Key.Zero, Key.NumPad0 -> "0"; Key.One, Key.NumPad1 -> "1"; Key.Two, Key.NumPad2 -> "2"
                    Key.Three, Key.NumPad3 -> "3"; Key.Four, Key.NumPad4 -> "4"; Key.Five, Key.NumPad5 -> "5"
                    Key.Six, Key.NumPad6 -> "6"; Key.Seven, Key.NumPad7 -> "7"; Key.Eight, Key.NumPad8 -> "8"
                    Key.Nine, Key.NumPad9 -> "9"; else -> null
                }
                if (d != null) { type(d); true }
                else if (e.key == Key.Back || e.key == Key.Escape) { onDismiss(); true }
                else false
            }
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (profile != null) ProfileAvatar(profile.copy(locked = false), 72.dp)
        else Image(painterResource(R.drawable.logo), "IR PRO TV", Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
        Text(title, color = TXT, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title, textAlign = TextAlign.Center)
        Text(error ?: subtitle, color = if (error != null) Color(0xFFFF8A8A) else DIM, fontSize = 14.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.size(48.dp, 56.dp).clip(RoundedCornerShape(11.dp)).background(CARD)
                        .border(1.dp, if (i == code.length) Color(0xFFA78BFA) else Color(0x1FFFFFFF), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(13.dp).background(if (i < code.length) CYAN else Color.Transparent, CircleShape)) }
            }
        }
        Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Retour", "0", "Effacer")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        PinKey(k, Modifier.weight(1f), if (k == "1") Modifier.focusRequester(first) else Modifier) {
                            when (k) {
                                "Retour" -> onDismiss()
                                "Effacer" -> { code = code.dropLast(1); error = null }
                                else -> type(k)
                            }
                        }
                    }
                }
            }
        }
        Text(footnote, color = Color(0xFFA29DBB), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(520.dp))
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier, focusMod: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.then(focusMod).ring(f, shape).height(42.dp).clip(shape).background(if (f) Color.White else KEY)
            .onFocusChanged { if (it.isFocused && !f) Sfx.move(); f = it.isFocused }
            .clickable { Sfx.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (f) INK else if (label.length > 1) DIM else TXT, fontSize = if (label.length > 1) 12.sp else 17.sp,
            fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title)
    }
}

/* ---------------- Nouveau profil ---------------- */

@Composable
private fun AddProfileDialog(onDismiss: () -> Unit, onCreate: (String, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kids by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    val f = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { f.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(460.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF12101D))
                .border(1.dp, Color(0x59A78BFA), RoundedCornerShape(18.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Nouveau profil", color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = IrFonts.title)
            IrField(name, { name = it.take(16) }, "Nom du profil", "ex. Salon, Chambre, Enfants", Modifier.fillMaxWidth().focusRequester(f), last = true)
            OptionLine("Profil Enfants (contenu adulte toujours masqué)", kids) { kids = !kids }
            if (Parental.hasCode) OptionLine("Protégé par le code parental", locked) { locked = !locked }
            else Text("Pour protéger un profil par un code, crée d'abord le code dans Paramètres › Contrôle parental.", color = DIM, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IrButton("Créer le profil", enabled = name.isNotBlank()) { onCreate(name, kids, locked && Parental.hasCode) }
                IrButton("Annuler", primary = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun OptionLine(label: String, value: Boolean, onToggle: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (f) Color(0x338B5CF6) else CARD)
            .border(if (f) 2.dp else 0.dp, if (f) CYAN else Color.Transparent, shape)
            .onFocusChanged { f = it.isFocused }.clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TXT, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (value) "Oui" else "Non", color = if (value) CYAN else DIM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
