package com.drivestream.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivestream.app.R
import com.drivestream.app.data.model.DriveFile
import com.drivestream.app.data.model.FileSortOption
import com.drivestream.app.ui.browser.DriveUiState
import com.drivestream.app.ui.browser.DriveViewModel
import com.drivestream.app.ui.components.FolderCard
import com.drivestream.app.ui.components.LoadingIndicator
import com.drivestream.app.ui.components.VideoCard
import com.drivestream.app.ui.theme.AccentBlue
import com.drivestream.app.ui.theme.AccentRed
import com.drivestream.app.ui.theme.DarkElevated
import com.drivestream.app.ui.theme.DarkSurface
import com.drivestream.app.ui.theme.PureBlack
import com.drivestream.app.ui.theme.TextPrimary
import com.drivestream.app.ui.theme.TextSecondary

private const val PAGINATION_THRESHOLD = 3
private const val ICON_LARGE_SIZE = 56
private const val SPINNER_SIZE = 28
private const val BUTTON_CORNER_RADIUS = 12

@Composable
fun BrowserScreen(
    folderId: String,
    folderName: String,
    onNavigateBack: () -> Unit,
    onVideoClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onFolderClick: (String, String) -> Unit = { _, _ -> },
    viewModel: DriveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchActive by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null && uiState.files.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    androidx.activity.compose.BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        viewModel.clearSearch()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            BrowserTopAppBar(
                folderName = folderName,
                isSearchActive = isSearchActive,
                searchQuery = uiState.searchQuery,
                currentSortOption = uiState.sortOption,
                onSearchToggle = { active ->
                    isSearchActive = active
                    if (!active) {
                        viewModel.clearSearch()
                    }
                },
                onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                onSortOptionChange = { viewModel.setSortOption(it) },
                onNavigateBack = onNavigateBack,
                onRefresh = { viewModel.refresh() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface)
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingIndicator()
                }
                uiState.errorMessage != null && uiState.files.isEmpty() -> {
                    BrowserErrorView(
                        message = uiState.errorMessage ?: stringResource(R.string.error_sign_in_failed),
                        onRetry = { viewModel.refresh() }
                    )
                }
                uiState.isEmpty -> {
                    BrowserEmptyView(
                        isSearching = uiState.isSearching,
                        onRefresh = { viewModel.refresh() }
                    )
                }
                else -> {
                    BrowserFileList(
                        uiState = uiState,
                        onFolderClick = onFolderClick,
                        onVideoClick = { fileId, title ->
                            viewModel.preparePlaylist()
                            onVideoClick(fileId, title)
                        },
                        onDownloadClick = { file -> viewModel.downloadVideo(file) },
                        onLoadMore = { viewModel.loadNextPage() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopAppBar(
    folderName: String,
    isSearchActive: Boolean,
    searchQuery: String,
    currentSortOption: FileSortOption,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOptionChange: (FileSortOption) -> Unit,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit
) {
    var isSortMenuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_drive),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = {
                if (isSearchActive) {
                    onSearchToggle(false)
                } else {
                    onNavigateBack()
                }
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimary
                )
            }
        },
        actions = {
            if (isSearchActive) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear_search),
                            tint = TextSecondary
                        )
                    }
                }
            } else {
                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search_drive),
                        tint = TextPrimary
                    )
                }
                Box {
                    IconButton(onClick = { isSortMenuOpen = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.action_sort),
                            tint = TextPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = isSortMenuOpen,
                        onDismissRequest = { isSortMenuOpen = false },
                        modifier = Modifier.background(DarkElevated)
                    ) {
                        val sortItems = listOf(
                            FileSortOption.NAME_ASC to stringResource(R.string.sort_name_asc),
                            FileSortOption.NAME_DESC to stringResource(R.string.sort_name_desc),
                            FileSortOption.DATE_DESC to stringResource(R.string.sort_date_desc),
                            FileSortOption.DATE_ASC to stringResource(R.string.sort_date_asc),
                            FileSortOption.SIZE_DESC to stringResource(R.string.sort_size_desc),
                            FileSortOption.SIZE_ASC to stringResource(R.string.sort_size_asc)
                        )
                        sortItems.forEach { (option, label) ->
                            val isSelected = option == currentSortOption
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) AccentBlue else TextPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AccentBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    onSortOptionChange(option)
                                    isSortMenuOpen = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = TextPrimary
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = PureBlack)
    )
}

@Composable
private fun BrowserFileList(
    uiState: DriveUiState,
    onFolderClick: (String, String) -> Unit,
    onVideoClick: (String, String) -> Unit,
    onDownloadClick: (DriveFile) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= (totalItems - PAGINATION_THRESHOLD)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.nextPageToken != null) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = uiState.files,
            key = { it.id }
        ) { file ->
            if (file.isFolder) {
                FolderCard(
                    folder = file,
                    onClick = { onFolderClick(file.id, file.name) }
                )
            } else {
                VideoCard(
                    video = file,
                    onClick = { onVideoClick(file.id, file.name) },
                    onDownloadClick = { onDownloadClick(file) }
                )
            }
        }

        if (uiState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SPINNER_SIZE.dp),
                        color = AccentBlue,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserEmptyView(
    isSearching: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearching) Icons.Default.SearchOff else Icons.Default.FolderOpen,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(ICON_LARGE_SIZE.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearching) {
                stringResource(R.string.search_no_results)
            } else {
                stringResource(R.string.empty_folder)
            },
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(containerColor = DarkElevated),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.action_refresh),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun BrowserErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            tint = AccentRed,
            modifier = Modifier.size(ICON_LARGE_SIZE.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp)
        ) {
            Text(
                text = stringResource(R.string.action_retry),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
