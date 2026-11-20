package com.irpro.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class MovieDetail(
    val plot: String = "", val genre: String = "", val year: String = "", val duration: String = "",
    val rating: String = "", val cast: String = "", val backdrop: String = "",
)

data class EpisodeItem(
    val id: String, val season: Int, val number: Int, val title: String, val url: String,
    val plot: String = "", val image: String = "", val duration: String = "",
)

data class SeriesDetail(
    val plot: String = "", val genre: String = "", val year: String = "", val rating: String = "",
    val cover: String = "", val backdrop: String = "",
    val episodes: List<EpisodeItem> = emptyList(),
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
}

/** Fiches détaillées (Xtream : demandées au serveur à l'ouverture ; M3U : ce que contient la liste). */
object Details {
    private fun api(p: Playlist, action: String, extra: String): String {
        val u = URLEncoder.encode(p.username, "UTF-8"); val pw = URLEncoder.encode(p.password, "UTF-8")
        return "${p.server}/player_api.php?username=$u&password=$pw&action=$action&$extra"
    }

    private fun JSONObject.s(k: String) = optString(k).let { if (it == "null") "" else it.trim() }
    private fun firstOf(o: JSONObject, k: String): String = when (val v = o.opt(k)) {
        is JSONArray -> if (v.length() > 0) v.optString(0) else ""
        is String -> v
        else -> ""
    }

    suspend fun movie(p: Playlist, m: Movie): MovieDetail = withContext(Dispatchers.IO) {
        if (p.type != PlaylistType.XTREAM) return@withContext MovieDetail(rating = m.rating)
        runCatching {
            val o = JSONObject(Net.open(api(p, "get_vod_info", "vod_id=${m.id}")) { it.readText() })
            val i = o.optJSONObject("info") ?: JSONObject()
            MovieDetail(
                plot = i.s("plot").ifBlank { i.s("description") },
                genre = i.s("genre"),
                year = (i.s("releasedate").ifBlank { i.s("release_date") }).take(4),
                duration = i.s("duration"),
                rating = i.s("rating").ifBlank { m.rating },
                cast = i.s("cast").ifBlank { i.s("actors") },
                backdrop = firstOf(i, "backdrop_path"),
            )
        }.getOrDefault(MovieDetail(rating = m.rating))
    }

    suspend fun series(p: Playlist, s: Series): SeriesDetail = withContext(Dispatchers.IO) {
        if (p.type != PlaylistType.XTREAM) {
            return@withContext SeriesDetail(
                plot = s.plot, rating = s.rating, cover = s.cover,
                episodes = s.episodes.mapIndexed { i, e ->
                    EpisodeItem(id = e.url, season = e.season.coerceAtLeast(1), number = if (e.number > 0) e.number else i + 1, title = e.title, url = e.url)
                },
            )
        }
        runCatching {
            val o = JSONObject(Net.open(api(p, "get_series_info", "series_id=${s.id}")) { it.readText() })
            val i = o.optJSONObject("info") ?: JSONObject()
            val eps = ArrayList<EpisodeItem>()
            val epObj = o.opt("episodes")
            fun addSeason(arr: JSONArray?) {
                if (arr == null) return
                for (k in 0 until arr.length()) {
                    val e = arr.optJSONObject(k) ?: continue
                    val id = e.s("id")
                    val ext = e.s("container_extension").ifBlank { "mp4" }
                    val info = e.optJSONObject("info") ?: JSONObject()
                    eps += EpisodeItem(
                        id = id,
                        season = e.optInt("season", 1),
                        number = e.optInt("episode_num", k + 1),
                        title = e.s("title"),
                        url = "${p.server}/series/${p.username}/${p.password}/$id.$ext",
                        plot = info.s("plot"),
                        image = info.s("movie_image"),
                        duration = info.s("duration"),
                    )
                }
            }
            when (epObj) {
                is JSONObject -> epObj.keys().forEach { key -> addSeason(epObj.optJSONArray(key)) }
                is JSONArray -> for (k in 0 until epObj.length()) addSeason(epObj.optJSONArray(k))
            }
            SeriesDetail(
                plot = i.s("plot").ifBlank { s.plot },
                genre = i.s("genre"),
                year = (i.s("releaseDate").ifBlank { i.s("release_date") }).take(4),
                rating = i.s("rating").ifBlank { s.rating },
                cover = i.s("cover").ifBlank { s.cover },
                backdrop = firstOf(i, "backdrop_path"),
                episodes = eps.sortedWith(compareBy({ it.season }, { it.number })),
            )
        }.getOrDefault(SeriesDetail(plot = s.plot, rating = s.rating, cover = s.cover))
    }
}
