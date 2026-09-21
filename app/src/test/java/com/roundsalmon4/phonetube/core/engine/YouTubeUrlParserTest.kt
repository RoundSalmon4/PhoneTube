package com.roundsalmon4.phonetube.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {

    private fun parse(raw: String): YouTubeLink = YouTubeUrlParser.parseRaw(raw)

    private fun assertVideo(raw: String, expectedId: String) {
        val link = parse(raw)
        assertEquals("$raw type", YouTubeLink.Type.VIDEO, link.type)
        assertEquals("$raw id", expectedId, link.id)
        assertTrue(link.isValid)
    }

    @Test
    fun youtuBeShortLinksResolveToVideo() {
        assertVideo("https://youtu.be/abc123", "abc123")
        assertVideo("https://youtu.be/abc123?t=30", "abc123")
        assertVideo("http://youtu.be/xyz789", "xyz789")
    }

    @Test
    fun watchLinksResolveToVideoOrPlaylist() {
        assertVideo("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "dQw4w9WgXcQ")
        assertVideo("https://youtube.com/watch?v=VideoId1&list=PLextra", "VideoId1")
        val playlist = parse("https://www.youtube.com/watch?list=PLabc")
        assertEquals(YouTubeLink.Type.PLAYLIST, playlist.type)
        assertEquals("PLabc", playlist.id)
    }

    @Test
    fun bareHostVideoQueryResolves() {
        assertVideo("https://m.youtube.com/?v=barevid", "barevid")
        assertVideo("http://youtube.com/?v=barevid2&x=1", "barevid2")
    }

    @Test
    fun playlistPathResolves() {
        val link = parse("https://www.youtube.com/playlist?list=PLfull")
        assertEquals(YouTubeLink.Type.PLAYLIST, link.type)
        assertEquals("PLfull", link.id)
    }

    @Test
    fun shortsResolveToShortType() {
        val link = parse("https://youtube.com/shorts/shortid")
        assertEquals(YouTubeLink.Type.SHORT, link.type)
        assertEquals("shortid", link.id)
        val trailing = parse("https://www.youtube.com/shorts/shortid/")
        assertEquals("shortid", trailing.id)
    }

    @Test
    fun channelPathsResolve() {
        val channel = parse("https://www.youtube.com/channel/UCabc123")
        assertEquals(YouTubeLink.Type.CHANNEL, channel.type)
        assertEquals("UCabc123", channel.id)

        val user = parse("https://www.youtube.com/user/someone")
        assertEquals(YouTubeLink.Type.CHANNEL, user.type)
        assertEquals("someone", user.id)

        val cPath = parse("https://www.youtube.com/c/Name")
        assertEquals(YouTubeLink.Type.CHANNEL, cPath.type)
        assertEquals("Name", cPath.id)
    }

    @Test
    fun handleLinksKeepAtPrefixAndStripTabs() {
        val handle = parse("https://www.youtube.com/@MyHandle")
        assertEquals(YouTubeLink.Type.CHANNEL, handle.type)
        assertEquals("@MyHandle", handle.id)

        val withTab = parse("https://www.youtube.com/@MyHandle/live")
        assertEquals("@MyHandle", withTab.id)
    }

    @Test
    fun embedAndLegacyVideoPathsResolve() {
        assertVideo("https://www.youtube.com/embed/EMBED1", "EMBED1")
        assertVideo("https://www.youtube.com/v/VOld1", "VOld1")
    }

    @Test
    fun livePathResolvesToVideo() {
        assertVideo("https://www.youtube.com/live/liveid1", "liveid1")
    }

    @Test
    fun attributionLinkDecodesRedirectTarget() {
        // u is a URL-encoded /watch?v=... inside the attribution redirect.
        assertVideo(
            "https://www.youtube.com/attribution_link?a=xyz&u=%2Fwatch%3Fv%3Dredirid",
            "redirid"
        )
    }

    @Test
    fun vndYoutubeSchemeResolves() {
        assertVideo("vnd.youtube:vid123", "vid123")
        assertVideo("vnd.youtube.launch:vid456?feature=share", "vid456")
    }

    @Test
    fun idsKeepOriginalCase() {
        assertVideo("https://www.youtube.com/watch?v=AbCdEfG12", "AbCdEfG12")
    }

    @Test
    fun unknownLinksAreInvalid() {
        val link = parse("https://example.com/not-youtube")
        assertEquals(YouTubeLink.Type.UNKNOWN, link.type)
        assertFalse(link.isValid)
        assertFalse(parse("").isValid)
    }
}