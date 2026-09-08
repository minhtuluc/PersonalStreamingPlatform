package com.drivestream.app.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toDownloadStatus(value: String?): DownloadStatus {
        if (value == null) return DownloadStatus.FAILED
        return runCatching { DownloadStatus.valueOf(value) }
            .getOrDefault(DownloadStatus.FAILED)
    }
}
