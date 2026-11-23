package com.streamvault.app.data.remote

import com.streamvault.app.data.model.ParsedInput
import java.net.URI

object YouTubeUrlParser {

    private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
    private val CHANNEL_ID_REGEX = Regex("^UC[a-zA-Z0-9_-]{22}$")

    fun parse(rawInput: String): ParsedInput {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedInput.Invalid("Please enter a YouTube channel link, video link, or stream URL.")
        }

        // Allow raw 11-char video ID or UC... channel ID directly
        if (VIDEO_ID_REGEX.matches(trimmed)) {
            return ParsedInput.VideoUrl(trimmed, "https://www.youtube.com/watch?v=$trimmed")
        }
        if (CHANNEL_ID_REGEX.matches(trimmed)) {
            return ParsedInput.ChannelIdUrl(trimmed, "https://www.youtube.com/channel/$trimmed")
        }

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = try {
            URI(normalized)
        } catch (e: Exception) {
            return ParsedInput.Invalid("Malformed URL format.")
        }

        val host = (uri.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val path = uri.path ?: ""
        val lowerPath = path.lowercase()

        // Direct media stream URLs (.m3u8, .mpd, .mp4, .webm)
        if (lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".mpd") ||
            lowerPath.endsWith(".mp4") || lowerPath.endsWith(".webm")
        ) {
            return ParsedInput.DirectMediaUrl(normalized)
        }

        if (host == "youtu.be") {
            val segments = path.trim('/').split('/')
            val id = segments.firstOrNull().orEmpty()
            return if (VIDEO_ID_REGEX.matches(id)) {
                ParsedInput.VideoUrl(id, normalized)
            } else {
                ParsedInput.Invalid("Invalid youtu.be video link.")
            }
        }

        if (host == "youtube.com" || host == "music.youtube.com") {
            val queryParams = parseQueryParams(uri.rawQuery)

            // 1. Standard watch URL: /watch?v=VIDEO_ID
            if (path.startsWith("/watch")) {
                val videoId = queryParams["v"].orEmpty()
                return if (VIDEO_ID_REGEX.matches(videoId)) {
                    ParsedInput.VideoUrl(videoId, normalized)
                } else {
                    ParsedInput.Invalid("Missing or invalid video ID in watch URL.")
                }
            }

            // 2. Shorts, Embed, or Live URLs: /shorts/ID, /embed/ID, /live/ID
            val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
            if (segments.size >= 2 && segments[0] in listOf("shorts", "embed", "live", "v")) {
                val videoId = segments[1]
                return if (VIDEO_ID_REGEX.matches(videoId)) {
                    ParsedInput.VideoUrl(videoId, normalized)
                } else {
                    ParsedInput.Invalid("Invalid video ID in ${segments[0]} URL.")
                }
            }

            // 3. Explicit Channel ID: /channel/UC...
            if (segments.size >= 2 && segments[0] == "channel") {
                val channelId = segments[1]
                return if (CHANNEL_ID_REGEX.matches(channelId)) {
                    ParsedInput.ChannelIdUrl(channelId, normalized)
                } else {
                    ParsedInput.Invalid("Invalid YouTube channel ID.")
                }
            }

            // 4. Handle or custom channel path: /@handle, /c/name, /user/name
            if (segments.isNotEmpty() && (segments[0].startsWith("@") ||
                        (segments.size >= 2 && segments[0] in listOf("c", "user")))
            ) {
                val handlePath = if (segments[0].startsWith("@")) {
                    "/${segments[0]}"
                } else {
                    "/${segments[0]}/${segments[1]}"
                }
                return ParsedInput.ChannelHandleUrl(handlePath, normalized)
            }
        }

        return ParsedInput.Invalid("Unsupported URL. Paste a YouTube channel link, video link, or direct stream URL.")
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    pair.substring(0, idx) to pair.substring(idx + 1)
                } else null
            }
            .toMap()
    }
}
