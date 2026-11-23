package com.streamvault.app

import android.app.Application
import com.streamvault.app.data.local.AppDatabase
import com.streamvault.app.data.remote.StreamResolver
import com.streamvault.app.data.remote.YouTubeFeedService
import com.streamvault.app.data.repository.VideoRepository
import com.streamvault.app.download.VideoDownloadHelper
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class StreamVaultApp : Application() {

    lateinit var repository: VideoRepository
        private set

    lateinit var downloadHelper: VideoDownloadHelper
        private set

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val database = AppDatabase.getInstance(this)
        val feedService = YouTubeFeedService(okHttpClient)
        val streamResolver = StreamResolver(this, okHttpClient)

        repository = VideoRepository(
            feedService = feedService,
            streamResolver = streamResolver,
            favoriteDao = database.favoriteDao(),
            downloadDao = database.downloadDao()
        )

        downloadHelper = VideoDownloadHelper(
            context = this,
            downloadDao = database.downloadDao()
        )
    }
}
