package com.irpro.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.irpro.tv.data.Catalog

/**
 * Aperçu d'une section en attendant son écran complet (étapes 6 à 10) :
 * les catégories, françaises en premier, avec leur nombre de chaînes / films / séries.
 */
@Composable
fun SectionPreviewScreen(section: Section, catalog: Catalog, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val (cats, counts, step) = remember(section, catalog) {
        when (section) {
            Section.LIVE -> Triple(catalog.liveCategories, catalog.channels.groupingBy { it.categoryId }.eachCount(), 6)
            Section.MOVIES -> Triple(catalog.movieCategories, catalog.movies.groupingBy { it.categoryId }.eachCount(), 9)
            Section.SERIES -> Triple(catalog.seriesCategories, catalog.series.groupingBy { it.categoryId }.eachCount(), 10)
            Section.GUIDE -> Triple(emptyList<com.irpro.tv.data.Category>(), emptyMap<String, Int>(), 8)
            Section.FAVORITES -> Triple(emptyList<com.irpro.tv.data.Category>(), emptyMap<String, Int>(), 7)
        }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().background(IrBackground).padding(horizontal = 40.dp, vertical = 26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IrIconView(section.icon, IrColors.Cyan, 30.dp)
            Spacer(Modifier.width(12.dp))
            Text(section.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            Text(
                "Écran complet à l'étape $step · aperçu des catégories",
                color = IrColors.TextDim, fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        if (cats.isEmpty()) {
            Text(
                if (section == Section.FAVORITES) "Tes favoris apparaîtront ici dès que le lecteur sera prêt."
                else if (section == Section.GUIDE) "Le guide des programmes arrive à l'étape 8."
                else "Aucune catégorie dans cette playlist.",
                color = Color(0xFFD5DBEA), fontSize = 17.sp,
            )
            Spacer(Modifier.height(20.dp))
            IrButton("Retour à l'accueil", modifier = Modifier.focusRequester(first), onClick = onBack)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp, start = 4.dp, end = 4.dp),
            ) {
                itemsIndexed(cats, key = { _, c -> c.id }) { i, c ->
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        (if (i == 0) Modifier.focusRequester(first) else Modifier)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focused) IrColors.Violet else IrColors.Card)
                            .border(if (focused) 3.dp else 1.dp, if (focused) IrColors.Cyan else Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (c.isFrench) {
                                Text(
                                    "FR", color = IrColors.CyanDark, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.background(IrColors.Cyan, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(c.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatCount(counts[c.id] ?: 0), color = IrColors.Cyan, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ComingSoonScreen(title: String, text: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(
        Modifier.fillMaxSize().background(IrBackground).padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text, color = IrColors.TextDim, fontSize = 16.sp)
        Spacer(Modifier.height(20.dp))
        IrButton("Retour à l'accueil", modifier = Modifier.focusRequester(first), onClick = onBack)
    }
}
