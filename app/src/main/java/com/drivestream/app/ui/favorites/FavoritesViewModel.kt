package com.drivestream.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivestream.app.data.FavoriteEntity
import com.drivestream.app.data.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    val favorites: StateFlow<List<FavoriteEntity>> = favoritesRepository.favorites.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
        initialValue = emptyList()
    )

    fun remove(fileId: String) {
        viewModelScope.launch {
            favoritesRepository.remove(fileId)
        }
    }

    companion object {
        private const val SUBSCRIBE_TIMEOUT_MS = 5000L
    }
}
