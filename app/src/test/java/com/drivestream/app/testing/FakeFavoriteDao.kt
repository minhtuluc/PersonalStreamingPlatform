package com.drivestream.app.testing

import com.drivestream.app.data.FavoriteDao
import com.drivestream.app.data.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFavoriteDao : FavoriteDao {

    private val items = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override fun getAllFavorites(): Flow<List<FavoriteEntity>> = items

    override fun getFavoriteIds(): Flow<List<String>> =
        items.map { favorites -> favorites.map { it.fileId } }

    override suspend fun getById(fileId: String): FavoriteEntity? =
        items.value.firstOrNull { it.fileId == fileId }

    override suspend fun upsert(entity: FavoriteEntity) {
        items.value = items.value.filterNot { it.fileId == entity.fileId } + entity
    }

    override suspend fun delete(fileId: String) {
        items.value = items.value.filterNot { it.fileId == fileId }
    }

    override suspend fun clearAll() {
        items.value = emptyList()
    }
}
