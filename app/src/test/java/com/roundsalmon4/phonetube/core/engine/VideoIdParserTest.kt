package com.roundsalmon4.phonetube.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoIdParserTest {

    @Test
    fun parsesIptvIdWithoutPort() {
        val parsed = VideoIdParser.parseIptv("iptv:host|user:12345")
        assertEquals("host|user", parsed?.first)
        assertEquals("12345", parsed?.second)
    }

    @Test
    fun parsesIptvIdWithPortInHost() {
        // ":" must be cut at the last occurrence so hosts with a port survive.
        val parsed = VideoIdParser.parseIptv("iptv:host:8080|user:12345")
        assertEquals("host:8080|user", parsed?.first)
        assertEquals("12345", parsed?.second)
    }

    @Test
    fun rejectsNonIptvIds() {
        assertNull(VideoIdParser.parseIptv("peertube:host:channel"))
        assertNull(VideoIdParser.parseIptv(""))
        assertNull(VideoIdParser.parseIptv("watch?v=x"))
    }

    @Test
    fun rejectsMalformedIptvIds() {
        assertNull(VideoIdParser.parseIptv("iptv:"))
        assertNull(VideoIdParser.parseIptv("iptv:host|user"))
        assertNull(VideoIdParser.parseIptv("iptv::"))
    }
}