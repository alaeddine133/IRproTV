package com.irpro.tv.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Téléchargement en flux (sans tout garder en mémoire) : tient avec de très grosses listes. */
object Net {
    private const val UA = "IRPROTV/1.0 (Android TV)"

    fun <T> open(start: String, read: (BufferedReader) -> T): T =
        openStream(start) { BufferedReader(InputStreamReader(it, Charsets.UTF_8), 64 * 1024).use(read) }

    /** Ouvre une adresse en suivant les redirections (y compris http <-> https). */
    fun <T> openStream(start: String, read: (java.io.InputStream) -> T): T {
        var url = start
        repeat(6) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    url = URL(URL(url), c.getHeaderField("Location") ?: throw IOException("HTTP $code")).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                return c.inputStream.use(read)
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Trop de redirections")
    }
}

/** Lit une valeur JSON quelle que soit sa forme (texte, nombre, null…) et la rend en texte. */
fun JsonReader.anyString(): String = when (peek()) {
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    JsonToken.BOOLEAN -> nextBoolean().toString()
    JsonToken.NULL -> { nextNull(); "" }
    else -> { skipValue(); "" }
}

class XtreamClient(private val p: Playlist) {
    private val u = URLEncoder.encode(p.username, "UTF-8")
    private val pw = URLEncoder.encode(p.password, "UTF-8")
    private fun api(action: String) = "${p.server}/player_api.php?username=$u&password=$pw&action=$action"

    /** Parcourt un tableau JSON d'objets ; chaque objet est donné sous forme de Map texte -> texte. */
    private fun eachObject(action: String, onItem: (Map<String, String>) -> Unit) {
        Net.open(api(action)) { br ->
            val r = JsonReader(br)
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return@open }
            r.beginArray()
            val m = HashMap<String, String>(16)
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                m.clear()
                r.beginObject()
                while (r.hasNext()) m[r.nextName()] = r.anyString()
                r.endObject()
                onItem(m)
            }
            r.endArray()
        }
    }

    private fun categories(action: String, kind: Kind, prefix: String): List<Category> {
        val out = ArrayList<Category>()
        eachObject(action) { out += Category(prefix + it["category_id"].orEmpty(), it["category_name"].orEmpty(), kind) }
        return out
    }

    fun liveCategories() = categories("get_live_categories", Kind.LIVE, "l:")
    fun movieCategories() = categories("get_vod_categories", Kind.MOVIE, "m:")
    fun seriesCategories() = categories("get_series_categories", Kind.SERIES, "s:")

    fun channels(): List<Channel> {
        val out = ArrayList<Channel>()
        eachObject("get_live_streams") {
            val id = it["stream_id"].orEmpty()
            out += Channel(
                id = id, num = it["num"]?.toIntOrNull() ?: (out.size + 1), name = it["name"].orEmpty(),
                logo = it["stream_icon"].orEmpty(), categoryId = "l:" + it["category_id"].orEmpty(),
                url = "${p.server}/live/${p.username}/${p.password}/$id.ts", epgId = it["epg_channel_id"].orEmpty(),
            )
        }
        return out
    }

    fun movies(): List<Movie> {
        val out = ArrayList<Movie>()
        eachObject("get_vod_streams") {
            val id = it["stream_id"].orEmpty()
            val ext = it["container_extension"].orEmpty().ifBlank { "mp4" }
            out += Movie(
                id = id, name = it["name"].orEmpty(), poster = it["stream_icon"].orEmpty(),
                categoryId = "m:" + it["category_id"].orEmpty(),
                url = "${p.server}/movie/${p.username}/${p.password}/$id.$ext",
                rating = it["rating"].orEmpty(), added = it["added"]?.toLongOrNull() ?: 0,
            )
        }
        return out
    }

    fun series(): List<Series> {
        val out = ArrayList<Series>()
        eachObject("get_series") {
            out += Series(
                id = it["series_id"].orEmpty(), name = it["name"].orEmpty(), cover = it["cover"].orEmpty(),
                categoryId = "s:" + it["category_id"].orEmpty(), plot = it["plot"].orEmpty(), rating = it["rating"].orEmpty(),
            )
        }
        return out
    }
}
