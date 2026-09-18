package com.drivestream.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.drivestream.app.BuildConfig
import com.drivestream.app.auth.GoogleAuthManager
import com.drivestream.app.data.BrowseViewMode
import com.drivestream.app.data.DriveRepository
import com.drivestream.app.data.SettingsRepository
import com.drivestream.app.data.WatchHistoryDao
import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val driveRepository: DriveRepository,
    private val watchHistoryDao: WatchHistoryDao,
    private val googleAuthManager: GoogleAuthManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val messageFlow = MutableStateFlow<SettingsMessage?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        messageFlow
    ) { settings, message ->
        SettingsUiState(
            settings = settings,
            appVersion = BuildConfig.VERSION_NAME,
            message = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = SettingsUiState(
            settings = settingsRepository.settings.value,
            appVersion = BuildConfig.VERSION_NAME
        )
    )

    fun setSortOption(option: FileSortOption) {
        settingsRepository.setSortOption(option)
    }

    fun setViewMode(mode: BrowseViewMode) {
        settingsRepository.setViewMode(mode)
    }

    fun setAutoplayNext(enabled: Boolean) {
        settingsRepository.setAutoplayNext(enabled)
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        settingsRepository.setDefaultPlaybackSpeed(speed)
    }

    fun setResumeLastFolder(enabled: Boolean) {
        settingsRepository.setResumeLastFolder(enabled)
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearDriveCache() {
        viewModelScope.launch {
            driveRepository.clearAllCache()
            withContext(ioDispatcher) {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
            }
            messageFlow.value = SettingsMessage.CACHE_CLEARED
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            watchHistoryDao.clearAll()
            messageFlow.value = SettingsMessage.HISTORY_CLEARED
        }
    }

    fun signOut() {
        viewModelScope.launch {
            googleAuthManager.signOut()
        }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5000L
    }
}
