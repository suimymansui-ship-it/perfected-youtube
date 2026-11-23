package com.streamvault.app.data.model

data class VideoItem(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val channelId: String = "",
    val thumbnailUrl: String,
    val publishedAt: String = "",
    val durationText: String = "",
    val viewCountText: String = "",
    val directStreamUrl: String? = null
)

data class ChannelInfo(
    val channelId: String,
    val title: String,
    val channelUrl: String,
    val videos: List<VideoItem>
)

data class StreamOption(
    val qualityLabel: String, // e.g., "1080p", "720p", "480p", "360p", "Auto (Adaptive)"
    val height: Int,
    val url: String,
    val mimeType: String = "video/mp4",
    val isAdaptive: Boolean = false
)

data class ResolvedMedia(
    val videoItem: VideoItem,
    val streams: List<StreamOption>,
    val sourceNotice: String? = null
)

sealed class ParsedInput {
    data class VideoUrl(val videoId: String, val originalUrl: String) : ParsedInput()
    data class ChannelIdUrl(val channelId: String, val originalUrl: String) : ParsedInput()
    data class ChannelHandleUrl(val handleOrPath: String, val originalUrl: String) : ParsedInput()
    data class DirectMediaUrl(val url: String) : ParsedInput()
    data class Invalid(val reason: String) : ParsedInput()
}
