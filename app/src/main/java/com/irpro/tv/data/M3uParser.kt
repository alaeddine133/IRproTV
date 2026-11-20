package com.irpro.tv.data

/**
 * Lit une liste M3U ligne par ligne (fonctionne avec des listes de plusieurs dizaines de milliers d'entrées).
 * Classement : lien contenant /movie/ = film, /series/ = épisode de série, sinon chaîne en direct.
 */
object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    private val seasonEp = Regex("""^(.*?)[\s._-]+S(\d{1,2})[\s._-]?E(\d{1,3})""", RegexOption.IGNORE_CASE)

    fun parse(lines: Sequence<String>): Catalog {
        val liveCats = LinkedHashMap<String, Category>()
        val movieCats = LinkedHashMap<String, Category>()
        val seriesCats = LinkedHashMap<String, Category>()
        val channels = ArrayList<Channel>()
        val movies = ArrayList<Movie>()
        val seriesMap = LinkedHashMap<String, Series>()
        val episodes = HashMap<String, MutableList<Episode>>()
        // Une même adresse peut apparaître plusieurs fois : on garde des identifiants uniques et stables
        val seen = HashMap<String, Int>()
        fun uid(url: String): String { val n = seen[url] ?: 0; seen[url] = n + 1; return if (n == 0) url else "$url#$n" }

        var name: String? = null
        var logo = ""
        var group = ""
        var tvgId = ""
        var epgUrl = ""
        lines.forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val a = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: "").split(',').first().trim()
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val after = line.substringAfterLast("\",", "").ifEmpty { line.substringAfter(",", "") }
                    name = after.trim().ifEmpty { attrs["tvg-name"] ?: "Sans nom" }
                    logo = attrs["tvg-logo"].orEmpty()
                    group = attrs["group-title"].orEmpty()
                    tvgId = attrs["tvg-id"].orEmpty()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> group = line.substringAfter(":").trim()
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    val title = name ?: line.substringAfterLast('/')
                    val g = group.ifBlank { "Sans catégorie" }
                    val lower = line.lowercase()
                    when {
                        "/movie/" in lower -> {
                            val cat = movieCats.getOrPut(g) { Category("m:$g", g, Kind.MOVIE) }
                            movies += Movie(id = uid(line), name = title, poster = logo, categoryId = cat.id, url = line)
                        }
                        "/series/" in lower -> {
                            val cat = seriesCats.getOrPut(g) { Category("s:$g", g, Kind.SERIES) }
                            val m = seasonEp.find(title)
                            val showName = m?.groupValues?.get(1)?.trim()?.ifEmpty { null } ?: title
                            val key = "${cat.id}|$showName"
                            if (key !in seriesMap) seriesMap[key] = Series(id = key, name = showName, cover = logo, categoryId = cat.id)
                            episodes.getOrPut(key) { mutableListOf() } += Episode(
                                title = title, url = line,
                                season = m?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                                number = m?.groupValues?.get(3)?.toIntOrNull() ?: 0,
                            )
                        }
                        else -> {
                            val cat = liveCats.getOrPut(g) { Category("l:$g", g, Kind.LIVE) }
                            channels += Channel(
                                id = uid(line), num = channels.size + 1, name = title, logo = logo,
                                categoryId = cat.id, url = line, epgId = tvgId,
                            )
                        }
                    }
                    name = null; logo = ""; group = ""; tvgId = ""
                }
            }
        }
        val series = seriesMap.values.map { s ->
            s.copy(episodes = episodes[s.id].orEmpty().sortedWith(compareBy({ it.season }, { it.number })))
        }
        return Catalog(
            liveCategories = liveCats.values.toList(), channels = channels,
            movieCategories = movieCats.values.toList(), movies = movies,
            seriesCategories = seriesCats.values.toList(), series = series,
            updatedAt = System.currentTimeMillis(),
            epgUrl = epgUrl,
        )
    }
}
