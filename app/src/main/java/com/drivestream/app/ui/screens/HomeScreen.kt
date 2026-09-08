@file:Suppress("TooManyFunctions")

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.drivestream.app.data.DownloadedVideoEntity
import com.drivestream.app.data.WatchHistoryEntity
import com.drivestream.app.ui.home.HomeUiState
import com.drivestream.app.ui.home.HomeViewModel
import com.drivestream.app.ui.theme.AccentBlue
import com.drivestream.app.ui.theme.AccentGreen
import com.drivestream.app.ui.theme.AccentTeal
import com.drivestream.app.ui.theme.DarkElevated
import com.drivestream.app.ui.theme.DarkSurface
import com.drivestream.app.ui.theme.TextPrimary
import com.drivestream.app.ui.theme.TextSecondary
import java.util.Locale

private const val CARD_CORNER_RADIUS = 14
private const val ACTION_CARD_HEIGHT = 100
private const val CONTINUE_CARD_WIDTH = 220
private const val CONTINUE_THUMB_HEIGHT = 120
private const val DOWNLOAD_CARD_WIDTH = 200
private const val ICON_SIZE_MEDIUM = 32
private const val ICON_SIZE_SMALL = 20
private const val MAX_RECENT_ITEMS_DISPLAY = 5
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private const val BYTES_PER_MB = 1048576.0
private const val MB_PER_GB = 1024.0

@Composable
fun HomeScreen(
    onNavigateToBrowser: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onPlayVideo: (String, String) -> Unit,
    onSignOut: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            HomeHeader(onSignOut = {
                viewModel.signOut()
                onSignOut()
            })
        }

        item {
            HomeActionCards(
                onNavigateToBrowser = onNavigateToBrowser,
                onNavigateToDownloads = onNavigateToDownloads
            )
        }

        if (uiState.continueWatching.isNotEmpty()) {
            item {
                ContinueWatchingSection(
                    items = uiState.continueWatching,
                    onPlayVideo = onPlayVideo
                )
            }
        }

        if (uiState.downloadedVideos.isNotEmpty()) {
            item {
                HomeDownloadsSection(
                    items = uiState.downloadedVideos,
                    onPlayVideo = onPlayVideo
                )
            }
        }

        if (uiState.recentlyViewed.isNotEmpty()) {
            item {
                RecentlyViewedSection(
                    items = uiState.recentlyViewed,
                    onPlayVideo = onPlayVideo
                )
            }
        }

        val hasNoContent = uiState.continueWatching.isEmpty() &&
            uiState.downloadedVideos.isEmpty() &&
            uiState.recentlyViewed.isEmpty()

        if (hasNoContent && !uiState.isLoading) {
            item {
                HomeEmptyView()
            }
        }
    }
}

@Composable
private fun HomeHeader(onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        IconButton(onClick = onSignOut) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Sign Out",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun HomeActionCards(
    onNavigateToBrowser: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeActionCard(
            title = stringResource(R.string.browse_drive),
            icon = Icons.Default.Folder,
            accentColor = AccentBlue,
            onClick = onNavigateToBrowser,
            modifier = Modifier.weight(1f)
        )

        HomeActionCard(
            title = stringResource(R.string.nav_downloads),
            icon = Icons.Default.Download,
            accentColor = AccentTeal,
            onClick = onNavigateToDownloads,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(ACTION_CARD_HEIGHT.dp),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(ICON_SIZE_MEDIUM.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<WatchHistoryEntity>,
    onPlayVideo: (String, String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.continue_watching),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.fileId }) { video ->
                ContinueWatchingCard(video = video, onClick = { onPlayVideo(video.fileId, video.fileName) })
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    video: WatchHistoryEntity,
    onClick: () -> Unit
) {
    val progress = if (video.duration > 0L) (video.lastPosition.toFloat() / video.duration).coerceIn(0f, 1f) else 0f

    Card(
        onClick = onClick,
        modifier = Modifier.width(CONTINUE_CARD_WIDTH.dp),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTINUE_THUMB_HEIGHT.dp)
                    .clip(RoundedCornerShape(topStart = CARD_CORNER_RADIUS.dp, topEnd = CARD_CORNER_RADIUS.dp))
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(video.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = video.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(40.dp)
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = AccentBlue,
                trackColor = Color.White.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Square
            )

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatRemainingTime(video.lastPosition, video.duration),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun HomeDownloadsSection(
    items: List<DownloadedVideoEntity>,
    onPlayVideo: (String, String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FileDownloadDone,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(ICON_SIZE_SMALL.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.downloaded_videos),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.fileId }) { download ->
                HomeDownloadCard(download = download, onClick = { onPlayVideo(download.fileId, download.fileName) })
            }
        }
    }
}

@Composable
private fun HomeDownloadCard(
    download: DownloadedVideoEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(DOWNLOAD_CARD_WIDTH.dp),
        shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Offline",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = download.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatBytes(download.fileSize),
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RecentlyViewedSection(
    items: List<WatchHistoryEntity>,
    onPlayVideo: (String, String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(ICON_SIZE_SMALL.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.recently_viewed),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.take(MAX_RECENT_ITEMS_DISPLAY).forEach { item ->
                RecentlyViewedItemRow(item = item, onClick = { onPlayVideo(item.fileId, item.fileName) })
            }
        }
    }
}

@Composable
private fun RecentlyViewedItemRow(
    item: WatchHistoryEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatBytes(item.fileSize),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun HomeEmptyView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.no_continue_watching),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

private fun formatRemainingTime(currentMs: Long, totalMs: Long): String {
    val remainingSec = ((totalMs - currentMs) / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val mins = remainingSec / SECONDS_PER_MINUTE
    return "Còn lại ${mins}m"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes.toDouble() / BYTES_PER_MB
    return if (mb >= MB_PER_GB) {
        String.format(Locale.US, "%.1f GB", mb / MB_PER_GB)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}
