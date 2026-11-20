package com.irpro.tv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoadState(
    val playlistId: String? = null,
    val catalog: Catalog? = null,
    val loading: Boolean = false,
    /** Étape en cours, affichée à l'écran (« Chaînes… », « Films… ») */
    val step: String = "",
    val liveCount: Int? = null,
    val movieCount: Int? = null,
    val seriesCount: Int? = null,
    /** Message si la mise à jour a échoué (le contenu de la dernière fois reste affiché) */
    val error: String? = null,
)

/** Contenu de la playlist active, partagé par tous les écrans. */
object ContentRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LoadState())
    val state: StateFlow<LoadState> = _state.asStateFlow()
    private var job: Job? = null

    /**
     * Affiche tout de suite la copie enregistrée, puis recharge depuis le serveur si [refresh].
     * Appelé au démarrage (pendant l'intro), après l'ajout d'une playlist et par le bouton « Mettre à jour ».
     */
    fun load(context: Context, playlist: Playlist, refresh: Boolean = true) {
        val app = context.applicationContext
        val cache = CatalogCache(app)
        job?.cancel()
        job = scope.launch {
            val cached = if (_state.value.playlistId == playlist.id && _state.value.catalog != null) _state.value.catalog
            else cache.read(playlist.id)?.sorted()
            val doRefresh = refresh || cached == null
            _state.value = LoadState(playlist.id, cached, loading = doRefresh, step = if (doRefresh) "Connexion au serveur…" else "")
            if (!doRefresh) return@launch
            try {
                val fresh = when (playlist.type) {
                    PlaylistType.XTREAM -> loadXtream(playlist)
                    PlaylistType.M3U -> loadM3u(playlist)
                }
                if (fresh.isEmpty) throw IllegalStateException("vide")
                cache.write(playlist.id, fresh)
                _state.value = LoadState(
                    playlist.id, fresh, loading = false,
                    liveCount = fresh.channels.size, movieCount = fresh.movies.size, seriesCount = fresh.series.size,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false, step = "",
                        error = if (it.catalog != null) "Mise à jour impossible : contenu de la dernière fois affiché."
                        else "Impossible de charger ta playlist. Vérifie ta connexion et réessaie.",
                    )
                }
            }
        }
    }

    fun clear(context: Context, playlistId: String) {
        CatalogCache(context.applicationContext).delete(playlistId)
        if (_state.value.playlistId == playlistId) { job?.cancel(); _state.value = LoadState() }
    }

    private fun step(s: String) = _state.update { it.copy(step = s) }

    private fun loadXtream(p: Playlist): Catalog {
        val x = XtreamClient(p)
        step("Chaînes en direct…")
        val liveCats = x.liveCategories()
        val channels = x.channels()
        _state.update { it.copy(liveCount = channels.size) }
        step("Films…")
        val movieCats = runCatching { x.movieCategories() }.getOrDefault(emptyList())
        val movies = runCatching { x.movies() }.getOrDefault(emptyList())
        _state.update { it.copy(movieCount = movies.size) }
        step("Séries…")
        val seriesCats = runCatching { x.seriesCategories() }.getOrDefault(emptyList())
        val series = runCatching { x.series() }.getOrDefault(emptyList())
        _state.update { it.copy(seriesCount = series.size) }
        return Catalog(liveCats, channels, movieCats, movies, seriesCats, series, System.currentTimeMillis()).sorted()
    }

    private fun loadM3u(p: Playlist): Catalog {
        step("Lecture de la liste M3U…")
        val c = Net.open(p.url) { br -> M3uParser.parse(br.lineSequence()) }
        _state.update { it.copy(liveCount = c.channels.size, movieCount = c.movies.size, seriesCount = c.series.size) }
        return c
    }
}
