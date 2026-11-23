package com.streamvault.app.data.remote

import android.util.Xml
import com.streamvault.app.BuildConfig
import com.streamvault.app.data.model.ChannelInfo
import com.streamvault.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder

/**
 * Fetches channel uploads and video metadata using:
 * 1. Official YouTube public Atom XML feeds (requires zero API keys).
 * 2. Optional YouTube Data API v3 (if YOUTUBE_API_KEY is configured in local.properties).
 */
class YouTubeFeedService(
    private val client: OkHttpClient
) {
    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        private val CHANNEL_ID_IN_HTML = Regex("\"channelId\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{22})\"")
        private val EXTERNAL_ID_IN_HTML = Regex("\"externalId\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{22})\"")
        private val RSS_LINK_IN_HTML = Regex("feeds/videos\\.xml\\?channel_id=(UC[a-zA-Z0-9_-]{22})")
    }

    suspend fun resolveChannelIdFromHandle(handleOrPath: String): String = withContext(Dispatchers.IO) {
        val cleanPath = if (handleOrPath.startsWith("/")) handleOrPath else "/$handleOrPath"

        // If YouTube Data API v3 key is configured and input is @handle, try official API first
        if (BuildConfig.YOUTUBE_API_KEY.isNotBlank() && cleanPath.startsWith("/@")) {
            val handle = cleanPath.removePrefix("/@")
            val apiUrl = "https://www.googleapis.com/youtube/v3/channels?part=id&forHandle=" +
                "${URLEncoder.encode(handle, "UTF-8")}&key=${BuildConfig.YOUTUBE_API_KEY}"
            val req = Request.Builder().url(apiUrl).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val items = JSONObject(body).optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val id = items.getJSONObject(0).optString("id")
                        if (id.startsWith("UC")) return@withContext id
                    }
                }
            }
        }

        // Fallback: resolve canonical channelId from public channel page metadata
        val url = "https://www.youtube.com$cleanPath"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to load channel page (HTTP ${response.code})")
            }
            val html = response.body?.string().orEmpty()
            val match = RSS_LINK_IN_HTML.find(html)
                ?: CHANNEL_ID_IN_HTML.find(html)
                ?: EXTERNAL_ID_IN_HTML.find(html)
                ?: throw IllegalStateException("Could not resolve channel ID for $cleanPath")
            match.groupValues[1]
        }
    }

    suspend fun fetchChannelVideos(channelId: String): ChannelInfo = withContext(Dispatchers.IO) {
        val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val xmlContent = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Could not load channel feed (HTTP ${response.code})")
            }
            response.body?.string() ?: throw IllegalStateException("Empty channel feed response.")
        }

        parseAtomFeed(channelId, xmlContent)
    }

    suspend fun fetchSingleVideoMetadata(videoId: String): VideoItem = withContext(Dispatchers.IO) {
        // Use public oEmbed endpoint for reliable title, author, and thumbnail without API key
        val oembedUrl = "https://www.youtube.com/oembed?url=" +
            URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8") +
            "&format=json"

        val request = Request.Builder()
            .url(oembedUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string().orEmpty())
                val title = json.optString("title", "Video $videoId")
                val author = json.optString("author_name", "YouTube Creator")
                val thumb = json.optString(
                    "thumbnail_url",
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                )
                return@withContext VideoItem(
                    videoId = videoId,
                    title = title,
                    channelTitle = author,
                    thumbnailUrl = thumb
                )
            }
        }

        VideoItem(
            videoId = videoId,
            title = "YouTube Video ($videoId)",
            channelTitle = "YouTube",
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        )
    }

    private fun parseAtomFeed(channelId: String, xml: String): ChannelInfo {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var channelTitle = "YouTube Channel"
        val videos = mutableListOf<VideoItem>()

        var insideEntry = false
        var currentTag = ""
        var videoId = ""
        var title = ""
        var authorName = ""
        var published = ""
        var thumbnail = ""
        var views = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "entry") {
                        insideEntry = true
                        videoId = ""
                        title = ""
                        authorName = channelTitle
                        published = ""
                        thumbnail = ""
                        views = ""
                    } else if (insideEntry && currentTag == "media:thumbnail") {
                        thumbnail = parser.getAttributeValue(null, "url").orEmpty()
                    } else if (insideEntry && currentTag == "media:statistics") {
                        val count = parser.getAttributeValue(null, "views")?.toLongOrNull()
                        if (count != null) {
                            views = formatViewCount(count)
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (!insideEntry) {
                            if (currentTag == "title" && channelTitle == "YouTube Channel") {
                                channelTitle = text
                            }
                        } else {
                            when (currentTag) {
                                "yt:videoId" -> videoId = text
                                "title" -> title = text
                                "name" -> authorName = text
                                "published" -> published = text.take(10)
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && insideEntry) {
                        if (videoId.isNotEmpty()) {
                            val resolvedThumb = thumbnail.ifEmpty {
                                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            }
                            videos.add(
                                VideoItem(
                                    videoId = videoId,
                                    title = title.ifEmpty { "Untitled Video" },
                                    channelTitle = authorName.ifEmpty { channelTitle },
                                    channelId = channelId,
                                    thumbnailUrl = resolvedThumb,
                                    publishedAt = published,
                                    viewCountText = views
                                )
                            )
                        }
                        insideEntry = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return ChannelInfo(
            channelId = channelId,
            title = channelTitle,
            channelUrl = "https://www.youtube.com/channel/$channelId",
            videos = videos
        )
    }

    private fun formatViewCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
            else -> "$count views"
        }
    }
}
