package com.drivestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.drivestream.app.R
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.ui.theme.AccentBlue
import com.drivestream.app.ui.theme.AccentTeal
import com.drivestream.app.ui.theme.DarkElevated
import com.drivestream.app.ui.theme.DarkSurface
import com.drivestream.app.ui.theme.PureBlack
import com.drivestream.app.ui.theme.TextPrimary
import com.drivestream.app.ui.theme.TextSecondary
import java.util.Locale

private const val THUMBNAIL_WIDTH = 124
private const val THUMBNAIL_HEIGHT = 76
private const val CORNER_RADIUS = 12
private const val SMALL_CORNER_RADIUS = 6
private const val PILL_CORNER_RADIUS = 4
private const val BADGE_TEXT_SIZE = 10
private const val DURATION_TEXT_SIZE = 10
private const val KILO_BYTE = 1024L
private const val MEGA_BYTE = 1024L * 1024L
private const val GIGA_BYTE = 1024L * 1024L * 1024L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val MILLIS_PER_SECOND = 1000L

@Composable
fun FolderCard(
    folder: DriveFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CORNER_RADIUS.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentBlue.copy(alpha = 0.2f), AccentTeal.copy(alpha = 0.2f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun VideoCard(
    video: DriveFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(CORNER_RADIUS.dp),
        colors = CardDefaults.cardColors(containerColor = DarkElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoThumbnail(
                video = video,
                modifier = Modifier
                    .width(THUMBNAIL_WIDTH.dp)
                    .height(THUMBNAIL_HEIGHT.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(THUMBNAIL_HEIGHT.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (video.resolution != null) {
                        ResolutionBadge(label = video.resolution.label)
                    }

                    Text(
                        text = formatFileSize(video.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (video.modifiedAtEpochMs > 0L) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = formatDate(video.modifiedAtEpochMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (onDownloadClick != null) {
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = stringResource(R.string.action_download),
                        tint = AccentBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoThumbnail(
    video: DriveFile,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SMALL_CORNER_RADIUS.dp))
            .background(DarkSurface)
    ) {
        if (!video.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(video.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = video.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(DarkElevated, DarkSurface)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (video.durationMs != null && video.durationMs > 0L) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(PILL_CORNER_RADIUS.dp),
                color = PureBlack.copy(alpha = 0.8f)
            ) {
                Text(
                    text = formatDuration(video.durationMs),
                    color = Color.White,
                    fontSize = DURATION_TEXT_SIZE.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ResolutionBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    val badgeColor = if (label.contains("4K", ignoreCase = true)) {
        AccentTeal
    } else {
        AccentBlue
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PILL_CORNER_RADIUS.dp),
        color = badgeColor.copy(alpha = 0.15f)
    ) {
        Text(
            text = label,
            color = badgeColor,
            fontSize = BADGE_TEXT_SIZE.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= GIGA_BYTE -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / GIGA_BYTE)
        bytes >= MEGA_BYTE -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / MEGA_BYTE)
        bytes >= KILO_BYTE -> String.format(Locale.US, "%.0f KB", bytes.toDouble() / KILO_BYTE)
        bytes > 0L -> "$bytes B"
        else -> "0 B"
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private val DATE_FORMATTER: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault())

fun formatDate(epochMs: Long): String {
    return DATE_FORMATTER.format(java.time.Instant.ofEpochMilli(epochMs))
}
