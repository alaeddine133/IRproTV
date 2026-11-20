package com.irpro.tv

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Mémorise si l'intro complète a déjà été vue (ensuite : version courte). */
object IntroPrefs {
    private const val FILE = "irpro_prefs"
    private const val KEY_SEEN = "intro_full_seen"
    fun fullSeen(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)
    fun reset(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, false).apply()
    fun markFullSeen(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_SEEN, true).apply()
}

/**
 * Vrai si l'écran est 4K et que la box sait décoder du H.264 en 3840x2160 à 60 i/s.
 * Sinon on joue la version 1080p, qui marche partout.
 */
fun canPlay4k(ctx: Context): Boolean = try {
    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    @Suppress("DEPRECATION")
    val mode = wm.defaultDisplay.mode
    val screen4k = maxOf(mode.physicalWidth, mode.physicalHeight) >= 3840
    val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 3840, 2160).apply {
        setInteger(MediaFormat.KEY_FRAME_RATE, 60)
    }
    val decoder = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(fmt)
    screen4k && decoder != null
} catch (e: Exception) {
    false
}

@OptIn(UnstableApi::class)
@Composable
fun IntroScreen(channelCount: Int = 0, onFinished: () -> Unit) {
    val context = LocalContext.current
    val full = remember { !IntroPrefs.fullSeen(context) }
    val rawId = remember {
        val k4 = canPlay4k(context)
        when {
            full && k4 -> R.raw.intro_full_4k
            full -> R.raw.intro_full_1080
            k4 -> R.raw.intro_short_4k
            else -> R.raw.intro_short_1080
        }
    }

    val done = remember { booleanArrayOf(false) }
    val finish: () -> Unit = {
        if (!done[0]) {
            done[0] = true
            if (full) IntroPrefs.markFullSeen(context)
            onFinished()
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$rawId")))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finish()
            }
            // Si la vidéo ne peut pas être lue sur cette box, on ne bloque jamais l'appli
            override fun onPlayerError(error: PlaybackException) = finish()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // OK / Retour / toucher l'écran : passer l'intro
    BackHandler { finish() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) { finish(); true } else false
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isFocusable = false
                    this.player = player
                }
            }
        )
        // Compteur réel de chaînes (intro complète), aux mêmes place et moment que dans la vidéo
        if (full && channelCount > 0) CounterOverlay(player, channelCount)
    }
}


@Composable
private fun CounterOverlay(player: ExoPlayer, count: Int) {
    var t by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(player) { while (true) { t = player.currentPosition; kotlinx.coroutines.delay(33) } }
    val sec = t / 1000f
    val alpha = when {
        sec < 3.75f -> 0f
        sec < 3.9f -> (sec - 3.75f) / 0.15f
        sec < 4.45f -> 1f
        sec < 4.75f -> 1f - (sec - 4.45f) / 0.3f
        else -> 0f
    }
    if (alpha <= 0f) return
    val p = ((sec - 3.8f) / 0.7f).coerceIn(0f, 1f)
    val shown = (count * (1 - (1 - p) * (1 - p) * (1 - p))).toInt()
    val bold = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.inter_display_bold))
    val medium = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.inter_display_medium))
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Même cadrage que la vidéo (mode « zoom ») : conception 1920 x 1080
        val s = maxOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
        val ox = (maxWidth.value - 1920f * s) / 2f
        val oy = (maxHeight.value - 1080f * s) / 2f
        val density = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.foundation.layout.Column(
            Modifier
                .offset(x = (ox + 150f * s).dp, y = (oy + 696f * s).dp)
                .alpha(alpha)
        ) {
            androidx.compose.material3.Text(
                String.format(java.util.Locale.FRANCE, "%,d", shown).replace('\u202F', ' ').replace('\u00A0', ' '),
                color = Color(0xFFF2F2F5), fontFamily = bold,
                fontSize = with(density) { (150f * s).dp.toSp() },
                letterSpacing = with(density) { (-6f * s).dp.toSp() },
                lineHeight = with(density) { (150f * s).dp.toSp() },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height((8f * s).dp))
            androidx.compose.material3.Text(
                "CHAÎNES EN DIRECT", color = Color(0xFFA78BFA), fontFamily = medium,
                fontSize = with(density) { (30f * s).dp.toSp() },
                letterSpacing = with(density) { (1f * s).dp.toSp() },
            )
        }
    }
}
