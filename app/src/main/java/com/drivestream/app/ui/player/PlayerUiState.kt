package com.drivestream.app.ui.player

data class PlayerUiState(
    val fileId: String = "",
    val title: String = "",
    val isPlaying: Boolean = true,
    val isLoading: Boolean = true,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isControlsVisible: Boolean = true,
    val isLocked: Boolean = false,
    val errorMessage: String? = null,
    val isInPipMode: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val bufferedProgress: Float
        get() = if (durationMs > 0L) (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
