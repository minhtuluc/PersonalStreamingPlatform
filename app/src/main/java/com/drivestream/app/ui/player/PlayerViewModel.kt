package com.drivestream.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.toRoute
import com.drivestream.app.data.DownloadRepository
import com.drivestream.app.data.PlayerRepository
import com.drivestream.app.player.GDriveDataSourceFactory
import com.drivestream.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gDriveDataSourceFactory: GDriveDataSourceFactory,
    private val loadControl: LoadControl,
    private val playerRepository: PlayerRepository,
    private val downloadRepository: DownloadRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val route = runCatching { savedStateHandle.toRoute<Screen.Player>() }.getOrNull()
    private var _fileId: String = route?.fileId ?: savedStateHandle.get<String>("fileId").orEmpty()
    private var _title: String = Uri.decode(route?.title ?: savedStateHandle.get<String>("title").orEmpty())

    val fileId: String get() = _fileId
    val title: String get() = _title

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            fileId = _fileId,
            title = _title,
            isLoading = true
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val player: ExoPlayer = createExoPlayer()
    private var progressJob: Job? = null
    private var periodicSaveJob: Job? = null

    init {
        setupPlayer()
        startProgressUpdates()
        startPeriodicSave()
    }

    private fun createExoPlayer(): ExoPlayer {
        val dataSourceFactory = DefaultDataSource.Factory(context, gDriveDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    private fun setupPlayer() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _uiState.update { it.copy(isBuffering = true, isLoading = false) }
                    }
                    Player.STATE_READY -> {
                        val duration = player.duration.coerceAtLeast(0L)
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                isLoading = false,
                                durationMs = duration
                            )
                        }
                    }
                    Player.STATE_ENDED -> {
                        _uiState.update { it.copy(isPlaying = false, isBuffering = false) }
                        saveCurrentPosition()
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (!isPlaying) {
                    saveCurrentPosition()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isBuffering = false,
                        errorMessage = error.localizedMessage ?: "Playback error occurred"
                    )
                }
            }
        })

        if (_fileId.isNotBlank()) {
            startPlayback()
        }
    }

    fun initialize(incomingFileId: String, incomingTitle: String) {
        val decodedTitle = Uri.decode(incomingTitle)
        if (_fileId.isBlank() && incomingFileId.isNotBlank()) {
            _fileId = incomingFileId
            _title = decodedTitle
            _uiState.update { it.copy(fileId = incomingFileId, title = decodedTitle) }
            startPlayback()
        }
    }

    private fun startPlayback() {
        if (_fileId.isBlank()) return
        viewModelScope.launch {
            val savedPosition = playerRepository.getSavedPosition(_fileId)
            val downloadedVideo = downloadRepository.getCompletedDownload(_fileId)
            val mediaItem = if (downloadedVideo != null) {
                MediaItem.fromUri(Uri.fromFile(File(downloadedVideo.localPath)))
            } else {
                MediaItem.fromUri(Uri.parse("gdrive://$_fileId"))
            }
            player.setMediaItem(mediaItem)
            player.prepare()
            if (savedPosition > 0L) {
                player.seekTo(savedPosition)
            }
            player.playWhenReady = true
        }
    }

    private fun startProgressUpdates() {
        progressJob = viewModelScope.launch {
            while (true) {
                if (player.playbackState == Player.STATE_READY || player.isPlaying) {
                    _uiState.update {
                        it.copy(
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun startPeriodicSave() {
        periodicSaveJob = viewModelScope.launch {
            while (true) {
                delay(PERIODIC_SAVE_INTERVAL_MS)
                saveCurrentPosition()
            }
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L)))
        _uiState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun seekForward() {
        seekRelative(SEEK_FORWARD_OFFSET_MS)
    }

    fun seekBackward() {
        seekRelative(SEEK_BACKWARD_OFFSET_MS)
    }

    private fun seekRelative(offsetMs: Long) {
        val target = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleControlsVisibility() {
        if (!_uiState.value.isLocked) {
            _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
        }
    }

    fun setControlsVisible(visible: Boolean) {
        if (!_uiState.value.isLocked) {
            _uiState.update { it.copy(isControlsVisible = visible) }
        }
    }

    fun toggleLock() {
        _uiState.update {
            val newLocked = !it.isLocked
            it.copy(
                isLocked = newLocked,
                isControlsVisible = !newLocked
            )
        }
    }

    fun setPipMode(inPip: Boolean) {
        _uiState.update {
            it.copy(
                isInPipMode = inPip,
                isControlsVisible = !inPip
            )
        }
    }

    fun retry() {
        _uiState.update { it.copy(errorMessage = null, isLoading = true) }
        player.prepare()
        player.play()
    }

    fun saveCurrentPosition() {
        val position = player.currentPosition
        val duration = player.duration
        if (position > 0L && duration > 0L && _fileId.isNotBlank()) {
            viewModelScope.launch {
                playerRepository.savePlaybackPosition(
                    fileId = _fileId,
                    fileName = _title,
                    positionMs = position,
                    durationMs = duration
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        periodicSaveJob?.cancel()
        saveCurrentPosition()
        player.release()
    }

    companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        const val PERIODIC_SAVE_INTERVAL_MS = 10_000L
        const val SEEK_FORWARD_OFFSET_MS = 30_000L
        const val SEEK_BACKWARD_OFFSET_MS = -10_000L
    }
}
