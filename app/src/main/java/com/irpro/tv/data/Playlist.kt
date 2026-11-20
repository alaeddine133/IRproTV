package com.irpro.tv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PlaylistType { XTREAM, M3U }

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: PlaylistType,
    /** Xtream : adresse du serveur (http://hote:port) */
    val server: String = "",
    val username: String = "",
    val password: String = "",
    /** M3U : lien complet de la liste */
    val url: String = "",
    /** Date d'expiration de l'abonnement (millisecondes), si le serveur la donne */
    val expiresAt: Long? = null,
)

/** Playlists enregistrées sur l'appareil (plusieurs possibles, une seule active). */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("irpro_playlists", Context.MODE_PRIVATE)

    fun all(): List<Playlist> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun active(): Playlist? {
        val list = all()
        val id = prefs.getString(KEY_ACTIVE, null)
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun hasAny() = all().isNotEmpty()

    /** Ajoute (ou remplace si même id) et rend la playlist active. */
    fun save(p: Playlist) {
        val list = all().filterNot { it.id == p.id } + p
        prefs.edit()
            .putString(KEY_LIST, JSONArray(list.map { toJson(it) }).toString())
            .putString(KEY_ACTIVE, p.id)
            .apply()
    }

    fun setActive(id: String) = prefs.edit().putString(KEY_ACTIVE, id).apply()

    fun delete(id: String) {
        val list = all().filterNot { it.id == id }
        val ed = prefs.edit().putString(KEY_LIST, JSONArray(list.map { toJson(it) }).toString())
        if (prefs.getString(KEY_ACTIVE, null) == id) ed.putString(KEY_ACTIVE, list.firstOrNull()?.id)
        ed.apply()
    }

    private fun toJson(p: Playlist) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("type", p.type.name)
        put("server", p.server); put("username", p.username); put("password", p.password)
        put("url", p.url); if (p.expiresAt != null) put("expiresAt", p.expiresAt)
    }

    private fun fromJson(o: JSONObject) = Playlist(
        id = o.getString("id"),
        name = o.optString("name"),
        type = PlaylistType.valueOf(o.optString("type", "M3U")),
        server = o.optString("server"),
        username = o.optString("username"),
        password = o.optString("password"),
        url = o.optString("url"),
        expiresAt = if (o.has("expiresAt")) o.getLong("expiresAt") else null,
    )

    companion object {
        private const val KEY_LIST = "list"
        private const val KEY_ACTIVE = "active"
    }
}
