package com.streamvault.app.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.streamvault.app.data.local.DownloadDao
import com.streamvault.app.data.local.DownloadedVideoEntity
import com.streamvault.app.data.model.StreamOption
import com.streamvault.app.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoDownloadHelper(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        registerCompletionReceiver()
    }

    fun enqueueDownload(video: VideoItem, stream: StreamOption) {
        val safeTitle = video.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(50)
        val extension = if (stream.mimeType.contains("webm")) "webm" else "mp4"
        val fileName = "${safeTitle}_${stream.qualityLabel}.$extension"

        val request = DownloadManager.Request(Uri.parse(stream.url))
            .setTitle("${video.title} (${stream.qualityLabel})")
            .setDescription("Downloading via StreamVault")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        scope.launch {
            downloadDao.upsertDownload(
                DownloadedVideoEntity(
                    videoId = video.videoId,
                    title = video.title,
                    channelTitle = video.channelTitle,
                    thumbnailUrl = video.thumbnailUrl,
                    qualityLabel = stream.qualityLabel,
                    systemDownloadId = downloadId,
                    localFileUri = "",
                    status = "DOWNLOADING"
                )
            )
        }
    }

    fun removeDownload(entity: DownloadedVideoEntity) {
        try {
            downloadManager.remove(entity.systemDownloadId)
        } catch (_: Exception) {
        }
        scope.launch {
            downloadDao.deleteDownload(entity.videoId)
        }
    }

    private fun registerCompletionReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == -1L) return

                val query = DownloadManager.Query().setFilterById(id)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val localUri = if (uriIdx >= 0) cursor.getString(uriIdx).orEmpty() else ""

                        val mappedStatus = when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> "COMPLETED"
                            DownloadManager.STATUS_FAILED -> "FAILED"
                            else -> "DOWNLOADING"
                        }

                        scope.launch {
                            downloadDao.updateDownloadStatus(id, mappedStatus, localUri)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
