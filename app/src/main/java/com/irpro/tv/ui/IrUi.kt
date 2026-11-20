package com.irpro.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Thème actif : Thème 1 · Classique (false) ou Thème 2 · Pro (true). Changé dans Paramètres > Thème. */
object ThemeState {
    @Volatile var pro: Boolean = false
}

/** Couleurs d'IR PRO TV, selon le thème actif. */
object IrColors {
    private val pro: Boolean get() = ThemeState.pro
    val Night: Color get() = if (pro) Color(0xFF07060D) else Color(0xFF0A1430)
    val Black: Color get() = if (pro) Color(0xFF07060D) else Color(0xFF060607)
    val Card: Color get() = if (pro) Color(0xFF14121F) else Color(0xFF12204A)
    val Panel: Color get() = if (pro) Color(0xFF0D0B16) else Color(0xFF061026)
    val Violet: Color get() = if (pro) Color(0xFF8B5CF6) else Color(0xFF7C3AED)
    val VioletLight = Color(0xFFA78BFA)
    val Cyan = Color(0xFF22D3EE)
    val CyanDark = Color(0xFF04342C)
    val TextDim: Color get() = if (pro) Color(0xFFB4B0C8) else Color(0xFF9AA3B5)
    val Text: Color get() = if (pro) Color(0xFFF5F3FF) else Color.White
    val Danger = Color(0xFFFF8A8A)
    val Success = Color(0xFF5EEAD4)
}

val IrBackground: Brush
    get() = if (ThemeState.pro) Brush.radialGradient(listOf(Color(0x338B5CF6), Color(0xFF07060D)), center = androidx.compose.ui.geometry.Offset.Zero, radius = 1600f)
    else Brush.verticalGradient(listOf(Color(0xFF0C1838), Color(0xFF0A1430), Color(0xFF070E24)))

/** Polices : Inter (texte) et Poppins (titres) pour le Thème 2 ; police système pour le Thème 1. */
object IrFonts {
    private val inter = androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(com.irpro.tv.R.font.inter_regular, FontWeight.Normal),
        androidx.compose.ui.text.font.Font(com.irpro.tv.R.font.inter_medium, FontWeight.Medium),
        androidx.compose.ui.text.font.Font(com.irpro.tv.R.font.inter_semibold, FontWeight.SemiBold),
        androidx.compose.ui.text.font.Font(com.irpro.tv.R.font.inter_bold, FontWeight.Bold),
    )
    private val poppins = androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(com.irpro.tv.R.font.poppins_bold, FontWeight.Bold),
        androidx.compose.ui.text.font.Font(com.irpro.tv.R.font.poppins_bold, FontWeight.SemiBold),
    )
    val body get() = if (ThemeState.pro) inter else androidx.compose.ui.text.font.FontFamily.Default
    val title get() = if (ThemeState.pro) poppins else androidx.compose.ui.text.font.FontFamily.Default
}

/** Grande tuile façon console : violette + contour cyan + légère mise en avant quand elle est sélectionnée. */
@Composable
fun FocusTile(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable ColumnScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "tile")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(if (focused) IrColors.Violet else IrColors.Card)
            .border(if (focused) 3.dp else 1.dp, if (focused) IrColors.Cyan else Color(0x22FFFFFF), shape)
            .onFocusChanged { if (it.isFocused && !focused) Sfx.move(); focused = it.isFocused }
            .clickable { Sfx.select(); onClick() }
            .padding(20.dp),
    ) { content(focused) }
}

/** Bouton pilule : cyan (principal) ou contour (secondaire). */
@Composable
fun IrButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btn")
    val shape = RoundedCornerShape(50)
    val bg = when {
        !enabled -> Color(0x33FFFFFF)
        ThemeState.pro && primary -> Color.White
        primary -> if (focused) Color.White else IrColors.Cyan
        ThemeState.pro -> if (focused) Color(0x338B5CF6) else Color(0x14FFFFFF)
        else -> if (focused) IrColors.Violet else Color.Transparent
    }
    val fg = if (primary && enabled) IrColors.Night else Color.White
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) IrColors.Cyan else if (primary) Color.Transparent else Color(0x55FFFFFF),
                shape
            )
            .onFocusChanged { if (it.isFocused && !focused) Sfx.move(); focused = it.isFocused }
            .clickable { if (enabled) { Sfx.select(); onClick() } }
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (ThemeState.pro && primary && enabled) Color(0xFF0B0918) else fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = IrFonts.title)
    }
}

/** Champ de saisie aux couleurs de l'appli ; « Suivant » passe au champ d'en dessous. */
@Composable
fun IrField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    last: Boolean = false,
    onDone: () -> Unit = {},
) {
    val fm = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Color(0x66FFFFFF)) },
        singleLine = true,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboard,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(
            onNext = { fm.moveFocus(FocusDirection.Down) },
            onDone = { onDone() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = IrColors.Card,
            unfocusedContainerColor = IrColors.Card,
            focusedBorderColor = IrColors.Cyan,
            unfocusedBorderColor = Color(0x33FFFFFF),
            focusedLabelColor = IrColors.Cyan,
            unfocusedLabelColor = IrColors.TextDim,
            cursorColor = IrColors.Cyan,
        ),
    )
}
