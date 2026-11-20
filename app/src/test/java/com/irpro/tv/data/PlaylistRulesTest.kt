package com.irpro.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRulesTest {

    @Test fun serverWithoutScheme() =
        assertEquals("http://monserveur.com:8080", PlaylistRules.normalizeServer("monserveur.com:8080"))

    @Test fun serverKeepsHttps() =
        assertEquals("https://tv.example.net", PlaylistRules.normalizeServer("https://tv.example.net/"))

    @Test fun serverDropsPathAndQuery() =
        assertEquals(
            "http://1.2.3.4:25461",
            PlaylistRules.normalizeServer(" http://1.2.3.4:25461/player_api.php?username=a&password=b ")
        )

    @Test fun serverEmpty() = assertEquals("", PlaylistRules.normalizeServer("   "))

    @Test fun urlAddsScheme() =
        assertEquals("http://x.com/get.php?a=1", PlaylistRules.normalizeUrl("x.com/get.php?a=1"))

    @Test fun m3uHeader() {
        assertTrue(PlaylistRules.looksLikeM3u("#EXTM3U\n#EXTINF:-1,Chaîne\nhttp://a/b.ts"))
        assertTrue(PlaylistRules.looksLikeM3u("﻿  #EXTM3U url-tvg=\"x\""))
        assertTrue(PlaylistRules.looksLikeM3u("#EXTINF:-1 tvg-id=\"a\",A\nhttp://a"))
        assertFalse(PlaylistRules.looksLikeM3u("<html><body>Erreur</body></html>"))
    }

    @Test fun expiry() {
        assertEquals(1_800_000_000_000L, PlaylistRules.parseExpiry("1800000000"))
        assertNull(PlaylistRules.parseExpiry(null))
        assertNull(PlaylistRules.parseExpiry("null"))
        assertNull(PlaylistRules.parseExpiry("0"))
    }
}
