package com.drivestream.app.player

/**
 * ExoPlayer buffer configurations optimized for original quality streaming
 * of 4K MP4/MKV/MOV videos (<= 5GB) over Google Drive API HTTP Range requests.
 */
object BufferConfig {
    const val MIN_BUFFER_MS = 30_000
    const val MAX_BUFFER_MS = 90_000
    const val BUFFER_FOR_PLAYBACK_MS = 2_500
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
    const val BACK_BUFFER_DURATION_MS = 30_000
    const val RETAIN_BACK_BUFFER = true

    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 30_000
    const val CHUNK_SIZE_BYTES = 2 * 1024 * 1024 // 2MB chunk
}
