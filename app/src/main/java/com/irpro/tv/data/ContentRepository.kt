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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext

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
    /** Playlist demandée en dernier (connue tout de suite, avant même la lecture du cache). */
    @Volatile var requestedId: String? = null
        private set

    fun load(context: Context, playlist: Playlist, refresh: Boolean = true) {
        val app = context.applicationContext
        val cache = CatalogCache(app)
        requestedId = playlist.id
        job?.cancel()
        job = scope.launch {
            fun stillWanted() = requestedId == playlist.id && isActive
            val reuse = _state.value.playlistId == playlist.id && _state.value.catalog != null
            val cached = if (reuse) _state.value.catalog else cache.read(playlist.id)?.sorted()
            if (!stillWanted()) return@launch
            val doRefresh = refresh || cached == null
            _state.value = LoadState(playlist.id, cached, loading = doRefresh, step = if (doRefresh) "Connexion au serveur…" else "")
            if (!doRefresh) return@launch
            try {
                val fresh = when (playlist.type) {
                    PlaylistType.XTREAM -> loadXtream(playlist)
                    PlaylistType.M3U -> loadM3u(playlist)
                }
                ensureActive()
                if (fresh.isEmpty) throw IllegalStateException("vide")
                cache.write(playlist.id, fresh)          // ordre du fournisseur conservé dans le cache
                val sorted = fresh.sorted()
                if (!stillWanted()) return@launch
                _state.value = LoadState(
                    playlist.id, sorted, loading = false,
                    liveCount = sorted.channels.size, movieCount = sorted.movies.size, seriesCount = sorted.series.size,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!stillWanted()) return@launch
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

    /** Re-trie en mémoire (réglage « Français en premier ») sans retélécharger. */
    fun resort(context: Context) {
        val id = _state.value.playlistId ?: return
        scope.launch {
            val raw = CatalogCache(context.applicationContext).read(id) ?: return@launch
            if (_state.value.playlistId == id) _state.update { it.copy(catalog = raw.sorted()) }
        }
    }

    fun clear(context: Context, playlistId: String) {
        CatalogCache(context.applicationContext).delete(playlistId)
        if (_state.value.playlistId == playlistId || requestedId == playlistId) { job?.cancel(); requestedId = null; _state.value = LoadState() }
    }

    private fun step(s: String) = _state.update { it.copy(step = s) }

    private suspend fun check() = currentCoroutineContext().ensureActive()

    private suspend fun loadXtream(p: Playlist): Catalog {
        val x = XtreamClient(p)
        step("Chaînes en direct…")
        val liveCats = x.liveCategories()
        val channels = x.channels()
        check()
        _state.update { it.copy(liveCount = channels.size) }
        step("Films…")
        val movieCats = runCatching { x.movieCategories() }.getOrDefault(emptyList())
        val movies = runCatching { x.movies() }.getOrDefault(emptyList())
        check()
        _state.update { it.copy(movieCount = movies.size) }
        step("Séries…")
        val seriesCats = runCatching { x.seriesCategories() }.getOrDefault(emptyList())
        val series = runCatching { x.series() }.getOrDefault(emptyList())
        _state.update { it.copy(seriesCount = series.size) }
        check()
        return Catalog(liveCats, channels, movieCats, movies, seriesCats, series, System.currentTimeMillis())
    }

    private suspend fun loadM3u(p: Playlist): Catalog {
        step("Lecture de la liste M3U…")
        val c = Net.open(p.url) { br -> M3uParser.parse(br.lineSequence()) }
        _state.update { it.copy(liveCount = c.channels.size, movieCount = c.movies.size, seriesCount = c.series.size) }
        return c
    }
}
