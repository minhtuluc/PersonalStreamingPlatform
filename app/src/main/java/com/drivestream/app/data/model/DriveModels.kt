package com.drivestream.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Resolution(
    val width: Int,
    val height: Int
) {
    val label: String get() = when {
        width >= RESOLUTION_4K_WIDTH -> "4K"
        width >= RESOLUTION_1440P_WIDTH -> "1440p"
        width >= RESOLUTION_1080P_WIDTH -> "1080p"
        width >= RESOLUTION_720P_WIDTH -> "720p"
        else -> "${height}p"
    }

    companion object {
        private const val RESOLUTION_4K_WIDTH = 3840
        private const val RESOLUTION_1440P_WIDTH = 2560
        private const val RESOLUTION_1080P_WIDTH = 1920
        private const val RESOLUTION_720P_WIDTH = 1280
    }
}

@Serializable
data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0L,
    val thumbnailUrl: String? = null,
    val resolution: Resolution? = null,
    val durationMs: Long? = null,
    val modifiedAtEpochMs: Long = 0L,
    val isFolder: Boolean = false
) {
    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}

@Serializable
data class DriveFileList(
    val files: List<DriveFile>,
    val nextPageToken: String? = null
)

@Serializable
data class GoogleDriveFileListDto(
    val nextPageToken: String? = null,
    val files: List<GoogleDriveFileDto> = emptyList()
)

@Serializable
data class GoogleDriveFileDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String? = null,
    val thumbnailLink: String? = null,
    val videoMediaMetadata: GoogleVideoMetadataDto? = null,
    val modifiedTime: String? = null
)

@Serializable
data class GoogleVideoMetadataDto(
    val width: Int? = null,
    val height: Int? = null,
    val durationMillis: String? = null
)
