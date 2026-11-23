package com.streamvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.streamvault.app.data.local.DownloadedVideoEntity
import com.streamvault.app.data.model.ChannelInfo
import com.streamvault.app.data.model.ResolvedMedia
import com.streamvault.app.data.model.StreamOption
import com.streamvault.app.data.model.VideoItem
import com.streamvault.app.data.repository.LoadOutcome
import com.streamvault.app.data.repository.VideoRepository
import com.streamvault.app.download.VideoDownloadHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentChannel: ChannelInfo? = null
)

data class PlayerUiState(
    val isLoading: Boolean = false,
    val resolvedMedia: ResolvedMedia? = null,
    val selectedStream: StreamOption? = null,
    val playbackSpeed: Float = 1.0f,
    val maxVideoHeight: Int = Int.MAX_VALUE, // Int.MAX_VALUE = Auto
    val errorMessage: String? = null,
    val statusBanner: String? = null
)

class MainViewModel(
    private val repository: VideoRepository,
    private val downloadHelper: VideoDownloadHelper
) : ViewModel() {

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    val favorites: StateFlow<List<VideoItem>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = repository.observeFavoriteIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloads: StateFlow<List<DownloadedVideoEntity>> = repository.observeDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onUrlInputChanged(newValue: String) {
        _homeState.update { it.copy(urlInput = newValue, errorMessage = null) }
    }

    fun submitUrl(onOpenVideo: (VideoItem) -> Unit) {
        val query = _homeState.value.urlInput
        viewModelScope.launch {
            _homeState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                when (val outcome = repository.processUserUrl(query)) {
                    is LoadOutcome.ChannelLoaded -> {
                        _homeState.update {
                            it.copy(
                                isLoading = false,
                                currentChannel = outcome.channel,
                                errorMessage = null
                            )
                        }
                    }
                    is LoadOutcome.VideoLoaded -> {
                        _homeState.update { it.copy(isLoading = false, errorMessage = null) }
                        onOpenVideo(outcome.video)
                    }
                }
            } catch (e: Exception) {
                _homeState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Unable to load link."
                    )
                }
            }
        }
    }

    fun loadVideoForPlayback(video: VideoItem) {
        viewModelScope.launch {
            _playerState.update {
                PlayerUiState(isLoading = true)
            }
            try {
                val resolved = repository.resolvePlayableMedia(video)
                _playerState.update {
                    it.copy(
                        isLoading = false,
                        resolvedMedia = resolved,
                        selectedStream = resolved.streams.firstOrNull(),
                        playbackSpeed = 1.0f,
                        maxVideoHeight = Int.MAX_VALUE
                    )
                }
            } catch (e: Exception) {
                _playerState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load video stream."
                    )
                }
            }
        }
    }

    fun selectStreamOption(option: StreamOption) {
        _playerState.update {
            it.copy(
                selectedStream = option,
                maxVideoHeight = if (option.isAdaptive) Int.MAX_VALUE else option.height
            )
        }
    }

    fun setMaxVideoHeight(height: Int) {
        _playerState.update { it.copy(maxVideoHeight = height) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            repository.toggleFavorite(video)
        }
    }

    fun downloadVideo(video: VideoItem, stream: StreamOption) {
        downloadHelper.enqueueDownload(video, stream)
        _playerState.update {
            it.copy(statusBanner = "Download started (${stream.qualityLabel}). Check the Downloads tab.")
        }
    }

    fun dismissPlayerBanner() {
        _playerState.update { it.copy(statusBanner = null) }
    }

    fun removeDownload(entity: DownloadedVideoEntity) {
        downloadHelper.removeDownload(entity)
    }

    fun getConfiguredInstanceUrl(): String = repository.getConfiguredInstanceUrl()

    fun saveConfiguredInstanceUrl(url: String) {
        repository.setConfiguredInstanceUrl(url)
    }

    class Factory(
        private val repository: VideoRepository,
        private val downloadHelper: VideoDownloadHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, downloadHelper) as T
        }
    }
}
