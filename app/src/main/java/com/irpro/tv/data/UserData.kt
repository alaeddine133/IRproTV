package com.irpro.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Réglages de l'appli (écran Paramètres). */
data class Settings(
    val engine: String = "exo",          // "exo" = lecteur rapide, "vlc" = lecteur compatible
    val buffer: String = "normal",       // "normal" | "large"
    val audioLang: String = "fr",
    val subtitlesDefault: Boolean = false,
    val subtitleSize: Int = 1,           // 0 petit, 1 moyen, 2 grand
    val autoNextEpisode: Boolean = true,
    val frenchFirst: Boolean = true,
    val navSounds: Boolean = true,
    val refreshOnLaunch: Boolean = true,
    val epgUrl: String = "",
    val epgOffsetHours: Int = 0,
    val audioDelayMs: Int = 0,
    val theme: String = "classic",      // "classic" = Thème 1, "pro" = Thème 2
)

/** Données de l'utilisateur, par playlist : favoris, chaînes récentes, reprise des films/épisodes, épisodes vus. */
object UserData {
    private lateinit var appCtx: Context
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** Change à chaque modification (pour rafraîchir les écrans). */
    private val _rev = MutableStateFlow(0)
    val rev: StateFlow<Int> = _rev.asStateFlow()

    fun init(ctx: Context) {
        if (::appCtx.isInitialized) return
        appCtx = ctx.applicationContext
        _settings.value = loadSettings()
        FrenchPriority.enabled = _settings.value.frenchFirst
        com.irpro.tv.ui.ThemeState.pro = true   // un seul style (Mosaïque) pour toute l'appli
        Profiles.init(appCtx)
        Parental.init(appCtx)
    }

    /** Signale que les données affichées ont changé (ex. : changement de profil). */
    fun bump() { _rev.value++ }

    private val prefs get() = appCtx.getSharedPreferences("irpro_user", Context.MODE_PRIVATE)

    /* ---------- Réglages ---------- */
    private fun loadSettings(): Settings {
        val o = prefs.getString("settings", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return Settings()
        val d = Settings()
        return Settings(
            engine = o.optString("engine", d.engine),
            buffer = o.optString("buffer", d.buffer),
            audioLang = o.optString("audioLang", d.audioLang),
            subtitlesDefault = o.optBoolean("subtitlesDefault", d.subtitlesDefault),
            subtitleSize = o.optInt("subtitleSize", d.subtitleSize),
            autoNextEpisode = o.optBoolean("autoNextEpisode", d.autoNextEpisode),
            frenchFirst = o.optBoolean("frenchFirst", d.frenchFirst),
            navSounds = o.optBoolean("navSounds", d.navSounds),
            refreshOnLaunch = o.optBoolean("refreshOnLaunch", d.refreshOnLaunch),
            epgUrl = o.optString("epgUrl", d.epgUrl),
            epgOffsetHours = o.optInt("epgOffsetHours", d.epgOffsetHours),
            audioDelayMs = o.optInt("audioDelayMs", d.audioDelayMs),
            theme = o.optString("theme", d.theme),
        )
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val s = f(_settings.value)
        _settings.value = s
        FrenchPriority.enabled = s.frenchFirst
        com.irpro.tv.ui.ThemeState.pro = true
        val o = JSONObject().apply {
            put("engine", s.engine); put("buffer", s.buffer); put("audioLang", s.audioLang)
            put("subtitlesDefault", s.subtitlesDefault); put("subtitleSize", s.subtitleSize)
            put("autoNextEpisode", s.autoNextEpisode); put("frenchFirst", s.frenchFirst)
            put("navSounds", s.navSounds); put("refreshOnLaunch", s.refreshOnLaunch)
            put("epgUrl", s.epgUrl); put("epgOffsetHours", s.epgOffsetHours); put("audioDelayMs", s.audioDelayMs); put("theme", s.theme)
        }
        prefs.edit().putString("settings", o.toString()).apply()
    }

    /* ---------- Listes simples (favoris, récents, vus, ma liste) ---------- */
    private fun list(key: String): List<String> =
        prefs.getString(key, null)?.let { s -> runCatching { JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull() }
            ?: emptyList()

    private fun putList(key: String, l: List<String>) {
        prefs.edit().putString(key, JSONArray(l).toString()).apply()
        _rev.value++
    }

    /** Clé des données : propre à la playlist ET au profil en cours. */
    private fun k(pl: String, what: String) = "${Profiles.keyPrefix(pl)}:$what"

    /** Recherches récentes (écran Recherche du Thème 2). */
    fun recentSearches(): List<String> = list("recent_search")
    fun addRecentSearch(q: String) {
        val t = q.trim(); if (t.length < 2) return
        putList("recent_search", (listOf(t) + recentSearches().filterNot { it.equals(t, ignoreCase = true) }).take(8))
    }

    fun favorites(pl: String) = list(k(pl, "fav"))
    fun isFavorite(pl: String, id: String) = id in favorites(pl)
    fun toggleFavorite(pl: String, id: String): Boolean {
        val l = favorites(pl)
        return if (id in l) { putList(k(pl, "fav"), l - id); false } else { putList(k(pl, "fav"), l + id); true }
    }

    fun recents(pl: String) = list(k(pl, "recent"))
    fun addRecent(pl: String, id: String) = putList(k(pl, "recent"), (listOf(id) + (recents(pl) - id)).take(30))

    fun myList(pl: String) = list(k(pl, "mylist"))
    fun toggleMyList(pl: String, id: String): Boolean {
        val l = myList(pl)
        return if (id in l) { putList(k(pl, "mylist"), l - id); false } else { putList(k(pl, "mylist"), l + id); true }
    }

    fun watched(pl: String) = list(k(pl, "watched")).toSet()
    fun markWatched(pl: String, id: String) {
        val l = list(k(pl, "watched"))
        if (id !in l) putList(k(pl, "watched"), (l + id).takeLast(5000))
    }

    /* ---------- Reprise de lecture : id -> position, durée, titre, date ---------- */
    data class Resume(val id: String, val pos: Long, val dur: Long, val title: String, val kind: String, val parentId: String, val at: Long)

    fun resumes(pl: String): List<Resume> {
        val s = prefs.getString(k(pl, "resume"), null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Resume(o.getString("id"), o.getLong("pos"), o.getLong("dur"), o.optString("title"), o.optString("kind"), o.optString("parent"), o.optLong("at"))
            }
        }.getOrDefault(emptyList())
    }

    fun resumeOf(pl: String, id: String) = resumes(pl).firstOrNull { it.id == id }

    fun saveResume(pl: String, r: Resume) {
        val l = resumes(pl).filterNot { it.id == r.id }
        val done = r.dur > 0 && r.pos > r.dur * 0.92
        if (done) markWatched(pl, r.id)
        val keep = if (done || r.pos < 30_000) l else (listOf(r) + l)
        val a = JSONArray(keep.take(60).map {
            JSONObject().put("id", it.id).put("pos", it.pos).put("dur", it.dur).put("title", it.title)
                .put("kind", it.kind).put("parent", it.parentId).put("at", it.at)
        })
        prefs.edit().putString(k(pl, "resume"), a.toString()).apply()
        _rev.value++
    }
}
