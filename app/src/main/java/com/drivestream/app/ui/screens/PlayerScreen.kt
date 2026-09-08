package com.drivestream.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.drivestream.app.R
import com.drivestream.app.ui.components.formatDuration
import com.drivestream.app.ui.player.PlayerUiState
import com.drivestream.app.ui.player.PlayerViewModel
import com.drivestream.app.ui.theme.AccentBlue
import com.drivestream.app.ui.theme.AccentRed
import com.drivestream.app.ui.theme.DarkElevated
import com.drivestream.app.ui.theme.PureBlack
import com.drivestream.app.ui.theme.TextPrimary
import com.drivestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val AUTO_HIDE_CONTROLS_MS = 4000L
private const val CONTROLS_BUTTON_SIZE = 48
private const val PLAY_BUTTON_SIZE = 72
private const val WARNING_ICON_SIZE = 56
private const val BUFFER_SPINNER_SIZE = 48
private const val CORNER_RADIUS = 16
private const val TIME_TEXT_SIZE = 12
private const val PIP_RATIO_NUMERATOR = 16
private const val PIP_RATIO_DENOMINATOR = 9
private const val SPEED_HALF = 0.5f
private const val SPEED_THREE_QUARTERS = 0.75f
private const val SPEED_NORMAL = 1.0f
private const val SPEED_ONE_AND_A_QUARTER = 1.25f
private const val SPEED_ONE_AND_A_HALF = 1.5f
private const val SPEED_DOUBLE = 2.0f

private val AVAILABLE_SPEEDS = listOf(
    SPEED_HALF,
    SPEED_THREE_QUARTERS,
    SPEED_NORMAL,
    SPEED_ONE_AND_A_QUARTER,
    SPEED_ONE_AND_A_HALF,
    SPEED_DOUBLE
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    fileId: String,
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(fileId, title) {
        viewModel.initialize(fileId, title)
    }

    // Immersive Fullscreen Mode
    DisposableEffect(view) {
        runCatching {
            val window = (context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            runCatching {
                val currentWindow = (context as? Activity)?.window
                if (currentWindow != null) {
                    WindowCompat.getInsetsController(currentWindow, view).show(WindowInsetsCompat.Type.systemBars())
                }
            }
            viewModel.saveCurrentPosition()
        }
    }

    // Auto-hide controls ticker
    LaunchedEffect(uiState.isControlsVisible, uiState.isPlaying) {
        if (uiState.isControlsVisible && uiState.isPlaying && !uiState.isLocked) {
            delay(AUTO_HIDE_CONTROLS_MS)
            viewModel.setControlsVisible(false)
        }
    }

    BackHandler {
        if (uiState.isLocked) {
            viewModel.toggleLock()
        } else {
            onNavigateBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .pointerInput(uiState.isLocked) {
                detectTapGestures(
                    onTap = {
                        viewModel.toggleControlsVisibility()
                    },
                    onDoubleTap = { offset ->
                        if (!uiState.isLocked) {
                            if (offset.x < size.width / 2) {
                                viewModel.seekBackward()
                            } else {
                                viewModel.seekForward()
                            }
                        }
                    }
                )
            }
    ) {
        // Video Surface via Media3 PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = viewModel.player
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Spinner
        if (uiState.isBuffering || uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(BUFFER_SPINNER_SIZE.dp),
                    color = AccentBlue,
                    strokeWidth = 4.dp
                )
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerControlsOverlay(
                uiState = uiState,
                onNavigateBack = onNavigateBack,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeekBackward = { viewModel.seekBackward() },
                onSeekForward = { viewModel.seekForward() },
                onSeekTo = { viewModel.seekTo(it) },
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onToggleLock = { viewModel.toggleLock() },
                onEnterPip = {
                    enterPictureInPicture(context as? Activity)
                }
            )
        }

        // Error Dialog Overlay
        if (uiState.errorMessage != null) {
            PlayerErrorOverlay(
                message = uiState.errorMessage ?: "Lỗi phát video",
                onRetry = { viewModel.retry() },
                onClose = onNavigateBack
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    onNavigateBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleLock: () -> Unit,
    onEnterPip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(16.dp)
    ) {
        if (uiState.isLocked) {
            // Only show Lock button when locked
            IconButton(
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(CONTROLS_BUTTON_SIZE.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.unlock_controls),
                    tint = AccentBlue
                )
            }
        } else {
            // Top Bar
            PlayerTopBar(
                title = uiState.title,
                playbackSpeed = uiState.playbackSpeed,
                onNavigateBack = onNavigateBack,
                onToggleLock = onToggleLock,
                onSpeedChange = onSpeedChange,
                onEnterPip = onEnterPip,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Center Play / Rewind / Forward Controls
            PlayerCenterControls(
                isPlaying = uiState.isPlaying,
                onPlayPause = onPlayPause,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                modifier = Modifier.align(Alignment.Center)
            )

            // Bottom Bar with Progress Slider & Timers
            PlayerBottomBar(
                currentPositionMs = uiState.currentPositionMs,
                durationMs = uiState.durationMs,
                onSeekTo = onSeekTo,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    playbackSpeed: Float,
    onNavigateBack: () -> Unit,
    onToggleLock: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSpeedMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Speed Menu Button
            Box {
                IconButton(onClick = { isSpeedMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(R.string.playback_speed),
                        tint = TextPrimary
                    )
                }
                DropdownMenu(
                    expanded = isSpeedMenuOpen,
                    onDismissRequest = { isSpeedMenuOpen = false },
                    modifier = Modifier.background(DarkElevated)
                ) {
                    val normalLabel = stringResource(R.string.speed_normal)
                    AVAILABLE_SPEEDS.forEach { speed ->
                        val speedText = if (speed == SPEED_NORMAL) "${speed}x ($normalLabel)" else "${speed}x"
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = speedText,
                                    color = if (speed == playbackSpeed) AccentBlue else TextPrimary
                                )
                            },
                            onClick = {
                                onSpeedChange(speed)
                                isSpeedMenuOpen = false
                            }
                        )
                    }
                }
            }

            // PiP Button
            IconButton(onClick = onEnterPip) {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = stringResource(R.string.pip_mode),
                    tint = TextPrimary
                )
            }

            // Lock Screen Button
            IconButton(onClick = onToggleLock) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = stringResource(R.string.lock_controls),
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSeekBackward,
            modifier = Modifier
                .size(CONTROLS_BUTTON_SIZE.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = stringResource(R.string.rewind_10s),
                tint = TextPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(PLAY_BUTTON_SIZE.dp)
                .clip(CircleShape)
                .background(AccentBlue)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        IconButton(
            onClick = onSeekForward,
            modifier = Modifier
                .size(CONTROLS_BUTTON_SIZE.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Default.Forward30,
                contentDescription = stringResource(R.string.forward_30s),
                tint = TextPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun PlayerBottomBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDragging) {
        dragProgress
    } else {
        if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = currentProgress,
            onValueChange = { progress ->
                isDragging = true
                dragProgress = progress
            },
            onValueChangeFinished = {
                isDragging = false
                val targetMs = (dragProgress * durationMs).toLong()
                onSeekTo(targetMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = AccentBlue,
                activeTrackColor = AccentBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayCurrent = if (isDragging) (dragProgress * durationMs).toLong() else currentPositionMs
            Text(
                text = formatDuration(displayCurrent),
                color = TextPrimary,
                fontSize = TIME_TEXT_SIZE.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatDuration(durationMs),
                color = TextSecondary,
                fontSize = TIME_TEXT_SIZE.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(CORNER_RADIUS.dp),
            color = DarkElevated,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(WARNING_ICON_SIZE.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(R.string.action_close), color = TextSecondary)
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(R.string.action_retry), color = Color.White)
                    }
                }
            }
        }
    }
}

private fun enterPictureInPicture(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val params = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(PIP_RATIO_NUMERATOR, PIP_RATIO_DENOMINATOR))
        .build()
    activity.enterPictureInPictureMode(params)
}
