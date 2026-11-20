package com.irpro.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.irpro.tv.data.PlaylistStore
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

/** Navigation de l'appli : intro → bienvenue (si aucune playlist) → formulaires → playlist prête. */
@androidx.compose.runtime.Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember(ctx) { PlaylistStore(ctx) }
    var screen by rememberSaveable { mutableStateOf("intro") }
    var version by remember { mutableIntStateOf(0) } // force la relecture des playlists après un changement

    fun afterIntro() = if (store.hasAny()) "ready" else "welcome"

    when (screen) {
        "intro" -> IntroScreen(onFinished = { screen = afterIntro() })

        "welcome" -> WelcomeScreen(
            canGoBack = store.hasAny(),
            onBack = { screen = "ready" },
            onXtream = { screen = "xtream" },
            onM3u = { screen = "m3u" },
        )

        "xtream" -> XtreamScreen(
            onBack = { screen = "welcome" },
            onSaved = { store.save(it); version++; screen = "ready" },
        )

        "m3u" -> M3uScreen(
            onBack = { screen = "welcome" },
            onSaved = { store.save(it); version++; screen = "ready" },
        )

        else -> {
            val active = remember(version) { store.active() }
            if (active == null) {
                LaunchedEffect(Unit) { screen = "welcome" }
            } else {
                PlaylistReadyScreen(
                    playlist = active,
                    count = remember(version) { store.all().size },
                    onAdd = { screen = "welcome" },
                    onDelete = { store.delete(active.id); version++; screen = afterIntro() },
                )
            }
        }
    }
}
