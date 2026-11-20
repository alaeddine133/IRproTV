package com.irpro.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTest {

    @Test fun frenchNames() {
        listOf("FR | SPORT", "[FR] Cinéma", "FR: TNT", "Sport FR", "|FR| INFO", "France 24", "Films VF",
            "Séries VOSTFR", "TNT HD", "Français", "FRENCH MOVIES", "FRA - Kids").forEach {
            assertTrue("doit être FR : $it", FrenchPriority.isFrench(it))
        }
        listOf("AFRICA", "UK | Sport", "Free TV", "FRIENDS", "Arabic", "DE: Kino", "INFRA").forEach {
            assertFalse("ne doit pas être FR : $it", FrenchPriority.isFrench(it))
        }
    }

    @Test fun frenchFirstKeepsOrder() {
        val cats = listOf("UK | News", "FR | Sport", "US | Movies", "FR | Info").map { Category(it, it, Kind.LIVE) }
        assertEquals(listOf("FR | Sport", "FR | Info", "UK | News", "US | Movies"), FrenchPriority.sortCategories(cats).map { it.name })
    }

    @Test fun m3uParse() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1.fr" tvg-logo="http://l/tf1.png" group-title="UK | News",BBC One
            http://srv/live/u/p/2.ts
            #EXTINF:-1 tvg-id="tf1.fr" tvg-logo="http://l/tf1.png" group-title="FR | TNT",TF1 HD
            http://srv/live/u/p/1.ts
            #EXTINF:-1 group-title="FR | Films",Le Film (2024)
            http://srv/movie/u/p/10.mkv
            #EXTINF:-1 group-title="FR | Séries",Ma Série S01 E02
            http://srv/series/u/p/21.mp4
            #EXTINF:-1 group-title="FR | Séries",Ma Série S01 E01
            http://srv/series/u/p/20.mp4
            #EXTINF:-1,Sans groupe
            http://srv/live/u/p/3.m3u8
        """.trimIndent()
        val c = M3uParser.parse(m3u.lineSequence()).sorted()
        assertEquals(3, c.channels.size)
        assertEquals("BBC One", c.channels[0].name)
        assertEquals("TF1 HD", c.channels[1].name)
        assertEquals("tf1.fr", c.channels[1].epgId)
        assertEquals("FR | TNT", c.liveCategories.first().name) // FR en premier
        assertEquals(1, c.movies.size)
        assertEquals(1, c.series.size)
        assertEquals("Ma Série", c.series[0].name)
        assertEquals(listOf(1, 2), c.series[0].episodes.map { it.number })
        assertEquals("Sans groupe", c.channels[2].name)
    }
}
