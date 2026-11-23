package com.streamvault.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamvault.app.data.model.VideoItem
import com.streamvault.app.ui.screens.DownloadsScreen
import com.streamvault.app.ui.screens.FavoritesScreen
import com.streamvault.app.ui.screens.HomeScreen
import com.streamvault.app.ui.screens.PlayerScreen
import com.streamvault.app.ui.screens.SettingsScreen
import com.streamvault.app.ui.theme.StreamVaultTheme
import com.streamvault.app.ui.viewmodel.MainViewModel

enum class BottomTab(val label: String) {
    HOME("Browse"),
    FAVORITES("Favorites"),
    DOWNLOADS("Downloads"),
    SETTINGS("Settings")
}

class MainActivity : ComponentActivity() {

    private var sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        val app = application as StreamVaultApp
        val factory = MainViewModel.Factory(app.repository, app.downloadHelper)

        setContent {
            StreamVaultTheme {
                val vm: MainViewModel = viewModel(factory = factory)
                sharedUrlState.value?.let { incoming ->
                    vm.onUrlInputChanged(incoming)
                    sharedUrlState.value = null
                }
                StreamVaultMainContent(viewModel = vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUrl = intent?.dataString
        if (!dataUrl.isNullOrBlank()) {
            sharedUrlState.value = dataUrl
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamVaultMainContent(viewModel: MainViewModel) {
    val homeState by viewModel.homeState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val downloads by viewModel.downloads.collectAsState()

    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    var activePlayerVideo by remember { mutableStateOf<VideoItem?>(null) }

    val openPlayer: (VideoItem) -> Unit = { video ->
        activePlayerVideo = video
        viewModel.loadVideoForPlayback(video)
    }

    if (activePlayerVideo != null) {
        val currentVideo = playerState.resolvedMedia?.videoItem ?: activePlayerVideo!!
        PlayerScreen(
            state = playerState,
            isFavorite = currentVideo.videoId in favoriteIds,
            onBack = {
                activePlayerVideo = null
                viewModel.dismissPlayerBanner()
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onSelectStreamOption = { viewModel.selectStreamOption(it) },
            onSelectMaxHeight = { viewModel.setMaxVideoHeight(it) },
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onDownloadClick = { video, stream -> viewModel.downloadVideo(video, stream) }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == BottomTab.HOME,
                        onClick = { currentTab = BottomTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(BottomTab.HOME.label) }
                    )
                    NavigationBarItem(
                        selected = currentTab == BottomTab.FAVORITES,
                        onClick = { currentTab = BottomTab.FAVORITES },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        label = { Text(BottomTab.FAVORITES.label) }
                    )
                    NavigationBarItem(
                        selected = currentTab == BottomTab.DOWNLOADS,
                        onClick = { currentTab = BottomTab.DOWNLOADS },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                        label = { Text(BottomTab.DOWNLOADS.label) }
                    )
                    NavigationBarItem(
                        selected = currentTab == BottomTab.SETTINGS,
                        onClick = { currentTab = BottomTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(BottomTab.SETTINGS.label) }
                    )
                }
            }
        ) { innerPadding ->
            when (currentTab) {
                BottomTab.HOME -> HomeScreen(
                    state = homeState,
                    favoriteIds = favoriteIds,
                    onUrlChange = { viewModel.onUrlInputChanged(it) },
                    onLoadClick = { viewModel.submitUrl(onOpenVideo = openPlayer) },
                    onVideoSelected = openPlayer,
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    modifier = Modifier.padding(innerPadding)
                )

                BottomTab.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    onVideoSelected = openPlayer,
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    modifier = Modifier.padding(innerPadding)
                )

                BottomTab.DOWNLOADS -> DownloadsScreen(
                    downloads = downloads,
                    onPlayDownloaded = openPlayer,
                    onDeleteDownload = { viewModel.removeDownload(it) },
                    modifier = Modifier.padding(innerPadding)
                )

                BottomTab.SETTINGS -> SettingsScreen(
                    initialInstanceUrl = viewModel.getConfiguredInstanceUrl(),
                    onSaveInstanceUrl = { viewModel.saveConfiguredInstanceUrl(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
