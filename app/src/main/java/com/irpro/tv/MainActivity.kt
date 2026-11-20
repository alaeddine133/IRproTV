package com.irpro.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.irpro.tv.data.ContentRepository
import com.irpro.tv.data.PlaylistStore
import com.irpro.tv.ui.ComingSoonScreen
import com.irpro.tv.ui.HomeScreen
import com.irpro.tv.ui.LoadingScreen
import com.irpro.tv.ui.Section
import com.irpro.tv.ui.SectionPreviewScreen
import com.irpro.tv.ui.M3uScreen
import com.irpro.tv.ui.PlaylistReadyScreen
import com.irpro.tv.ui.WelcomeScreen
import com.irpro.tv.ui.XtreamScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

/** Navigation : intro → bienvenue (si aucune playlist) → formulaires → chargement → playlist prête. */
@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember(ctx) { PlaylistStore(ctx) }
    var screen by rememberSaveable { mutableStateOf("intro") }
    var version by remember { mutableIntStateOf(0) } // relit les playlists après un changement
    val content by ContentRepository.state.collectAsState()

    // Le contenu se met à jour pendant l'intro, à chaque lancement de l'appli
    LaunchedEffect(Unit) {
        store.active()?.let { if (content.playlistId != it.id) ContentRepository.load(ctx, it, refresh = true) }
    }

    fun afterIntro() = if (store.hasAny()) "ready" else "welcome"

    when (screen) {
        "intro" -> IntroScreen(onFinished = { screen = afterIntro() })

        "welcome" -> WelcomeScreen(
            canGoBack = store.hasAny(),
            onBack = { screen = "playlists" },
            onXtream = { screen = "xtream" },
            onM3u = { screen = "m3u" },
        )

        "xtream", "m3u" -> {
            val onSaved: (com.irpro.tv.data.Playlist) -> Unit = {
                store.save(it); version++
                ContentRepository.load(ctx, it, refresh = true)
                screen = "ready"
            }
            if (screen == "xtream") XtreamScreen(onBack = { screen = "welcome" }, onSaved = onSaved)
            else M3uScreen(onBack = { screen = "welcome" }, onSaved = onSaved)
        }

        else -> {
            val active = remember(version) { store.active() }
            if (active == null) {
                LaunchedEffect(Unit) { screen = "welcome" }
            } else if (content.catalog == null || content.playlistId != active.id) {
                // Premier chargement (pas encore de copie enregistrée) : écran de progression
                LaunchedEffect(active.id) {
                    if (content.playlistId != active.id) ContentRepository.load(ctx, active, refresh = true)
                }
                LoadingScreen(
                    state = content,
                    onRetry = { ContentRepository.load(ctx, active, refresh = true) },
                    onPlaylists = { screen = "welcome" },
                )
            } else when (screen) {
                "playlists" -> PlaylistReadyScreen(
                    playlist = active,
                    count = remember(version) { store.all().size },
                    state = content,
                    onRefresh = { ContentRepository.load(ctx, active, refresh = true) },
                    onAdd = { screen = "welcome" },
                    onDelete = {
                        ContentRepository.clear(ctx, active.id)
                        store.delete(active.id); version++
                        store.active()?.let { ContentRepository.load(ctx, it, refresh = true) }
                        screen = afterIntro()
                    },
                    onBack = { screen = "ready" },
                )
                "search" -> ComingSoonScreen(
                    "Recherche", "La recherche dans toutes tes chaînes, films et séries arrive à l'étape 13.",
                    onBack = { screen = "ready" },
                )
                else -> if (screen.startsWith("section:")) {
                    SectionPreviewScreen(
                        section = Section.valueOf(screen.removePrefix("section:")),
                        catalog = content.catalog!!,
                        onBack = { screen = "ready" },
                    )
                } else {
                    HomeScreen(
                        playlist = active,
                        catalog = content.catalog!!,
                        updating = content.loading,
                        onOpen = { screen = "section:" + it.name },
                        onSearch = { screen = "search" },
                        onSettings = { screen = "playlists" },
                    )
                }
            }
        }
    }
}
