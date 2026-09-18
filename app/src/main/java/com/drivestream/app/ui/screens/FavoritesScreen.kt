package com.drivestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.drivestream.app.R
import com.drivestream.app.data.FavoriteEntity
import com.drivestream.app.ui.components.formatFileSize
import com.drivestream.app.ui.favorites.FavoritesViewModel
import com.drivestream.app.ui.theme.AccentAmber
import com.drivestream.app.ui.theme.AccentTeal
import com.drivestream.app.ui.theme.DarkElevated
import com.drivestream.app.ui.theme.DarkSurface
import com.drivestream.app.ui.theme.PureBlack
import com.drivestream.app.ui.theme.TextPrimary
import com.drivestream.app.ui.theme.TextSecondary

private const val THUMBNAIL_WIDTH = 96
private const val THUMBNAIL_HEIGHT = 60
private const val THUMBNAIL_CORNER_RADIUS = 8
private const val CARD_CORNER_RADIUS = 12
private const val EMPTY_ICON_SIZE = 64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onNavigateBack: () -> Unit,
    onPlayVideo: (String, String) -> Unit,
    onOpenFolder: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.favorites_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface)
                .padding(innerPadding)
        ) {
            if (favorites.isEmpty()) {
                FavoritesEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = favorites,
                        key = { it.fileId }
                    ) { favorite ->
                        FavoriteRow(
                            favorite = favorite,
                            onClick = {
                                if (favorite.isFolder) {
                                    onOpenFolder(favorite.fileId, favorite.fileName)
                                } else {
                                    onPlayVideo(favorite.fileId, favorite.fileName)
                                }
                            },
                            onRemove = { viewModel.remove(favorite.fileId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FavoriteThumbnail(favorite = favorite)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (favorite.isFolder) {
                        stringResource(R.string.favorites_folder_label)
                    } else {
                        formatFileSize(favorite.fileSize)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = stringResource(R.string.favorites_remove),
                    tint = AccentAmber
                )
            }
        }
    }
}

@Composable
private fun FavoriteThumbnail(favorite: FavoriteEntity) {
    Box(
        modifier = Modifier
            .width(THUMBNAIL_WIDTH.dp)
            .height(THUMBNAIL_HEIGHT.dp)
            .clip(RoundedCornerShape(THUMBNAIL_CORNER_RADIUS.dp))
            .background(DarkSurface),
        contentAlignment = Alignment.Center
    ) {
        if (!favorite.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(favorite.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = favorite.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (favorite.isFolder) Icons.Default.Folder else Icons.Default.Movie,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun FavoritesEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.StarBorder,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(EMPTY_ICON_SIZE.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.favorites_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}
