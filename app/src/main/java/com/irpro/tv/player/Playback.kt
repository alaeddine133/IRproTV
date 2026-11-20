package com.irpro.tv.player

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.irpro.tv.data.UserData

/**
 * Pilote la lecture : lecteur choisi dans les Paramètres, et bascule automatique
 * sur l'autre lecteur (ExoPlayer <-> VLC) si un flux refuse de se lancer.
 */
class Playback(private val ctx: Context) {
    var engine by mutableStateOf(Engine.create(ctx, UserData.settings.value, UserData.settings.value.engine == "vlc"))
        private set
    var buffering by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var onEnded: (() -> Unit)? = null
    /** Appelé quand l'appli passe en arrière-plan (sauvegarde de la reprise). */
    var onBackground: (() -> Unit)? = null
    private var stopped = false

    fun onStop() { if (stopped) return; stopped = true; onBackground?.invoke(); runCatching { engine.pause() } }
    fun onStart() {
        if (!stopped) return
        stopped = false
        if (url.isEmpty()) return
        if (engine.duration() > 0) engine.resume() else engine.play(url)   // direct : on reprend le direct
    }

    private var url = ""
    private var fallbackTried = false

    private val listener = object : EngineListener {
        override fun onReady() { ready = true; error = null }
        override fun onBuffering(b: Boolean) { buffering = b }
        override fun onEnded() { onEnded?.invoke() }
        override fun onError(msg: String) {
            if (!fallbackTried && url.isNotEmpty()) {
                fallbackTried = true
                val pos = if (engine.duration() > 0) engine.position() else 0L   // direct : on repart du direct
                val useVlc = engine is ExoEngine
                engine.listener = null
                engine.release()
                engine = Engine.create(ctx, UserData.settings.value, useVlc).also { it.listener = this }
                engine.play(url, pos)
            } else {
                buffering = false
                error = "Ce contenu ne répond pas. Le serveur est peut-être saturé ou la chaîne indisponible."
            }
        }
    }

    init { engine.listener = listener }

    fun play(u: String, startMs: Long = 0) {
        url = u; fallbackTried = false; error = null; ready = false; buffering = true
        engine.play(u, startMs)
    }

    fun release() { engine.listener = null; engine.release() }
}

@Composable
fun rememberPlayback(): Playback {
    val ctx = LocalContext.current
    val pb = remember { Playback(ctx) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(pb, owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> pb.onStop()
                androidx.lifecycle.Lifecycle.Event.ON_START -> pb.onStart()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); pb.release() }
    }
    return pb
}

/** Zone vidéo : affiche la vue du lecteur actif (change si on bascule de lecteur). */
@Composable
fun VideoSurface(pb: Playback, modifier: Modifier = Modifier) {
    val view = pb.engine.view
    AndroidView(
        modifier = modifier,
        factory = { c ->
            FrameLayout(c).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        update = { box ->
            if (box.childCount == 0 || box.getChildAt(0) !== view) {
                box.removeAllViews()
                (view.parent as? ViewGroup)?.removeView(view)
                box.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }
        },
    )
}
