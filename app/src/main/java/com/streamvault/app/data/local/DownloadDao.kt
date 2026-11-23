package com.streamvault.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAtEpochMs DESC")
    fun observeAllDownloads(): Flow<List<DownloadedVideoEntity>>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownloadByVideoId(videoId: String): DownloadedVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(entity: DownloadedVideoEntity)

    @Query("UPDATE downloads SET status = :status, localFileUri = :localFileUri WHERE systemDownloadId = :downloadId")
    suspend fun updateDownloadStatus(downloadId: Long, status: String, localFileUri: String)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)
}
