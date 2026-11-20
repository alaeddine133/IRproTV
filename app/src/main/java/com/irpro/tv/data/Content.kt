package com.irpro.tv.data

/** Types de contenu d'une playlist. */
enum class Kind { LIVE, MOVIE, SERIES }

data class Category(
    val id: String,
    val name: String,
    val kind: Kind,
    val isFrench: Boolean = FrenchPriority.isFrench(name),
)

data class Channel(
    val id: String,
    val num: Int,
    val name: String,
    val logo: String,
    val categoryId: String,
    val url: String,
    val epgId: String = "",
)

data class Movie(
    val id: String,
    val name: String,
    val poster: String,
    val categoryId: String,
    val url: String,
    val rating: String = "",
    val added: Long = 0,
)

data class Episode(val title: String, val url: String, val season: Int = 0, val number: Int = 0)

data class Series(
    val id: String,
    val name: String,
    val cover: String,
    val categoryId: String,
    val plot: String = "",
    val rating: String = "",
    /** Rempli pour les listes M3U ; pour Xtream, les épisodes sont chargés à l'ouverture de la série. */
    val episodes: List<Episode> = emptyList(),
)

data class Catalog(
    val liveCategories: List<Category> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val movieCategories: List<Category> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val seriesCategories: List<Category> = emptyList(),
    val series: List<Series> = emptyList(),
    val updatedAt: Long = 0,
    /** Lien du guide XMLTV annoncé par la liste M3U (url-tvg) */
    val epgUrl: String = "",
) {
    val isEmpty get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()

    /** Catégories françaises en premier (l'ordre du fournisseur est gardé à l'intérieur de chaque groupe). */
    fun sorted(): Catalog = copy(
        liveCategories = FrenchPriority.sortCategories(liveCategories),
        movieCategories = FrenchPriority.sortCategories(movieCategories),
        seriesCategories = FrenchPriority.sortCategories(seriesCategories),
    )
}
