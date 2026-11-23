package com.streamvault.app

import com.streamvault.app.data.model.ParsedInput
import com.streamvault.app.data.remote.YouTubeUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun parsesStandardWatchUrl() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(result is ParsedInput.VideoUrl)
        assertEquals("dQw4w9WgXcQ", (result as ParsedInput.VideoUrl).videoId)
    }

    @Test
    fun parsesShortenedYoutuBeUrl() {
        val result = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ?t=42")
        assertTrue(result is ParsedInput.VideoUrl)
        assertEquals("dQw4w9WgXcQ", (result as ParsedInput.VideoUrl).videoId)
    }

    @Test
    fun parsesShortsUrl() {
        val result = YouTubeUrlParser.parse("https://youtube.com/shorts/dQw4w9WgXcQ")
        assertTrue(result is ParsedInput.VideoUrl)
        assertEquals("dQw4w9WgXcQ", (result as ParsedInput.VideoUrl).videoId)
    }

    @Test
    fun parsesChannelIdUrl() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/channel/UCVHFbqXqoYvEWM1Ddxl0QDg")
        assertTrue(result is ParsedInput.ChannelIdUrl)
        assertEquals("UCVHFbqXqoYvEWM1Ddxl0QDg", (result as ParsedInput.ChannelIdUrl).channelId)
    }

    @Test
    fun parsesChannelHandleUrl() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/@AndroidDevelopers")
        assertTrue(result is ParsedInput.ChannelHandleUrl)
        assertEquals("/@AndroidDevelopers", (result as ParsedInput.ChannelHandleUrl).handleOrPath)
    }

    @Test
    fun parsesDirectHlsUrl() {
        val result = YouTubeUrlParser.parse("https://example.com/stream/master.m3u8")
        assertTrue(result is ParsedInput.DirectMediaUrl)
    }
}
