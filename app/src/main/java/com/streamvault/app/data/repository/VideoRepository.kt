package com.streamvault.app.data.repository

import com.streamvault.app.data.local.DownloadDao
import com.streamvault.app.data.local.DownloadedVideoEntity
import com.streamvault.app.data.local.FavoriteDao
import com.streamvault.app.data.local.FavoriteVideoEntity
import com.streamvault.app.data.model.ChannelInfo
import com.streamvault.app.data.model.ParsedInput
import com.streamvault.app.data.model.ResolvedMedia
import com.streamvault.app.data.model.VideoItem
import com.streamvault.app.data.remote.StreamResolver
import com.streamvault.app.data.remote.YouTubeFeedService
import com.streamvault.app.data.remote.YouTubeUrlParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class LoadOutcome {
    data class ChannelLoaded(val channel: ChannelInfo) : LoadOutcome()
    data class VideoLoaded(val video: VideoItem) : LoadOutcome()
}

class VideoRepository(
    private val feedService: YouTubeFeedService,
    private val streamResolver: StreamResolver,
    private val favoriteDao: FavoriteDao,
    private val downloadDao: DownloadDao
) {
    fun observeFavorites(): Flow<List<VideoItem>> =
        favoriteDao.observeAllFavorites().map { list -> list.map { it.toVideoItem() } }

    fun observeFavoriteIds(): Flow<Set<String>> =
        favoriteDao.observeFavoriteIds().map { it.toSet() }

    fun observeDownloads(): Flow<List<DownloadedVideoEntity>> =
        downloadDao.observeAllDownloads()

    suspend fun toggleFavorite(video: VideoItem) {
        if (favoriteDao.isFavorite(video.videoId)) {
            favoriteDao.deleteFavorite(video.videoId)
        } else {
            favoriteDao.insertFavorite(FavoriteVideoEntity.fromVideoItem(video))
        }
    }

    suspend fun deleteDownloadRecord(videoId: String) {
        downloadDao.deleteDownload(videoId)
    }

    suspend fun processUserUrl(rawInput: String): LoadOutcome {
        return when (val parsed = YouTubeUrlParser.parse(rawInput)) {
            is ParsedInput.Invalid -> throw IllegalArgumentException(parsed.reason)

            is ParsedInput.ChannelIdUrl -> {
                val channel = feedService.fetchChannelVideos(parsed.channelId)
                LoadOutcome.ChannelLoaded(channel)
            }

            is ParsedInput.ChannelHandleUrl -> {
                val channelId = feedService.resolveChannelIdFromHandle(parsed.handleOrPath)
                val channel = feedService.fetchChannelVideos(channelId)
                LoadOutcome.ChannelLoaded(channel)
            }

            is ParsedInput.VideoUrl -> {
                val video = feedService.fetchSingleVideoMetadata(parsed.videoId)
                LoadOutcome.VideoLoaded(video)
            }

            is ParsedInput.DirectMediaUrl -> {
                val id = "direct_${parsed.url.hashCode()}"
                val fileName = parsed.url.substringAfterLast('/').substringBefore('?')
                    .ifBlank { "Direct Video Stream" }
                val video = VideoItem(
                    videoId = id,
                    title = fileName,
                    channelTitle = "Direct Stream",
                    thumbnailUrl = "",
                    directStreamUrl = parsed.url
                )
                LoadOutcome.VideoLoaded(video)
            }
        }
    }

    suspend fun resolvePlayableMedia(video: VideoItem): ResolvedMedia {
        val localDownload = downloadDao.getDownloadByVideoId(video.videoId)
        if (localDownload != null && localDownload.status == "COMPLETED" && localDownload.localFileUri.isNotBlank()) {
            return streamResolver.resolveStreams(
                video.copy(directStreamUrl = localDownload.localFileUri)
            )
        }
        return streamResolver.resolveStreams(video)
    }

    fun getConfiguredInstanceUrl(): String = streamResolver.getConfiguredInstanceUrl()

    fun setConfiguredInstanceUrl(url: String) = streamResolver.setConfiguredInstanceUrl(url)
}
