@file:Suppress("TooManyFunctions")

package com.drivestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivestream.app.R
import com.drivestream.app.data.BrowseViewMode
import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.ui.player.AVAILABLE_SPEEDS
import com.drivestream.app.ui.player.NORMAL_PLAYBACK_SPEED
import com.drivestream.app.ui.settings.SettingsMessage
import com.drivestream.app.ui.settings.SettingsViewModel
import com.drivestream.app.ui.theme.AccentBlue
import com.drivestream.app.ui.theme.AccentRed
import com.drivestream.app.ui.theme.DarkElevated
import com.drivestream.app.ui.theme.DarkSurface
import com.drivestream.app.ui.theme.PureBlack
import com.drivestream.app.ui.theme.TextPrimary
import com.drivestream.app.ui.theme.TextSecondary

private const val CARD_CORNER_RADIUS = 14
private const val ROW_MIN_HEIGHT = 56
private const val DIVIDER_ALPHA = 0.08f
private const val COLUMN_SPACING = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isHistoryDialogVisible by remember { mutableStateOf(false) }

    val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)
    val historyClearedMessage = stringResource(R.string.settings_history_cleared)

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            when (message) {
                SettingsMessage.CACHE_CLEARED -> cacheClearedMessage
                SettingsMessage.HISTORY_CLEARED -> historyClearedMessage
            }
        )
        viewModel.consumeMessage()
    }

    if (isHistoryDialogVisible) {
        AlertDialog(
            onDismissRequest = { isHistoryDialogVisible = false },
            containerColor = DarkElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(text = stringResource(R.string.settings_clear_history)) },
            text = { Text(text = stringResource(R.string.confirm_clear_history)) },
            confirmButton = {
                TextButton(onClick = {
                    isHistoryDialogVisible = false
                    viewModel.clearWatchHistory()
                }) {
                    Text(text = stringResource(R.string.action_delete), color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { isHistoryDialogVisible = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(COLUMN_SPACING.dp)
        ) {
            LibrarySection(
                sortOption = uiState.settings.sortOption,
                viewMode = uiState.settings.viewMode,
                onSortOptionChange = { viewModel.setSortOption(it) },
                onViewModeChange = { viewModel.setViewMode(it) }
            )

            PlaybackSection(
                autoplayNext = uiState.settings.autoplayNext,
                defaultPlaybackSpeed = uiState.settings.defaultPlaybackSpeed,
                onAutoplayNextChange = { viewModel.setAutoplayNext(it) },
                onDefaultPlaybackSpeedChange = { viewModel.setDefaultPlaybackSpeed(it) }
            )

            BrowsingSection(
                resumeLastFolder = uiState.settings.resumeLastFolder,
                onResumeLastFolderChange = { viewModel.setResumeLastFolder(it) }
            )

            DataSection(
                onClearCache = { viewModel.clearDriveCache() },
                onClearHistory = { isHistoryDialogVisible = true }
            )

            AccountSection(
                appVersion = uiState.appVersion,
                onSignOut = {
                    viewModel.signOut()
                    onSignedOut()
                }
            )
        }
    }
}

@Composable
private fun LibrarySection(
    sortOption: FileSortOption,
    viewMode: BrowseViewMode,
    onSortOptionChange: (FileSortOption) -> Unit,
    onViewModeChange: (BrowseViewMode) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_section_library)) {
        SettingsDropdownRow(
            title = stringResource(R.string.settings_default_sort),
            current = sortOption,
            options = sortOptionLabels(),
            onSelect = onSortOptionChange
        )
        SettingsDivider()
        SettingsDropdownRow(
            title = stringResource(R.string.settings_view_mode),
            current = viewMode,
            options = listOf(
                BrowseViewMode.LIST to stringResource(R.string.view_mode_list),
                BrowseViewMode.GRID to stringResource(R.string.view_mode_grid)
            ),
            onSelect = onViewModeChange
        )
    }
}

@Composable
private fun PlaybackSection(
    autoplayNext: Boolean,
    defaultPlaybackSpeed: Float,
    onAutoplayNextChange: (Boolean) -> Unit,
    onDefaultPlaybackSpeedChange: (Float) -> Unit
) {
    val normalLabel = stringResource(R.string.speed_normal)

    SettingsSection(title = stringResource(R.string.settings_section_playback)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_autoplay_next),
            checked = autoplayNext,
            onCheckedChange = onAutoplayNextChange
        )
        SettingsDivider()
        SettingsDropdownRow(
            title = stringResource(R.string.settings_default_speed),
            current = defaultPlaybackSpeed,
            options = AVAILABLE_SPEEDS.map { speed ->
                val label = if (speed == NORMAL_PLAYBACK_SPEED) "${speed}x ($normalLabel)" else "${speed}x"
                speed to label
            },
            onSelect = onDefaultPlaybackSpeedChange
        )
    }
}

@Composable
private fun BrowsingSection(
    resumeLastFolder: Boolean,
    onResumeLastFolderChange: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_section_browsing)) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_resume_last_folder),
            checked = resumeLastFolder,
            onCheckedChange = onResumeLastFolderChange
        )
    }
}

@Composable
private fun DataSection(
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_section_data)) {
        SettingsActionRow(
            title = stringResource(R.string.settings_clear_cache),
            onClick = onClearCache
        )
        SettingsDivider()
        SettingsActionRow(
            title = stringResource(R.string.settings_clear_history),
            onClick = onClearHistory
        )
    }
}

@Composable
private fun AccountSection(
    appVersion: String,
    onSignOut: () -> Unit
) {
    SettingsSection(title = stringResource(R.string.settings_section_account)) {
        SettingsInfoRow(
            title = stringResource(R.string.settings_app_version),
            value = appVersion
        )
        SettingsDivider()
        SettingsActionRow(
            title = stringResource(R.string.sign_out),
            titleColor = AccentRed,
            showChevron = false,
            onClick = onSignOut
        )
    }
}

@Composable
private fun sortOptionLabels(): List<Pair<FileSortOption, String>> = listOf(
    FileSortOption.NAME_ASC to stringResource(R.string.sort_name_asc),
    FileSortOption.NAME_DESC to stringResource(R.string.sort_name_desc),
    FileSortOption.DATE_DESC to stringResource(R.string.sort_date_desc),
    FileSortOption.DATE_ASC to stringResource(R.string.sort_date_asc),
    FileSortOption.SIZE_DESC to stringResource(R.string.sort_size_desc),
    FileSortOption.SIZE_ASC to stringResource(R.string.sort_size_asc)
)

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(CARD_CORNER_RADIUS.dp),
            colors = CardDefaults.cardColors(containerColor = DarkElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = DIVIDER_ALPHA))
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_MIN_HEIGHT.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureBlack,
                checkedTrackColor = AccentBlue
            )
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_MIN_HEIGHT.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    titleColor: Color = TextPrimary,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_MIN_HEIGHT.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor
        )
        if (showChevron) {
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
private fun <T> SettingsDropdownRow(
    title: String,
    current: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_MIN_HEIGHT.dp)
            .clickable { isMenuOpen = true }
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = options.firstOrNull { it.first == current }?.second.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentBlue
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.action_sort),
                    tint = AccentBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                modifier = Modifier.background(DarkElevated)
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = if (value == current) AccentBlue else TextPrimary
                            )
                        },
                        onClick = {
                            onSelect(value)
                            isMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}
