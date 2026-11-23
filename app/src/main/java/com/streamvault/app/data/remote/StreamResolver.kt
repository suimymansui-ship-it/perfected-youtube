package com.streamvault.app.data.remote

import android.content.Context
import com.streamvault.app.data.model.ResolvedMedia
import com.streamvault.app.data.model.StreamOption
import com.streamvault.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Resolves playable non-DRM video streams for Media3 ExoPlayer.
 *
 * Supports:
 * 1. Direct HLS (.m3u8), DASH (.mpd), and progressive MP4/WebM streams.
 * 2. Optional user-configured Piped/Invidious-compatible JSON API endpoint (configured in Settings)
 *    for fetching public non-DRM adaptive/progressive streams without embedding DRM circumvention.
 * 3. Fallback reference HLS adaptive stream so player quality/speed controls can be tested anytime.
 */
class StreamResolver(
    context: Context,
    private val client: OkHttpClient
) {
    private val prefs = context.getSharedPreferences("streamvault_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_INSTANCE_URL = "custom_api_instance_url"

        // Public W3C / Apple / Blender reference adaptive HLS stream for immediate player testing
        const val DEMO_ADAPTIVE_HLS_URL =
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"
    }

    fun getConfiguredInstanceUrl(): String {
        return prefs.getString(KEY_INSTANCE_URL, "").orEmpty()
    }

    fun setConfiguredInstanceUrl(url: String) {
        prefs.edit().putString(KEY_INSTANCE_URL, url.trim().trimEnd('/')).apply()
    }

    suspend fun resolveStreams(video: VideoItem): ResolvedMedia = withContext(Dispatchers.IO) {
        // 1. If the video item already carries a direct stream URL (e.g., downloaded file or direct link)
        val direct = video.directStreamUrl
        if (!direct.isNullOrBlank()) {
            return@withContext ResolvedMedia(
                videoItem = video,
                streams = listOf(
                    StreamOption(
                        qualityLabel = "Auto (Direct)",
                        height = 1080,
                        url = direct,
                        mimeType = guessMimeType(direct),
                        isAdaptive = direct.contains(".m3u8") || direct.contains(".mpd")
                    )
                )
            )
        }

        // 2. If the user configured a self-hosted Piped-compatible API endpoint in Settings
        val instanceBase = getConfiguredInstanceUrl()
        if (instanceBase.isNotBlank()) {
            val resolvedFromInstance = tryResolveFromPipedEndpoint(instanceBase, video)
            if (resolvedFromInstance != null) {
                return@withContext resolvedFromInstance
            }
        }

        // 3. Default clean fallback stream when no external extractor instance is configured
        ResolvedMedia(
            videoItem = video,
            streams = listOf(
                StreamOption(
                    qualityLabel = "Auto (Adaptive HLS)",
                    height = 1080,
                    url = DEMO_ADAPTIVE_HLS_URL,
                    mimeType = "application/x-mpegURL",
                    isAdaptive = true
                )
            ),
            sourceNotice = "Playing reference adaptive stream. Configure a self-hosted Piped API URL in Settings or paste a direct .m3u8/.mp4 URL to stream custom sources."
        )
    }

    private fun tryResolveFromPipedEndpoint(baseUrl: String, video: VideoItem): ResolvedMedia? {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/streams/${video.videoId}")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())

                val options = mutableListOf<StreamOption>()

                val hlsUrl = json.optString("hls")
                if (hlsUrl.isNotBlank()) {
                    options.add(
                        StreamOption(
                            qualityLabel = "Auto (Adaptive HLS)",
                            height = 2160,
                            url = hlsUrl,
                            mimeType = "application/x-mpegURL",
                            isAdaptive = true
                        )
                    )
                }

                val videoStreams = json.optJSONArray("videoStreams")
                if (videoStreams != null) {
                    for (i in 0 until videoStreams.length()) {
                        val item = videoStreams.getJSONObject(i)
                        val videoOnly = item.optBoolean("videoOnly", false)
                        val url = item.optString("url")
                        val quality = item.optString("quality", "720p")
                        val height = quality.filter { it.isDigit() }.toIntOrNull() ?: 720
                        val mime = item.optString("mimeType", "video/mp4")

                        // Prefer muxed streams with audio for direct download & progressive playback
                        if (!videoOnly && url.isNotBlank()) {
                            options.add(
                                StreamOption(
                                    qualityLabel = quality,
                                    height = height,
                                    url = url,
                                    mimeType = mime,
                                    isAdaptive = false
                                )
                            )
                        }
                    }
                }

                if (options.isEmpty()) return null

                val updatedVideo = video.copy(
                    title = json.optString("title", video.title),
                    channelTitle = json.optString("uploader", video.channelTitle),
                    thumbnailUrl = json.optString("thumbnailUrl", video.thumbnailUrl)
                )

                ResolvedMedia(
                    videoItem = updatedVideo,
                    streams = options.distinctBy { it.qualityLabel }.sortedByDescending { it.height }
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun guessMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "application/x-mpegURL"
            lower.contains(".mpd") -> "application/dash+xml"
            lower.contains(".webm") -> "video/webm"
            else -> "video/mp4"
        }
    }
}
