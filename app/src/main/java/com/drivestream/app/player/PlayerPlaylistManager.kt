package com.drivestream.app.player

import com.drivestream.app.data.model.DriveFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerPlaylistManager @Inject constructor() {

    private val _playlist = MutableStateFlow<List<DriveFile>>(emptyList())
    val playlist: StateFlow<List<DriveFile>> = _playlist.asStateFlow()

    fun setPlaylist(files: List<DriveFile>) {
        _playlist.value = files
    }

    fun getPlaylist(): List<DriveFile> = _playlist.value

    fun clear() {
        _playlist.value = emptyList()
    }
}
