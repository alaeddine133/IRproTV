package com.irpro.tv.player

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.irpro.tv.data.Settings
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

data class TrackOpt(val id: String, val label: String, val selected: Boolean)

interface EngineListener {
    fun onReady() {}
    fun onBuffering(b: Boolean) {}
    fun onEnded() {}
    fun onError(msg: String) {}
}

/** Lecteur commun : ExoPlayer (rapide) ou VLC (compatible), interchangeables. */
interface Engine {
    val view: View
    val name: String
    var listener: EngineListener?
    fun play(url: String, startMs: Long = 0)
    fun pause()
    fun resume()
    fun isPlaying(): Boolean
    fun position(): Long
    fun duration(): Long
    fun seekTo(ms: Long)
    fun audioTracks(): List<TrackOpt>
    fun subtitleTracks(): List<TrackOpt>
    fun selectAudio(id: String)
    fun selectSubtitle(id: String?)
    fun setVolume(v: Float)
    fun release()

    companion object {
        fun create(ctx: Context, s: Settings, vlc: Boolean): Engine = if (vlc) VlcEngine(ctx, s) else ExoEngine(ctx, s)
    }
}

private const val UA = "IRPROTV/1.0 (Android TV)"

@OptIn(UnstableApi::class)
class ExoEngine(ctx: Context, private val s: Settings) : Engine {
    override val name = "ExoPlayer"
    override var listener: EngineListener? = null

    private val player: ExoPlayer
    private val pv: PlayerView

    init {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val big = s.buffer == "large"
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                if (big) 30_000 else 15_000, if (big) 90_000 else 50_000,
                if (big) 3_000 else 1_500, if (big) 6_000 else 3_000,
            ).build()
        // Mode « ON » : décodeurs de la box d'abord, puis FFmpeg (Dolby AC3/E-AC3, DTS…) si la box ne sait pas
        val renderers = DefaultRenderersFactory(ctx)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http)))
            .setLoadControl(load)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(s.audioLang)
            .setPreferredTextLanguage(s.audioLang)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !s.subtitlesDefault)
            .build()
        pv = PlayerView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            setKeepContentOnPlayerReset(false)
            isFocusable = false
            player = this@ExoEngine.player
            subtitleView?.setFractionalTextSize(
                when (s.subtitleSize) { 0 -> 0.045f; 2 -> 0.075f; else -> 0.058f }
            )
            subtitleView?.setStyle(
                CaptionStyleCompat(android.graphics.Color.WHITE, 0x99000000.toInt(), android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null)
            )
        }
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> listener?.onBuffering(true)
                    Player.STATE_READY -> { listener?.onBuffering(false); listener?.onReady() }
                    Player.STATE_ENDED -> listener?.onEnded()
                    else -> Unit
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                // Un direct « en retard » : on revient au direct au lieu d'afficher une erreur
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                listener?.onError(error.errorCodeName)
            }
        })
    }

    override val view: View get() = pv

    override fun play(url: String, startMs: Long) {
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)), startMs)
        player.playWhenReady = true
        player.prepare()
    }
    override fun pause() { player.pause() }
    override fun resume() { player.play() }
    override fun isPlaying() = player.isPlaying
    override fun position() = player.currentPosition.coerceAtLeast(0)
    override fun duration() = player.duration.let { if (it == C.TIME_UNSET) 0 else it }
    override fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0))
    override fun setVolume(v: Float) { player.volume = v }

    private fun tracks(type: Int): List<Pair<Int, androidx.media3.common.Tracks.Group>> =
        player.currentTracks.groups.withIndex().filter { it.value.type == type }.map { it.index to it.value }

    private fun label(f: androidx.media3.common.Format, n: Int): String {
        val lang = f.language?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.FRENCH).replaceFirstChar { c -> c.uppercase() } }
        return listOfNotNull(f.label, lang).firstOrNull { it.isNotBlank() } ?: "Piste $n"
    }

    override fun audioTracks() = tracks(C.TRACK_TYPE_AUDIO).mapIndexed { n, (i, g) ->
        TrackOpt(i.toString(), label(g.getTrackFormat(0), n + 1), g.isSelected)
    }
    override fun subtitleTracks() = tracks(C.TRACK_TYPE_TEXT).mapIndexed { n, (i, g) ->
        TrackOpt(i.toString(), label(g.getTrackFormat(0), n + 1), g.isSelected)
    }
    override fun selectAudio(id: String) {
        val g = player.currentTracks.groups.getOrNull(id.toInt()) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
    }
    override fun selectSubtitle(id: String?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (id == null) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        else {
            val g = player.currentTracks.groups.getOrNull(id.toInt()) ?: return
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
        }
        player.trackSelectionParameters = b.build()
    }
    override fun release() { player.release() }
}

class VlcEngine(ctx: Context, private val s: Settings) : Engine {
    override val name = "VLC"
    override var listener: EngineListener? = null
    private val cache = if (s.buffer == "large") 4000 else 1500
    private val lib = LibVLC(ctx, arrayListOf("--network-caching=$cache", "--http-reconnect", "--audio-time-stretch"))
    private val mp = MediaPlayer(lib)
    private val layout = VLCVideoLayout(ctx)
    private val frame = FrameLayout(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(layout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        isFocusable = false
    }
    private var attached = false

    init {
        // La vidéo peut passer d'un écran à l'autre (aperçu -> plein écran) : on raccroche l'affichage
        frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { if (!attached) { runCatching { mp.attachViews(layout, null, true, false) }; attached = true } }
            override fun onViewDetachedFromWindow(v: View) { if (attached) { runCatching { mp.detachViews() }; attached = false } }
        })
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Buffering -> listener?.onBuffering(e.buffering < 100f)
                MediaPlayer.Event.Playing -> { listener?.onBuffering(false); listener?.onReady() }
                MediaPlayer.Event.EndReached -> listener?.onEnded()
                MediaPlayer.Event.EncounteredError -> listener?.onError("VLC")
                else -> Unit
            }
        }
    }

    override val view: View get() = frame

    override fun play(url: String, startMs: Long) {
        if (!attached) { mp.attachViews(layout, null, true, false); attached = true }
        val m = Media(lib, Uri.parse(url))
        m.setHWDecoderEnabled(true, false)
        m.addOption(":network-caching=$cache")
        m.addOption(":http-user-agent=$UA")
        if (startMs > 0) m.addOption(":start-time=${startMs / 1000}")
        mp.media = m
        m.release()
        mp.play()
        if (s.audioDelayMs != 0) mp.setAudioDelay(s.audioDelayMs * 1000L)
    }
    override fun pause() { mp.pause() }
    override fun resume() { mp.play() }
    override fun isPlaying() = mp.isPlaying
    override fun position() = mp.time.coerceAtLeast(0)
    override fun duration() = mp.length.coerceAtLeast(0)
    override fun seekTo(ms: Long) { mp.setTime(ms.coerceAtLeast(0)) }
    override fun setVolume(v: Float) { mp.setVolume((v * 100).toInt()) }

    override fun audioTracks(): List<TrackOpt> =
        (mp.audioTracks ?: emptyArray()).filter { it.id >= 0 }.map { TrackOpt(it.id.toString(), it.name, it.id == mp.audioTrack) }
    override fun subtitleTracks(): List<TrackOpt> =
        (mp.spuTracks ?: emptyArray()).filter { it.id >= 0 }.map { TrackOpt(it.id.toString(), it.name, it.id == mp.spuTrack) }
    override fun selectAudio(id: String) { mp.setAudioTrack(id.toInt()) }
    override fun selectSubtitle(id: String?) { mp.setSpuTrack(id?.toInt() ?: -1) }

    override fun release() {
        runCatching { mp.stop() }
        if (attached) runCatching { mp.detachViews() }
        mp.release()
        lib.release()
    }
}
