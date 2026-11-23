package com.streamvault.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.streamvault.app.data.model.VideoItem

@Entity(tableName = "favorites")
data class FavoriteVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelTitle: String,
    val channelId: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val durationText: String,
    val viewCountText: String,
    val directStreamUrl: String?,
    val addedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun toVideoItem(): VideoItem = VideoItem(
        videoId = videoId,
        title = title,
        channelTitle = channelTitle,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        publishedAt = publishedAt,
        durationText = durationText,
        viewCountText = viewCountText,
        directStreamUrl = directStreamUrl
    )

    companion object {
        fun fromVideoItem(item: VideoItem): FavoriteVideoEntity = FavoriteVideoEntity(
            videoId = item.videoId,
            title = item.title,
            channelTitle = item.channelTitle,
            channelId = item.channelId,
            thumbnailUrl = item.thumbnailUrl,
            publishedAt = item.publishedAt,
            durationText = item.durationText,
            viewCountText = item.viewCountText,
            directStreamUrl = item.directStreamUrl
        )
    }
}

@Entity(tableName = "downloads")
data class DownloadedVideoEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val qualityLabel: String,
    val systemDownloadId: Long,
    val localFileUri: String,
    val status: String, // "DOWNLOADING", "COMPLETED", "FAILED"
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
