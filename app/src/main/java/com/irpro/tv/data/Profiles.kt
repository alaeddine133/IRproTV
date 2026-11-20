package com.irpro.tv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Un profil a sa propre liste, ses favoris, ses chaînes récentes et ses points de reprise.
 * kids = profil Enfants (contenu adulte toujours masqué) ; locked = il faut le code parental pour l'ouvrir.
 */
data class Profile(
    val id: String,
    val name: String,
    val color: Int = 0,
    val kids: Boolean = false,
    val locked: Boolean = false,
    /** Playlist à activer quand on choisit ce profil (vide = garder la playlist en cours). */
    val playlistId: String = "",
)

object Profiles {
    /** Le profil d'origine : ses données sont celles d'avant l'arrivée des profils. */
    const val MAIN = "main"

    private lateinit var prefs: SharedPreferences
    private val _active = MutableStateFlow(MAIN)
    val active: StateFlow<String> = _active.asStateFlow()
    private val _rev = MutableStateFlow(0)
    val rev: StateFlow<Int> = _rev.asStateFlow()

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("irpro_profiles", Context.MODE_PRIVATE)
        val saved = prefs.getString("active", MAIN) ?: MAIN
        _active.value = if (all().any { it.id == saved }) saved else MAIN
    }

    fun all(): List<Profile> {
        if (!::prefs.isInitialized) return listOf(Profile(MAIN, "Moi"))
        val l = prefs.getString("list", null)?.let { s ->
            runCatching {
                val a = JSONArray(s)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Profile(o.getString("id"), o.optString("name", "Profil"), o.optInt("color"), o.optBoolean("kids"), o.optBoolean("locked"), o.optString("playlistId"))
                }
            }.getOrNull()
        }.orEmpty()
        return if (l.none { it.id == MAIN }) listOf(Profile(MAIN, "Moi")) + l else l
    }

    fun current(): Profile = all().let { l -> l.firstOrNull { it.id == _active.value } ?: l.first() }

    private fun save(l: List<Profile>) {
        prefs.edit().putString("list", JSONArray(l.map {
            JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("kids", it.kids).put("locked", it.locked).put("playlistId", it.playlistId)
        }).toString()).apply()
        _rev.value++
    }

    fun add(name: String, kids: Boolean, locked: Boolean): Profile {
        val l = all()
        val p = Profile(UUID.randomUUID().toString().take(8), name.trim().ifBlank { "Profil ${l.size + 1}" }.take(16), l.size % 4, kids, locked)
        save(l + p)
        return p
    }

    fun update(p: Profile) = save(all().map { if (it.id == p.id) p else it })

    fun remove(id: String) {
        if (id == MAIN) return
        save(all().filterNot { it.id == id })
        if (_active.value == id) select(MAIN)
    }

    fun select(id: String) {
        val target = if (all().any { it.id == id }) id else MAIN
        Parental.lock()
        if (_active.value != target) {
            _active.value = target
            prefs.edit().putString("active", target).apply()
        }
        UserData.bump()
        _rev.value++
    }

    /** Préfixe des données d'un profil : le profil d'origine garde les clés d'avant (rien n'est perdu). */
    fun keyPrefix(playlistId: String): String = if (_active.value == MAIN) playlistId else "$playlistId@${_active.value}"
}

/** Contrôle parental : code à 4 chiffres et masquage des catégories pour adultes. */
object Parental {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(0)
    /** Change à chaque modification (code, activation, déblocage) : l'interface se met à jour. */
    val state: StateFlow<Int> = _state.asStateFlow()

    /** Contenu adulte affiché jusqu'à la fermeture de l'appli (après le code). */
    var unlocked = false
        private set

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.getSharedPreferences("irpro_parental", Context.MODE_PRIVATE)
    }

    val hasCode: Boolean get() = ::prefs.isInitialized && prefs.getString("code", null) != null
    val enabled: Boolean get() = hasCode && prefs.getBoolean("enabled", true)

    private fun hash(code: String): String =
        MessageDigest.getInstance("SHA-256").digest("irpro:$code".toByteArray()).joinToString("") { "%02x".format(it) }

    fun check(code: String): Boolean = hasCode && prefs.getString("code", null) == hash(code)

    fun setCode(code: String) {
        prefs.edit().putString("code", hash(code)).putBoolean("enabled", true).apply()
        unlocked = false; _state.value++
    }

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean("enabled", on).apply()
        unlocked = false; _state.value++
    }

    fun unlockSession() { unlocked = true; _state.value++ }
    fun lock() { if (unlocked) { unlocked = false; _state.value++ } }

    /** Le contenu adulte est-il masqué en ce moment ? */
    fun hidesAdult(): Boolean = Profiles.current().kids || (enabled && !unlocked)

    private val ADULT = Regex(
        """(\bxxx\b|\badult|\badulte|\+\s?18\b|\b18\s?\+|\bporn|\berotic|[ée]rotique|\bhot\s?club|\bplayboy|\bhustler|\bbrazzers|\bdorcel)""",
        RegexOption.IGNORE_CASE,
    )

    fun isAdult(name: String): Boolean = ADULT.containsMatchIn(name)

    /** Catalogue sans les catégories pour adultes (et leur contenu), si elles doivent être masquées. */
    fun filter(c: Catalog): Catalog {
        if (!hidesAdult()) return c
        // Les numéros de catégorie peuvent se répéter entre Direct, Films et Séries : un ensemble par type
        val badLive = c.liveCategories.filter { isAdult(it.name) }.map { it.id }.toHashSet()
        val badMovie = c.movieCategories.filter { isAdult(it.name) }.map { it.id }.toHashSet()
        val badSeries = c.seriesCategories.filter { isAdult(it.name) }.map { it.id }.toHashSet()
        if (badLive.isEmpty() && badMovie.isEmpty() && badSeries.isEmpty()) return c
        return c.copy(
            liveCategories = c.liveCategories.filterNot { it.id in badLive },
            channels = c.channels.filterNot { it.categoryId in badLive },
            movieCategories = c.movieCategories.filterNot { it.id in badMovie },
            movies = c.movies.filterNot { it.categoryId in badMovie },
            seriesCategories = c.seriesCategories.filterNot { it.id in badSeries },
            series = c.series.filterNot { it.categoryId in badSeries },
        )
    }
}
