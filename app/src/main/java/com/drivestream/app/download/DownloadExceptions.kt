package com.drivestream.app.download

import java.io.IOException

enum class DownloadFailure {
    INSUFFICIENT_STORAGE,
    NETWORK,
    OTHER
}

class InsufficientStorageException(
    message: String,
    val requiredBytes: Long = 0L,
    val availableBytes: Long = 0L
) : IOException(message)

class DownloadFailedException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
