package com.drivestream.app.ui.settings

import com.drivestream.app.data.AppSettings

enum class SettingsMessage {
    CACHE_CLEARED,
    HISTORY_CLEARED
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val appVersion: String = "",
    val message: SettingsMessage? = null
)
