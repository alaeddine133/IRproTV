package com.irpro.tv.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import java.io.File

/** Copie de secours du contenu sur l'appareil : l'appli s'ouvre même si le serveur ne répond pas. */
class CatalogCache(context: Context) {
    private val dir = File(context.filesDir, "catalog").apply { mkdirs() }
    private fun file(id: String) = File(dir, "${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    fun delete(id: String) { file(id).delete() }

    fun write(id: String, c: Catalog) {
        val tmp = File(dir, "${file(id).name}.tmp")
        tmp.bufferedWriter().use { bw ->
            JsonWriter(bw).use { w ->
                w.beginObject()
                w.name("updatedAt").value(c.updatedAt)
                w.name("epg").value(c.epgUrl)
                w.name("cats").beginArray()
                (c.liveCategories + c.movieCategories + c.seriesCategories).forEach {
                    w.beginArray().value(it.id).value(it.name).value(it.kind.name).endArray()
                }
                w.endArray()
                w.name("ch").beginArray()
                c.channels.forEach {
                    w.beginArray().value(it.id).value(it.num.toLong()).value(it.name).value(it.logo)
                        .value(it.categoryId).value(it.url).value(it.epgId).endArray()
                }
                w.endArray()
                w.name("mv").beginArray()
                c.movies.forEach {
                    w.beginArray().value(it.id).value(it.name).value(it.poster).value(it.categoryId)
                        .value(it.url).value(it.rating).value(it.added).endArray()
                }
                w.endArray()
                w.name("se").beginArray()
                c.series.forEach { s ->
                    w.beginArray().value(s.id).value(s.name).value(s.cover).value(s.categoryId).value(s.plot).value(s.rating)
                    w.beginArray()
                    s.episodes.forEach { e -> w.beginArray().value(e.title).value(e.url).value(e.season.toLong()).value(e.number.toLong()).endArray() }
                    w.endArray()
                    w.endArray()
                }
                w.endArray()
                w.endObject()
            }
        }
        val dest = file(id)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
    }

    fun read(id: String): Catalog? {
        val f = file(id)
        if (!f.exists()) return null
        return try {
            f.bufferedReader().use { br ->
                val r = JsonReader(br)
                var updated = 0L
                var epg = ""
                val live = ArrayList<Category>(); val mcat = ArrayList<Category>(); val scat = ArrayList<Category>()
                val ch = ArrayList<Channel>(); val mv = ArrayList<Movie>(); val se = ArrayList<Series>()
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "updatedAt" -> updated = r.nextLong()
                        "epg" -> epg = r.nextString()
                        "cats" -> arr(r) {
                            val c = Category(r.nextString(), r.nextString(), Kind.valueOf(r.nextString()))
                            when (c.kind) { Kind.LIVE -> live += c; Kind.MOVIE -> mcat += c; Kind.SERIES -> scat += c }
                        }
                        "ch" -> arr(r) {
                            ch += Channel(r.nextString(), r.nextInt(), r.nextString(), r.nextString(), r.nextString(), r.nextString(), r.nextString())
                        }
                        "mv" -> arr(r) {
                            mv += Movie(r.nextString(), r.nextString(), r.nextString(), r.nextString(), r.nextString(), r.nextString(), r.nextLong())
                        }
                        "se" -> arr(r) {
                            val id = r.nextString(); val n = r.nextString(); val cv = r.nextString(); val cat = r.nextString()
                            val plot = r.nextString(); val rating = r.nextString()
                            val eps = ArrayList<Episode>()
                            arr(r) { eps += Episode(r.nextString(), r.nextString(), r.nextInt(), r.nextInt()) }
                            se += Series(id, n, cv, cat, plot, rating, eps)
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                Catalog(live, ch, mcat, mv, scat, se, updated, epg)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Parcourt un tableau de tableaux : pour chaque élément, ouvre le tableau, appelle body, le referme. */
    private inline fun arr(r: JsonReader, body: () -> Unit) {
        r.beginArray()
        while (r.hasNext()) {
            r.beginArray(); body(); r.endArray()
        }
        r.endArray()
    }
}
