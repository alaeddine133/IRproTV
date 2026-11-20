package com.irpro.tv.data

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

data class Program(val title: String, val start: Long, val end: Long, val desc: String = "") {
    fun isNow(t: Long = System.currentTimeMillis()) = t in start until end
    fun progress(t: Long = System.currentTimeMillis()) =
        if (end <= start) 0f else ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

/**
 * Guide des programmes.
 * - Xtream : programme court par chaîne (chargé à la demande, gardé en mémoire 30 min).
 * - M3U : fichier XMLTV (lien « url-tvg » de la liste ou lien saisi dans les Paramètres), lu une fois en flux.
 */
object Epg {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<Program>>>()
    private var xmltv: Map<String, List<Program>> = emptyMap()
    private var xmltvLoadedFor: String? = null

    private fun offset() = UserData.settings.value.epgOffsetHours * 3_600_000L

    fun cached(ch: Channel): List<Program>? = cache[ch.id]?.second ?: xmltv[ch.epgId]

    suspend fun forChannel(p: Playlist, ch: Channel): List<Program> = withContext(Dispatchers.IO) {
        cache[ch.id]?.let { (at, l) -> if (System.currentTimeMillis() - at < 30 * 60_000) return@withContext l }
        val list = when {
            p.type == PlaylistType.XTREAM && UserData.settings.value.epgUrl.isBlank() ->
                runCatching { xtreamShort(p, ch.id) }.getOrDefault(emptyList())
            else -> xmltv[ch.epgId].orEmpty()
        }
        cache[ch.id] = System.currentTimeMillis() to list
        list
    }

    private fun xtreamShort(p: Playlist, streamId: String): List<Program> {
        val u = URLEncoder.encode(p.username, "UTF-8"); val pw = URLEncoder.encode(p.password, "UTF-8")
        val url = "${p.server}/player_api.php?username=$u&password=$pw&action=get_short_epg&stream_id=$streamId&limit=12"
        val out = ArrayList<Program>()
        Net.open(url) { br ->
            val r = JsonReader(br); r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); return@open }
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "epg_listings" || r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); continue }
                r.beginArray()
                while (r.hasNext()) {
                    val m = HashMap<String, String>()
                    r.beginObject(); while (r.hasNext()) m[r.nextName()] = r.anyString(); r.endObject()
                    val s = m["start_timestamp"]?.toLongOrNull()?.times(1000) ?: continue
                    val e = m["stop_timestamp"]?.toLongOrNull()?.times(1000) ?: continue
                    out += Program(b64(m["title"]), s + offset(), e + offset(), b64(m["description"]))
                }
                r.endArray()
            }
            r.endObject()
        }
        return out.sortedBy { it.start }
    }

    private fun b64(s: String?): String {
        if (s.isNullOrBlank()) return ""
        return runCatching { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(s).trim()
    }

    /** Charge le XMLTV pour les chaînes de la playlist (fenêtre : -3 h … +24 h). */
    suspend fun loadXmltv(link: String, channels: List<Channel>) = withContext(Dispatchers.IO) {
        if (link.isBlank() || xmltvLoadedFor == link) return@withContext
        val wanted = channels.mapNotNull { it.epgId.ifBlank { null } }.toHashSet()
        if (wanted.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val from = now - 3 * 3_600_000L; val to = now + 24 * 3_600_000L
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val res = HashMap<String, MutableList<Program>>()
        runCatching {
            val conn = java.net.URL(link).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15_000; conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "IRPROTV/1.0 (Android TV)")
            val raw = conn.inputStream.buffered()
            raw.mark(4)
            val gz = raw.read() == 0x1f && raw.read() == 0x8b
            raw.reset()
            val input = if (gz || link.endsWith(".gz")) GZIPInputStream(raw) else raw
            input.use { ins ->
                val x = Xml.newPullParser()
                x.setInput(ins, null)
                var ch = ""; var s = 0L; var e = 0L; var title = ""; var desc = ""; var inProg = false; var tag = ""
                while (x.next() != XmlPullParser.END_DOCUMENT) {
                    when (x.eventType) {
                        XmlPullParser.START_TAG -> {
                            tag = x.name
                            if (tag == "programme") {
                                ch = x.getAttributeValue(null, "channel") ?: ""
                                s = runCatching { fmt.parse(x.getAttributeValue(null, "start"))!!.time }.getOrDefault(0)
                                e = runCatching { fmt.parse(x.getAttributeValue(null, "stop"))!!.time }.getOrDefault(0)
                                title = ""; desc = ""; inProg = ch in wanted && e > from && s < to
                            }
                        }
                        XmlPullParser.TEXT -> if (inProg) {
                            if (tag == "title" && title.isEmpty()) title = x.text.trim()
                            if (tag == "desc" && desc.isEmpty()) desc = x.text.trim()
                        }
                        XmlPullParser.END_TAG -> {
                            if (x.name == "programme" && inProg) {
                                res.getOrPut(ch) { mutableListOf() } += Program(title, s + offset(), e + offset(), desc)
                                inProg = false
                            }
                            tag = ""
                        }
                    }
                }
            }
        }
        if (res.isNotEmpty()) {
            xmltv = res.mapValues { it.value.sortedBy { p -> p.start } }
            xmltvLoadedFor = link
            cache.clear()
        }
    }

    fun clear() { cache.clear(); xmltv = emptyMap(); xmltvLoadedFor = null }
}
