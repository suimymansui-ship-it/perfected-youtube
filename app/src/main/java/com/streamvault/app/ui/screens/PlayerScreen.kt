package com.streamvault.app.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.streamvault.app.data.model.StreamOption
import com.streamvault.app.data.model.VideoItem
import com.streamvault.app.ui.viewmodel.PlayerUiState

private val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
private val QUALITY_CAPS = listOf(
    "Auto" to Int.MAX_VALUE,
    "2160p" to 2160,
    "1440p" to 1440,
    "1080p" to 1080,
    "720p" to 720,
    "480p" to 480,
    "360p" to 360,
    "240p" to 240,
    "144p" to 144
)

@OptIn(UnstableApi::class)
@ExperimentalLayoutApi
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onSelectStreamOption: (StreamOption) -> Unit,
    onSelectMaxHeight: (Int) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onDownloadClick: (VideoItem, StreamOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().clearVideoSizeConstraints())
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    val activeStream = state.selectedStream
    LaunchedEffect(activeStream?.url) {
        val url = activeStream?.url ?: return@LaunchedEffect
        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        if (currentPos > 0L) {
            exoPlayer.seekTo(currentPos)
        }
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(state.playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(state.playbackSpeed)
    }

    LaunchedEffect(state.maxVideoHeight) {
        val paramsBuilder = trackSelector.buildUponParameters()
        if (state.maxVideoHeight == Int.MAX_VALUE) {
            paramsBuilder.clearVideoSizeConstraints()
        } else {
            paramsBuilder.setMaxVideoSize(Int.MAX_VALUE, state.maxVideoHeight)
        }
        trackSelector.setParameters(paramsBuilder)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Ad-Free Player",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            state.resolvedMedia?.videoItem?.let { video ->
                IconButton(onClick = { onToggleFavorite(video) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // 16:9 Media3 Player Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Video metadata & player controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            state.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.statusBanner?.let { banner ->
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = banner,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            val resolved = state.resolvedMedia
            if (resolved != null) {
                val video = resolved.videoItem
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = video.channelTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )

                resolved.sourceNotice?.let { notice ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = notice,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Action buttons: Favorite & Download Full Quality
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onToggleFavorite(video) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(if (isFavorite) "Favorited" else "Favorite")
                    }

                    Button(
                        onClick = {
                            val bestDownloadable = resolved.streams
                                .firstOrNull { !it.isAdaptive }
                                ?: resolved.streams.firstOrNull()
                            if (bestDownloadable != null) {
                                onDownloadClick(video, bestDownloadable)
                            }
                        },
                        enabled = resolved.streams.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("Download")
                    }
                }

                // Playback Speed Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Playback Speed (${state.playbackSpeed}x)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PLAYBACK_SPEEDS.forEach { speed ->
                            FilterChip(
                                selected = state.playbackSpeed == speed,
                                onClick = { onSelectSpeed(speed) },
                                label = { Text("${speed}x") }
                            )
                        }
                    }
                }

                // Video Quality Selector
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Video Quality",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (resolved.streams.size > 1) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            resolved.streams.forEach { option ->
                                FilterChip(
                                    selected = state.selectedStream?.qualityLabel == option.qualityLabel,
                                    onClick = { onSelectStreamOption(option) },
                                    label = { Text(option.qualityLabel) }
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QUALITY_CAPS.forEach { (label, maxHeight) ->
                                FilterChip(
                                    selected = state.maxVideoHeight == maxHeight,
                                    onClick = { onSelectMaxHeight(maxHeight) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }

                // Individual Quality Download Chips
                if (resolved.streams.any { !it.isAdaptive }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Download Specific Quality",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            resolved.streams.filter { !it.isAdaptive }.forEach { option ->
                                AssistChip(
                                    onClick = { onDownloadClick(video, option) },
                                    label = { Text("Save ${option.qualityLabel}") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
