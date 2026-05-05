package moe.ouom.neriplayer.ui.screen.tab

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.ui.screen.tab/LibraryScreen
 * Created: 2025/8/8
 */

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.auth.common.SavedCookieAuthState
import moe.ouom.neriplayer.data.auth.youtube.YouTubeAuthState
import moe.ouom.neriplayer.data.playlist.favorite.FavoritePlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.system.FavoritesPlaylist
import moe.ouom.neriplayer.data.local.playlist.system.LocalFilesPlaylist
import moe.ouom.neriplayer.data.local.playlist.model.LocalPlaylist
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.data.local.playlist.system.SystemLocalPlaylists
import moe.ouom.neriplayer.data.model.displayCoverUrl
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight
import moe.ouom.neriplayer.ui.screen.tab.settings.auth.SettingsBiliAuthDialogs
import moe.ouom.neriplayer.ui.screen.tab.settings.auth.SettingsNeteaseAuthDialogs
import moe.ouom.neriplayer.ui.screen.tab.settings.auth.SettingsYouTubeAuthDialogs
import moe.ouom.neriplayer.ui.screen.tab.settings.state.formatSyncTime
import moe.ouom.neriplayer.ui.viewmodel.auth.BiliAuthEvent
import moe.ouom.neriplayer.ui.viewmodel.auth.BiliAuthViewModel
import moe.ouom.neriplayer.ui.viewmodel.auth.YouTubeAuthEvent
import moe.ouom.neriplayer.ui.viewmodel.auth.YouTubeAuthViewModel
import moe.ouom.neriplayer.ui.viewmodel.debug.NeteaseAuthEvent
import moe.ouom.neriplayer.ui.viewmodel.debug.NeteaseAuthViewModel
import moe.ouom.neriplayer.ui.viewmodel.tab.AlbumSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.LibraryViewModel
import moe.ouom.neriplayer.ui.viewmodel.tab.PlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeMusicPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.favoriteId
import moe.ouom.neriplayer.util.HapticIconButton
import moe.ouom.neriplayer.util.HapticTextButton
import moe.ouom.neriplayer.util.formatPlayCount
import moe.ouom.neriplayer.util.offlineCachedImageRequest
import org.burnoutcrew.reorderable.ItemPosition
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

enum class LibraryTab(val labelResId: Int) {
    LOCAL(R.string.library_tab_local),
    FAVORITE(R.string.library_tab_favorite),
    YTMUSIC(R.string.library_tab_youtube_music),
    NETEASE(R.string.platform_netease_short),
    NETEASEALBUM(R.string.library_tab_netease_album),
    BILI(R.string.library_tab_bilibili),
    QQMUSIC(R.string.library_tab_qqmusic)
}

private enum class LibraryAuthPlatform {
    NETEASE,
    YOUTUBE,
    BILI,
    QQMUSIC
}

private enum class PlatformConnectionState {
    Connected,
    NeedsRefresh,
    Missing,
    ComingSoon
}

private data class PlatformAuthUiState(
    val platform: LibraryAuthPlatform,
    val title: String,
    val iconResId: Int,
    val connectionState: PlatformConnectionState,
    val statusText: String,
    val emptyHint: String,
    val actionLabel: String
)

private fun libraryTabDisplayOrder(isInternational: Boolean): List<LibraryTab> {
    return if (isInternational) {
        listOf(
            LibraryTab.LOCAL,
            LibraryTab.FAVORITE,
            LibraryTab.YTMUSIC,
            LibraryTab.NETEASE,
            LibraryTab.BILI
        )
    } else {
        listOf(
            LibraryTab.LOCAL,
            LibraryTab.FAVORITE,
            LibraryTab.NETEASE,
            LibraryTab.YTMUSIC,
            LibraryTab.BILI
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    initialTab: LibraryTab = LibraryTab.LOCAL,
    onTabChange: (LibraryTab) -> Unit = {},
    localListState: LazyListState,
    favoriteListState: LazyListState,
    neteaseListState: LazyListState,
    youtubeMusicListState: LazyListState,
    biliListState: LazyListState,
    qqMusicListState: LazyListState,
    onLocalPlaylistClick: (LocalPlaylist) -> Unit = {},
    onNeteasePlaylistClick: (PlaylistSummary) -> Unit = {},
    onNeteaseAlbumClick: (AlbumSummary) -> Unit = {},
    onYouTubeMusicPlaylistClick: (YouTubeMusicPlaylist) -> Unit = {},
    onBiliPlaylistClick: (BiliPlaylist) -> Unit = {}
) {
    val vm: LibraryViewModel = viewModel()
    val neteaseVm: NeteaseAuthViewModel = viewModel()
    val biliVm: BiliAuthViewModel = viewModel()
    val youtubeVm: YouTubeAuthViewModel = viewModel()
    val ui by vm.uiState.collectAsState()
    val neteaseAuthUiState by neteaseVm.uiState.collectAsState()
    val biliAuthUiState by biliVm.uiState.collectAsState()
    val youtubeAuthUiState by youtubeVm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val defaultPlaylistName = stringResource(R.string.library_create_playlist_default)
    val isInternational by AppContainer.settingsRepo.internationalizationEnabledFlow
        .collectAsState(initial = false)
    val orderedTabs = remember(isInternational) { libraryTabDisplayOrder(isInternational) }
    val initialPage = remember(orderedTabs, initialTab) {
        orderedTabs.indexOf(initialTab).takeIf { it >= 0 } ?: 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { orderedTabs.size }
    )
    val scope = rememberCoroutineScope()
    var showPlatformAccountPage by rememberSaveable { mutableStateOf(false) }
    var inlineMsg by remember { mutableStateOf<String?>(null) }
    var showNeteaseSheet by remember { mutableStateOf(false) }
    var showNeteaseSavedCookieDialog by remember { mutableStateOf(false) }
    var showNeteaseCookieDialog by remember { mutableStateOf(false) }
    var showNeteaseConfirmDialog by remember { mutableStateOf(false) }
    var neteaseSheetInitialTab by rememberSaveable { mutableStateOf(0) }
    var neteaseCookieText by remember { mutableStateOf("") }
    var confirmPhoneMasked by remember { mutableStateOf<String?>(null) }
    var showBiliSheet by remember { mutableStateOf(false) }
    var showBiliSavedCookieDialog by remember { mutableStateOf(false) }
    var showBiliCookieDialog by remember { mutableStateOf(false) }
    var biliSheetInitialTab by rememberSaveable { mutableStateOf(0) }
    var biliCookieText by remember { mutableStateOf("") }
    var showYouTubeSheet by remember { mutableStateOf(false) }
    var showYouTubeSavedCookieDialog by remember { mutableStateOf(false) }
    var showYouTubeCookieDialog by remember { mutableStateOf(false) }
    var youtubeSheetInitialTab by rememberSaveable { mutableStateOf(0) }
    var youtubeCookieText by remember { mutableStateOf("") }

    fun openNeteaseAuth(tab: Int = 0) {
        inlineMsg = null
        if (neteaseAuthUiState.hasSavedCookies) {
            showNeteaseSavedCookieDialog = true
        } else {
            neteaseSheetInitialTab = tab
            showNeteaseSheet = true
        }
    }

    fun openBiliAuth(tab: Int = 0) {
        inlineMsg = null
        if (biliAuthUiState.hasSavedCookies) {
            showBiliSavedCookieDialog = true
        } else {
            biliSheetInitialTab = tab
            showBiliSheet = true
        }
    }

    fun openYouTubeAuth(tab: Int = 0) {
        inlineMsg = null
        if (youtubeAuthUiState.hasSavedAuth) {
            showYouTubeSavedCookieDialog = true
        } else {
            youtubeSheetInitialTab = tab
            showYouTubeSheet = true
        }
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(initialTab, orderedTabs) {
        val targetPage = orderedTabs.indexOf(initialTab).takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage, orderedTabs, initialTab) {
        val currentTab = orderedTabs.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (currentTab != initialTab) {
            onTabChange(currentTab)
        }
    }

    LaunchedEffect(Unit) {
        vm.refreshYouTubeMusicPlaylists()
    }

    LaunchedEffect(neteaseVm, biliVm, youtubeVm) {
        neteaseVm.refreshAuthHealth()
        biliVm.refreshAuthHealth()
        youtubeVm.refreshAuthHealth()
    }

    LaunchedEffect(neteaseVm) {
        neteaseVm.events.collect { event ->
            when (event) {
                is NeteaseAuthEvent.ShowSnack -> {
                    inlineMsg = event.message
                    showToast(event.message)
                }
                is NeteaseAuthEvent.AskConfirmSend -> {
                    confirmPhoneMasked = event.masked
                    showNeteaseConfirmDialog = true
                }
                is NeteaseAuthEvent.ShowCookies -> {
                    neteaseCookieText = event.cookies.entries.joinToString("\n") { (key, value) ->
                        "$key=${maskCookieValue(value)}"
                    }
                    showNeteaseCookieDialog = true
                }
                NeteaseAuthEvent.LoginSuccess -> {
                    showNeteaseSavedCookieDialog = false
                    showNeteaseSheet = false
                    showToast(context.getString(R.string.settings_netease_login_success))
                    neteaseVm.refreshAuthHealth()
                    vm.refreshNeteasePlaylists()
                    vm.refreshNeteaseAlbums()
                }
            }
        }
    }

    LaunchedEffect(biliVm) {
        biliVm.events.collect { event ->
            when (event) {
                is BiliAuthEvent.ShowSnack -> {
                    inlineMsg = event.message
                    showToast(event.message)
                }
                is BiliAuthEvent.ShowCookies -> {
                    biliCookieText = event.cookies.entries.joinToString("\n") { (key, value) ->
                        "$key=${maskCookieValue(value)}"
                    }
                    showBiliCookieDialog = true
                }
                BiliAuthEvent.LoginSuccess -> {
                    showBiliSavedCookieDialog = false
                    showBiliSheet = false
                    showToast(context.getString(R.string.settings_bili_login_success))
                    biliVm.refreshAuthHealth()
                }
            }
        }
    }

    LaunchedEffect(youtubeVm) {
        youtubeVm.events.collect { event ->
            when (event) {
                is YouTubeAuthEvent.ShowSnack -> {
                    inlineMsg = event.message
                    showToast(event.message)
                }
                is YouTubeAuthEvent.ShowCookies -> {
                    youtubeCookieText = event.cookies.entries.joinToString("\n") { (key, value) ->
                        "$key=${maskCookieValue(value)}"
                    }
                    showYouTubeCookieDialog = true
                }
                YouTubeAuthEvent.LoginSuccess -> {
                    showYouTubeSavedCookieDialog = false
                    showYouTubeSheet = false
                    showToast(context.getString(R.string.settings_youtube_login_success))
                    youtubeVm.refreshAuthHealth()
                    vm.refreshYouTubeMusicPlaylists()
                }
            }
        }
    }

    val neteasePlaylistAuth = savedCookiePlatformAuthUiState(
        platform = LibraryAuthPlatform.NETEASE,
        title = stringResource(R.string.platform_netease),
        iconResId = R.drawable.ic_netease_cloud_music,
        healthState = neteaseAuthUiState.health.state,
        savedAt = neteaseAuthUiState.health.savedAt,
        hasSavedCookies = neteaseAuthUiState.hasSavedCookies,
        validStatusResId = R.string.settings_netease_status_valid,
        savedInvalidResId = R.string.settings_netease_status_saved_invalid,
        missingResId = R.string.settings_netease_status_missing,
        emptyHint = stringResource(R.string.library_platform_empty_netease_playlist)
    )
    val biliAuth = savedCookiePlatformAuthUiState(
        platform = LibraryAuthPlatform.BILI,
        title = stringResource(R.string.platform_bilibili),
        iconResId = R.drawable.ic_bilibili,
        healthState = biliAuthUiState.health.state,
        savedAt = biliAuthUiState.health.savedAt,
        hasSavedCookies = biliAuthUiState.hasSavedCookies,
        validStatusResId = R.string.settings_bili_status_valid,
        savedInvalidResId = R.string.settings_bili_status_saved_invalid,
        missingResId = R.string.settings_bili_status_missing,
        emptyHint = stringResource(R.string.library_platform_empty_bilibili)
    )
    val youtubeAuth = youtubePlatformAuthUiState(
        title = stringResource(R.string.common_youtube),
        healthState = youtubeAuthUiState.health.state,
        savedAt = youtubeAuthUiState.health.savedAt,
        hasSavedAuth = youtubeAuthUiState.hasSavedAuth
    )
    val platformAuthStates = listOf(neteasePlaylistAuth, youtubeAuth, biliAuth)
    val hasConnectedPlatform = platformAuthStates.any {
        it.connectionState == PlatformConnectionState.Connected
    }
    val hasAuthNeedingAttention = platformAuthStates.any {
        it.connectionState == PlatformConnectionState.NeedsRefresh
    }
    val accountIconTint = when {
        hasAuthNeedingAttention -> MaterialTheme.colorScheme.error
        hasConnectedPlatform -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    fun handlePlatformAuthAction(platform: LibraryAuthPlatform) {
        when (platform) {
            LibraryAuthPlatform.NETEASE -> openNeteaseAuth()
            LibraryAuthPlatform.YOUTUBE -> openYouTubeAuth()
            LibraryAuthPlatform.BILI -> openBiliAuth()
            LibraryAuthPlatform.QQMUSIC -> showToast(context.getString(R.string.library_platform_qqmusic_auth_hint))
        }
    }

    BackHandler(enabled = showPlatformAccountPage) {
        showPlatformAccountPage = false
    }

    if (showPlatformAccountPage) {
        PlatformAccountPage(
            platforms = platformAuthStates,
            onNavigateUp = { showPlatformAccountPage = false },
            onPlatformAction = ::handlePlatformAuthAction
        )
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    HapticIconButton(onClick = { showPlatformAccountPage = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.library_platform_accounts),
                            tint = accountIconTint
                        )
                    }
                }
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 0.dp, vertical = 12.dp)
                    .fillMaxSize()
            ) {
                Column(Modifier.fillMaxSize()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 8.dp,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        orderedTabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                text = { Text(stringResource(tab.labelResId)) }
                            )
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp
                    ) { page ->
                        when (orderedTabs[page]) {
                            LibraryTab.LOCAL -> LocalPlaylistList(
                                playlists = ui.localPlaylists,
                                listState = localListState,
                                onCreate = { name ->
                                    val finalName = name.trim().ifBlank { defaultPlaylistName }
                                    vm.createLocalPlaylist(finalName)
                                },
                                onClick = onLocalPlaylistClick,
                                onRename = { playlistId, newName ->
                                    vm.renameLocalPlaylist(playlistId, newName)
                                },
                                onDelete = { playlistId ->
                                    vm.deleteLocalPlaylist(playlistId)
                                },
                                onReorder = { order ->
                                    vm.reorderLocalPlaylists(order)
                                }
                            )

                            LibraryTab.FAVORITE -> FavoritePlaylistList(
                                listState = favoriteListState,
                                onNeteasePlaylistClick = onNeteasePlaylistClick,
                                onNeteaseAlbumClick = onNeteaseAlbumClick,
                                onBiliPlaylistClick = onBiliPlaylistClick,
                                onYouTubeMusicPlaylistClick = onYouTubeMusicPlaylistClick
                            )

                            LibraryTab.NETEASE,
                            LibraryTab.NETEASEALBUM -> NeteaseLibraryList(
                                playlists = ui.neteasePlaylists,
                                albums = ui.neteaseAlbums,
                                listState = neteaseListState,
                                authUiState = neteasePlaylistAuth,
                                onPlaylistClick = onNeteasePlaylistClick,
                                onAlbumClick = onNeteaseAlbumClick,
                                onAuthAction = { openNeteaseAuth() }
                            )

                            LibraryTab.YTMUSIC -> YouTubeMusicPlaylistList(
                                playlists = ui.youtubeMusicPlaylists,
                                error = ui.youtubeMusicError,
                                listState = youtubeMusicListState,
                                authUiState = youtubeAuth,
                                onClick = onYouTubeMusicPlaylistClick,
                                onAuthAction = { openYouTubeAuth() },
                                onRetry = { vm.refreshYouTubeMusicPlaylists() }
                            )

                            LibraryTab.BILI -> BiliPlaylistList(
                                playlists = ui.biliPlaylists,
                                listState = biliListState,
                                authUiState = biliAuth,
                                onClick = onBiliPlaylistClick,
                                onAuthAction = { openBiliAuth() }
                            )

                            LibraryTab.QQMUSIC -> QqMusicPlaylistList(
                                listState = qqMusicListState
                            )
                        }
                    }
                }
            }
        }
    }

    SettingsNeteaseAuthDialogs(
        showSheet = showNeteaseSheet,
        initialTab = neteaseSheetInitialTab,
        onDismissSheet = { showNeteaseSheet = false },
        inlineMsg = inlineMsg,
        onInlineMsgChange = { inlineMsg = it },
        showConfirmDialog = showNeteaseConfirmDialog,
        confirmPhoneMasked = confirmPhoneMasked,
        onDismissConfirmDialog = { showNeteaseConfirmDialog = false },
        vm = neteaseVm,
        showCookieDialog = showNeteaseCookieDialog,
        cookieText = neteaseCookieText,
        onDismissCookieDialog = { showNeteaseCookieDialog = false },
        showSavedCookieDialog = showNeteaseSavedCookieDialog,
        onDismissSavedCookieDialog = { showNeteaseSavedCookieDialog = false },
        onOpenSheetAtTab = { tab ->
            inlineMsg = null
            neteaseSheetInitialTab = tab
            showNeteaseSheet = true
        },
        onLogout = {
            showNeteaseSavedCookieDialog = false
            neteaseVm.clearCookies()
        }
    )

    SettingsBiliAuthDialogs(
        showSheet = showBiliSheet,
        initialTab = biliSheetInitialTab,
        onDismissSheet = { showBiliSheet = false },
        inlineMsg = inlineMsg,
        onInlineMsgChange = { inlineMsg = it },
        vm = biliVm,
        showCookieDialog = showBiliCookieDialog,
        cookieText = biliCookieText,
        onDismissCookieDialog = { showBiliCookieDialog = false },
        showSavedCookieDialog = showBiliSavedCookieDialog,
        onDismissSavedCookieDialog = { showBiliSavedCookieDialog = false },
        onOpenSheetAtTab = { tab ->
            inlineMsg = null
            biliSheetInitialTab = tab
            showBiliSheet = true
        },
        onLogout = {
            showBiliSavedCookieDialog = false
            biliVm.clearCookies()
        }
    )

    SettingsYouTubeAuthDialogs(
        showSheet = showYouTubeSheet,
        initialTab = youtubeSheetInitialTab,
        onDismissSheet = { showYouTubeSheet = false },
        inlineMsg = inlineMsg,
        onInlineMsgChange = { inlineMsg = it },
        vm = youtubeVm,
        showCookieDialog = showYouTubeCookieDialog,
        cookieText = youtubeCookieText,
        onDismissCookieDialog = { showYouTubeCookieDialog = false },
        showSavedCookieDialog = showYouTubeSavedCookieDialog,
        onDismissSavedCookieDialog = { showYouTubeSavedCookieDialog = false },
        onOpenSheetAtTab = { tab ->
            inlineMsg = null
            youtubeSheetInitialTab = tab
            showYouTubeSheet = true
        },
        onLogout = {
            showYouTubeSavedCookieDialog = false
            youtubeVm.clearAuth()
        }
    )
}

@Composable
private fun savedCookiePlatformAuthUiState(
    platform: LibraryAuthPlatform,
    title: String,
    iconResId: Int,
    healthState: SavedCookieAuthState,
    savedAt: Long,
    hasSavedCookies: Boolean,
    validStatusResId: Int,
    savedInvalidResId: Int,
    missingResId: Int,
    emptyHint: String
): PlatformAuthUiState {
    val connectionState = when {
        healthState == SavedCookieAuthState.Valid -> PlatformConnectionState.Connected
        hasSavedCookies -> PlatformConnectionState.NeedsRefresh
        else -> PlatformConnectionState.Missing
    }
    val statusText = when (connectionState) {
        PlatformConnectionState.Connected -> {
            val relativeTime = savedAt
                .takeIf { it > 0L }
                ?.let { formatSyncTime(it) }
                ?: stringResource(R.string.time_just_now)
            stringResource(validStatusResId, relativeTime)
        }
        PlatformConnectionState.NeedsRefresh -> stringResource(savedInvalidResId)
        PlatformConnectionState.Missing -> stringResource(missingResId)
        PlatformConnectionState.ComingSoon -> stringResource(R.string.common_coming_soon)
    }
    return PlatformAuthUiState(
        platform = platform,
        title = title,
        iconResId = iconResId,
        connectionState = connectionState,
        statusText = statusText,
        emptyHint = emptyHint,
        actionLabel = platformActionLabel(connectionState)
    )
}

@Composable
private fun youtubePlatformAuthUiState(
    title: String,
    healthState: YouTubeAuthState,
    savedAt: Long,
    hasSavedAuth: Boolean
): PlatformAuthUiState {
    val connectionState = when {
        healthState == YouTubeAuthState.Valid -> PlatformConnectionState.Connected
        hasSavedAuth -> PlatformConnectionState.NeedsRefresh
        else -> PlatformConnectionState.Missing
    }
    val statusText = when (connectionState) {
        PlatformConnectionState.Connected -> {
            val relativeTime = savedAt
                .takeIf { it > 0L }
                ?.let { formatSyncTime(it) }
                ?: stringResource(R.string.time_just_now)
            stringResource(R.string.settings_youtube_status_valid, relativeTime)
        }
        PlatformConnectionState.NeedsRefresh -> stringResource(R.string.settings_youtube_status_saved_invalid)
        PlatformConnectionState.Missing -> stringResource(R.string.settings_youtube_status_missing)
        PlatformConnectionState.ComingSoon -> stringResource(R.string.common_coming_soon)
    }
    return PlatformAuthUiState(
        platform = LibraryAuthPlatform.YOUTUBE,
        title = title,
        iconResId = R.drawable.ic_youtube,
        connectionState = connectionState,
        statusText = statusText,
        emptyHint = stringResource(R.string.library_platform_empty_youtube),
        actionLabel = platformActionLabel(connectionState)
    )
}

@Composable
private fun platformActionLabel(connectionState: PlatformConnectionState): String {
    return when (connectionState) {
        PlatformConnectionState.Connected -> stringResource(R.string.library_platform_manage)
        PlatformConnectionState.NeedsRefresh -> stringResource(R.string.library_platform_refresh_login)
        PlatformConnectionState.Missing -> stringResource(R.string.library_platform_login)
        PlatformConnectionState.ComingSoon -> stringResource(R.string.common_coming_soon)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlatformAccountPage(
    platforms: List<PlatformAuthUiState>,
    onNavigateUp: () -> Unit,
    onPlatformAction: (LibraryAuthPlatform) -> Unit
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.library_platform_accounts)) },
            navigationIcon = {
                HapticIconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 24.dp + miniPlayerHeight
            )
        ) {
            item {
                Text(
                    text = stringResource(R.string.library_platform_accounts_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                )
            }

            items(
                items = platforms,
                key = { platform -> platform.platform.name }
            ) { platform ->
                PlatformAccountRow(
                    authUiState = platform,
                    onAction = { onPlatformAction(platform.platform) }
                )
            }
        }
    }
}

@Composable
private fun PlatformAccountRow(
    authUiState: PlatformAuthUiState,
    onAction: () -> Unit
) {
    val actionEnabled = authUiState.connectionState != PlatformConnectionState.ComingSoon
    ListItem(
        leadingContent = {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = authUiState.iconResId),
                contentDescription = authUiState.title,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        headlineContent = {
            Text(
                text = authUiState.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = authUiState.statusText,
                color = if (authUiState.connectionState == PlatformConnectionState.NeedsRefresh) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        trailingContent = {
            HapticTextButton(
                enabled = actionEnabled,
                onClick = onAction
            ) {
                Text(authUiState.actionLabel)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun PlatformAuthEmptyState(
    authUiState: PlatformAuthUiState,
    onAction: () -> Unit
) {
    val cardShape = RoundedCornerShape(12.dp)
    val actionEnabled = authUiState.connectionState != PlatformConnectionState.ComingSoon
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(cardShape)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(R.string.library_platform_connect_platform, authUiState.title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = authUiState.emptyHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = authUiState.statusText,
                        color = if (authUiState.connectionState == PlatformConnectionState.NeedsRefresh) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    HapticTextButton(
                        enabled = actionEnabled,
                        onClick = onAction
                    ) {
                        Text(authUiState.actionLabel)
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = authUiState.iconResId),
                    contentDescription = authUiState.title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp)
                )
            }
        )
    }
}

private fun maskCookieValue(value: String): String {
    return when {
        value.isBlank() -> ""
        value.length <= 8 -> "*".repeat(value.length)
        else -> value.take(4) + "..." + value.takeLast(4)
    }
}

@Composable
private fun YouTubeMusicPlaylistList(
    playlists: List<YouTubeMusicPlaylist>,
    error: String?,
    listState: LazyListState,
    authUiState: PlatformAuthUiState,
    onClick: (YouTubeMusicPlaylist) -> Unit,
    onAuthAction: () -> Unit,
    onRetry: () -> Unit
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val favoriteRepo = remember(context) { FavoritePlaylistRepository.getInstance(context) }
    val favorites by favoriteRepo.favorites.collectAsState()
    val scope = rememberCoroutineScope()
    var menuPlaylist by remember { mutableStateOf<YouTubeMusicPlaylist?>(null) }

    fun copyToClipboard(label: String, text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 8.dp + miniPlayerHeight
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        if (playlists.isEmpty()) {
            item {
                if (authUiState.connectionState != PlatformConnectionState.Connected) {
                    PlatformAuthEmptyState(
                        authUiState = authUiState,
                        onAction = onAuthAction
                    )
                } else {
                    Card(
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(cardShape)
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = error ?: stringResource(R.string.library_youtube_music_empty),
                                    color = if (error != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color.Unspecified
                                    }
                                )
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = stringResource(R.string.library_youtube_music_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (error != null) {
                                        HapticTextButton(onClick = onRetry) {
                                            Text(text = stringResource(R.string.action_retry))
                                        }
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_youtube),
                                    contentDescription = stringResource(R.string.common_youtube),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
        items(
            items = playlists,
            key = { it.browseId }
        ) { playlist ->
            val playlistFavoriteId = remember(playlist.playlistId, playlist.browseId) {
                playlist.favoriteId()
            }
            val isFavorite = remember(favorites, playlistFavoriteId) {
                favoriteRepo.isFavorite(playlistFavoriteId, "youtubeMusic")
            }
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .combinedClickable(
                        onClick = { onClick(playlist) },
                        onLongClick = { menuPlaylist = playlist }
                    )
            ) {
                ListItem(
                    headlineContent = { Text(playlist.title) },
                    supportingContent = {
                        val trackCountText = playlist.trackCount
                            .takeIf { it > 0 }
                            ?.let { count ->
                                pluralStringResource(
                                    R.plurals.library_song_count,
                                    count,
                                    count
                                )
                            }
                        val subtitleText = playlist.subtitle.ifBlank {
                            stringResource(R.string.library_youtube_music_hint)
                        }
                        Text(
                            text = listOfNotNull(subtitleText, trackCountText)
                                .distinct()
                                .joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        if (playlist.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(playlist.coverUrl)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                )

                DropdownMenu(
                    expanded = menuPlaylist?.browseId == playlist.browseId,
                    onDismissRequest = { menuPlaylist = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_youtube_music_open_playlist)) },
                        onClick = {
                            menuPlaylist = null
                            onClick(playlist)
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isFavorite) {
                                    stringResource(R.string.home_unfavorite_playlist)
                                } else {
                                    stringResource(R.string.home_favorite_playlist)
                                }
                            )
                        },
                        onClick = {
                            menuPlaylist = null
                            val toastMessage = if (isFavorite) {
                                context.getString(R.string.home_unfavorited)
                            } else {
                                context.getString(R.string.favorite_success)
                            }
                            scope.launch {
                                if (isFavorite) {
                                    favoriteRepo.removeFavorite(playlistFavoriteId, "youtubeMusic")
                                } else {
                                    favoriteRepo.addFavorite(
                                        id = playlistFavoriteId,
                                        name = playlist.title,
                                        coverUrl = playlist.coverUrl,
                                        trackCount = playlist.trackCount,
                                        source = "youtubeMusic",
                                        browseId = playlist.browseId,
                                        playlistId = playlist.playlistId,
                                        subtitle = playlist.subtitle,
                                        songs = emptyList()
                                    )
                                }
                                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_youtube_music_copy_browse_id)) },
                        onClick = {
                            copyToClipboard("ytmusic_browse_id", playlist.browseId)
                            menuPlaylist = null
                        }
                    )
                    if (playlist.playlistId.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.library_youtube_music_copy_playlist_id)) },
                            onClick = {
                                copyToClipboard("ytmusic_playlist_id", playlist.playlistId)
                                menuPlaylist = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiliPlaylistList(
    playlists: List<BiliPlaylist>,
    listState: LazyListState,
    authUiState: PlatformAuthUiState,
    onClick: (BiliPlaylist) -> Unit,
    onAuthAction: () -> Unit
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        if (playlists.isEmpty() && authUiState.connectionState != PlatformConnectionState.Connected) {
            item {
                PlatformAuthEmptyState(
                    authUiState = authUiState,
                    onAction = onAuthAction
                )
            }
        }
        items(
            items = playlists,
            key = { it.mediaId }
        ) { pl ->
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .clickable { onClick(pl) }
            ) {
                ListItem(
                    headlineContent = { Text(pl.title) },
                    supportingContent = {
                        Text(
                            pluralStringResource(R.plurals.library_video_count, pl.count, pl.count),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        if (pl.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(pl.coverUrl).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LocalPlaylistList(
    playlists: List<LocalPlaylist>,
    listState: LazyListState,
    onCreate: (String) -> Unit,
    onClick: (LocalPlaylist) -> Unit,
    onRename: (Long, String) -> Unit = { _, _ -> },
    onDelete: (Long) -> Unit = {},
    onReorder: (List<Long>) -> Unit = {}
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteSelectedConfirm by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val defaultPlaylistName = context.getString(R.string.library_create_playlist_default)
    val maxNameLength = LocalPlaylistRepository.MAX_PLAYLIST_NAME_LENGTH
    val autoShowKeyboard by AppContainer.settingsRepo.autoShowKeyboardFlow.collectAsState(initial = false)
    val reorderablePlaylists = remember { mutableStateListOf<LocalPlaylist>() }

    LaunchedEffect(showDialog) {
        if (showDialog && autoShowKeyboard) focusRequester.requestFocus()
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
        showDeleteSelectedConfirm = false
    }

    fun toggleSelection(playlistId: Long) {
        selectedIds =
            if (selectedIds.contains(playlistId)) selectedIds - playlistId else selectedIds + playlistId
    }

    fun deleteSelected() {
        if (selectedIds.isEmpty()) return
        showDeleteSelectedConfirm = true
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    LaunchedEffect(playlists) {
        val filtered = playlists.filterNot { SystemLocalPlaylists.isSystemPlaylist(it, context) }
        reorderablePlaylists.clear()
        reorderablePlaylists.addAll(filtered)
        val validIds = filtered.map { it.id }.toSet()
        selectedIds = selectedIds.intersect(validIds)
        if (selectionMode && reorderablePlaylists.isEmpty()) {
            exitSelection()
        }
    }

    fun tryCreate(): Boolean {
        val trimmedInput = newName.trim().take(maxNameLength)
        val finalName = trimmedInput.ifBlank { defaultPlaylistName }.take(maxNameLength)

        val favoritesName = context.getString(R.string.favorite_my_music)
        val localFilesName = context.getString(R.string.local_files)
        if (FavoritesPlaylist.matches(finalName, context)) {
            nameError = context.getString(R.string.library_name_reserved, favoritesName)
            return false
        }
        if (LocalFilesPlaylist.matches(finalName, context)) {
            nameError = context.getString(R.string.library_name_reserved, localFilesName)
            return false
        }
        if (playlists.any { it.name.equals(finalName, ignoreCase = true) }) {
            nameError = context.getString(R.string.library_name_exists)
            return false
        }

        onCreate(finalName)
        showDialog = false
        newName = ""
        nameError = null
        return true
    }

    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val favoritesPlaylist = playlists.firstOrNull { FavoritesPlaylist.isSystemPlaylist(it, context) }
    val localFilesPlaylist = playlists.firstOrNull { LocalFilesPlaylist.isSystemPlaylist(it, context) }
    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from: ItemPosition, to: ItemPosition ->
            if (!selectionMode) return@rememberReorderableLazyListState
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState
            val fromIdx = reorderablePlaylists.indexOfFirst { it.id == fromId }
            val toIdx = reorderablePlaylists.indexOfFirst { it.id == toId }
            if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                reorderablePlaylists.add(toIdx, reorderablePlaylists.removeAt(fromIdx))
            }
        },
        canDragOver = { _, over ->
            selectionMode && over.key is Long
        },
        onDragEnd = { _, _ ->
            if (selectionMode) {
                onReorder(reorderablePlaylists.map { it.id })
            }
        }
    )

    LazyColumn(
        state = reorderState.listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .reorderable(reorderState)
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        if (selectionMode) {
            item(key = "local_playlist_selection_header") {
                val allSelected = selectedIds.size == reorderablePlaylists.size && reorderablePlaylists.isNotEmpty()
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.common_selected_count,
                                    selectedIds.size,
                                    selectedIds.size
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            HapticIconButton(onClick = { exitSelection() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_exit_multi_select)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HapticTextButton(
                                    onClick = {
                                        selectedIds = if (allSelected) {
                                            emptySet()
                                        } else {
                                            reorderablePlaylists.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Text(
                                        if (allSelected) {
                                            stringResource(R.string.action_deselect_all)
                                        } else {
                                            stringResource(R.string.action_select_all)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                HapticTextButton(
                                    enabled = selectedIds.isNotEmpty(),
                                    onClick = { deleteSelected() }
                                ) {
                                    Text(stringResource(R.string.common_delete_selected))
                                }
                            }
                        }
                    )
                }
            }
        }
        item {
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .animateItem()
                    .clip(cardShape)
                    .clickable(enabled = !selectionMode) { showDialog = true }
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_create_new)) },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showDialog = false
                        newName = ""
                        nameError = null
                    },
                    title = { Text(stringResource(R.string.playlist_create)) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = {
                                    newName = it.take(maxNameLength)
                                    if (nameError != null) nameError = null
                                },
                                placeholder = { Text(stringResource(R.string.playlist_enter_name)) },
                                singleLine = true,
                                isError = nameError != null,
                                supportingText = {
                                    val err = nameError
                                    if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { tryCreate() }
                                )
                            )
                        }
                    },
                    confirmButton = {
                        HapticTextButton(
                            onClick = { tryCreate() }
                        ) { Text(stringResource(R.string.action_create)) }
                    },
                    dismissButton = {
                        HapticTextButton(
                            onClick = {
                                showDialog = false
                                newName = ""
                                nameError = null
                            }
                        ) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }

            if (showDeleteSelectedConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteSelectedConfirm = false },
                    title = { Text(stringResource(R.string.dialog_confirm_delete)) },
                    text = {
                        Text(
                            pluralStringResource(
                                R.plurals.library_delete_selected_confirm,
                                selectedIds.size,
                                selectedIds.size
                            )
                        )
                    },
                    confirmButton = {
                        HapticTextButton(
                            onClick = {
                                selectedIds.forEach { onDelete(it) }
                                exitSelection()
                            }
                        ) { Text(stringResource(R.string.action_delete)) }
                    },
                    dismissButton = {
                        HapticTextButton(
                            onClick = { showDeleteSelectedConfirm = false }
                        ) { Text(stringResource(R.string.action_cancel)) }
                    }
                )
            }
        }

        favoritesPlaylist?.let { system ->
            item(key = "local_playlist_favorites") {
                val displayName = SystemLocalPlaylists.resolve(system.id, system.name, context)?.currentName ?: system.name
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                        .combinedClickable(
                            onClick = {
                                if (!selectionMode) onClick(system)
                            }
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.library_song_count, system.songs.size, system.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    Spacer(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val cover = system.displayCoverUrl(context)
                                if (!cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = offlineCachedImageRequest(
                                            context = context,
                                            data = cover,
                                            sizePx = 192,
                                            allowHardware = false
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        items(
            items = reorderablePlaylists,
            key = { it.id }
        ) { pl ->
            ReorderableItem(state = reorderState, key = pl.id) { isDragging ->
                val systemPlaylist = SystemLocalPlaylists.resolve(pl.id, pl.name, context)
                val displayName = systemPlaylist?.currentName ?: pl.name
                val isSystemPlaylist = systemPlaylist != null
                val isSelected = selectionMode && selectedIds.contains(pl.id)
                val rowContainerColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                }

                var showMenu by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var showDeleteDialog by remember { mutableStateOf(false) }
                var renameText by remember { mutableStateOf(pl.name.take(maxNameLength)) }

                if (selectionMode && showMenu) showMenu = false

                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = rowContainerColor
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .animateItem()
                        .clip(cardShape)
                        .combinedClickable(
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(pl.id)
                                } else {
                                    onClick(pl)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode && !isSystemPlaylist) {
                                    selectionMode = true
                                    selectedIds = setOf(pl.id)
                                }
                            }
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.library_song_count, pl.songs.size, pl.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            if (!isSystemPlaylist) toggleSelection(pl.id)
                                        },
                                        enabled = !isSystemPlaylist
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val cover = pl.displayCoverUrl(context)
                                if (!cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = offlineCachedImageRequest(
                                            context = context,
                                            data = cover,
                                            sizePx = 192,
                                            allowHardware = false
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            if (selectionMode && !isSystemPlaylist) {
                                Box(
                                    modifier = Modifier
                                        .detectReorder(reorderState)
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.common_drag_handle),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else if (!selectionMode && !isSystemPlaylist) {
                                Box {
                                    HapticIconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.common_more_options)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            onClick = {
                                                showMenu = false
                                                renameText = pl.name.take(maxNameLength)
                                                showRenameDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_delete)) },
                                            onClick = {
                                                showMenu = false
                                                showDeleteDialog = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                if (showRenameDialog) {
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text(stringResource(R.string.action_rename)) },
                        text = {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it.take(maxNameLength) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            HapticTextButton(
                                onClick = {
                                    val trimmed = renameText.trim().take(maxNameLength)
                                    if (trimmed.isNotBlank()) {
                                        onRename(pl.id, trimmed)
                                        showRenameDialog = false
                                    }
                                }
                            ) { Text(stringResource(R.string.action_confirm)) }
                        },
                        dismissButton = {
                            HapticTextButton(
                                onClick = { showRenameDialog = false }
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text(stringResource(R.string.action_delete)) },
                        text = {
                            Text(stringResource(R.string.library_delete_playlist_confirm, displayName))
                        },
                        confirmButton = {
                            HapticTextButton(
                                onClick = {
                                    onDelete(pl.id)
                                    showDeleteDialog = false
                                }
                            ) { Text(stringResource(R.string.action_delete)) }
                        },
                        dismissButton = {
                            HapticTextButton(
                                onClick = { showDeleteDialog = false }
                            ) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }
            }
        }

        localFilesPlaylist?.let { system ->
            item(key = "local_playlist_local_files") {
                val displayName = SystemLocalPlaylists.resolve(system.id, system.name, context)?.currentName ?: system.name
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                        .combinedClickable(
                            onClick = {
                                if (!selectionMode) onClick(system)
                            }
                        )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.library_song_count, system.songs.size, system.songs.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectionMode) {
                                    Spacer(modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                val cover = system.displayCoverUrl(context)
                                if (!cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = offlineCachedImageRequest(
                                            context = context,
                                            data = cover,
                                            sizePx = 192,
                                            allowHardware = false
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NeteaseLibraryList(
    playlists: List<PlaylistSummary>,
    albums: List<AlbumSummary>,
    listState: LazyListState,
    authUiState: PlatformAuthUiState,
    onPlaylistClick: (PlaylistSummary) -> Unit,
    onAlbumClick: (AlbumSummary) -> Unit,
    onAuthAction: () -> Unit
) {
    val context = LocalContext.current
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val cardShape = RoundedCornerShape(12.dp)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (
            playlists.isEmpty() &&
            albums.isEmpty() &&
            authUiState.connectionState != PlatformConnectionState.Connected
        ) {
            item {
                PlatformAuthEmptyState(
                    authUiState = authUiState,
                    onAction = onAuthAction
                )
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "netease_playlists_header") {
                NeteaseLibrarySectionHeader(
                    title = stringResource(R.string.library_netease_section_playlists)
                )
            }

            items(
                items = playlists,
                key = { "playlist:${it.id}" }
            ) { playlist ->
                NeteasePlaylistRow(
                    playlist = playlist,
                    context = context,
                    cardShape = cardShape,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }

        if (albums.isNotEmpty()) {
            item(key = "netease_albums_header") {
                NeteaseLibrarySectionHeader(
                    title = stringResource(R.string.library_netease_section_albums)
                )
            }

            items(
                items = albums,
                key = { "album:${it.id}" }
            ) { album ->
                NeteaseAlbumRow(
                    album = album,
                    cardShape = cardShape,
                    onClick = { onAlbumClick(album) }
                )
            }
        }
    }
}

@Composable
private fun NeteaseLibrarySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.NeteasePlaylistRow(
    playlist: PlaylistSummary,
    context: Context,
    cardShape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateItem()
            .clip(cardShape)
            .clickable { onClick() }
    ) {
        ListItem(
            headlineContent = { Text(playlist.name) },
            supportingContent = {
                Text(
                    stringResource(
                        R.string.home_play_count_format,
                        formatPlayCount(context, playlist.playCount),
                        playlist.trackCount
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            leadingContent = {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(playlist.picUrl).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.NeteaseAlbumRow(
    album: AlbumSummary,
    cardShape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateItem()
            .clip(cardShape)
            .clickable { onClick() }
    ) {
        ListItem(
            headlineContent = { Text(album.name) },
            supportingContent = {
                Text(
                    pluralStringResource(R.plurals.library_song_count, album.size, album.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            leadingContent = {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(album.picUrl).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        )
    }
}

@Composable
private fun FavoritePlaylistList(
    listState: LazyListState,
    onNeteasePlaylistClick: (PlaylistSummary) -> Unit,
    onNeteaseAlbumClick: (AlbumSummary) -> Unit,
    onBiliPlaylistClick: (BiliPlaylist) -> Unit,
    onYouTubeMusicPlaylistClick: (YouTubeMusicPlaylist) -> Unit
) {
    val context = LocalContext.current
    val favoriteRepo = remember(context) { FavoritePlaylistRepository.getInstance(context) }
    val favorites by favoriteRepo.favorites.collectAsState()
    val miniPlayerHeight = LocalMiniPlayerHeight.current
    val scope = rememberCoroutineScope()
    var sortMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteSelectedConfirm by rememberSaveable { mutableStateOf(false) }
    val reorderableFavorites = remember { mutableStateListOf<moe.ouom.neriplayer.data.playlist.favorite.FavoritePlaylist>() }

    fun favoriteKey(favorite: moe.ouom.neriplayer.data.playlist.favorite.FavoritePlaylist): String {
        return "${favorite.source}:${favorite.id}"
    }

    fun exitEditMode() {
        sortMode = false
        selectedKeys = emptySet()
        showDeleteSelectedConfirm = false
    }

    fun toggleSelection(key: String) {
        selectedKeys = if (selectedKeys.contains(key)) {
            selectedKeys - key
        } else {
            selectedKeys + key
        }
    }

    BackHandler(enabled = sortMode) { exitEditMode() }

    LaunchedEffect(favorites) {
        reorderableFavorites.clear()
        reorderableFavorites.addAll(favorites)
        val validKeys = favorites.map(::favoriteKey).toSet()
        selectedKeys = selectedKeys.intersect(validKeys)
        if (sortMode && favorites.isEmpty()) {
            exitEditMode()
        }
    }

    LaunchedEffect(sortMode) {
        if (sortMode && favorites.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        onMove = { from: ItemPosition, to: ItemPosition ->
            if (!sortMode) return@rememberReorderableLazyListState
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = reorderableFavorites.indexOfFirst { favoriteKey(it) == fromKey }
            val toIndex = reorderableFavorites.indexOfFirst { favoriteKey(it) == toKey }
            if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                reorderableFavorites.add(toIndex, reorderableFavorites.removeAt(fromIndex))
            }
        },
        canDragOver = { _, over -> sortMode && over.key is String },
        onDragEnd = { _, _ ->
            if (sortMode) {
                scope.launch {
                    favoriteRepo.reorderFavorites(
                        reorderableFavorites.map { "${it.source}:${it.id}" }
                    )
                }
            }
        }
    )

    LazyColumn(
        state = reorderState.listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxSize()
            .reorderable(reorderState)
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        if (sortMode) {
            item(key = "favorite_sort_mode_header") {
                val allSelected =
                    selectedKeys.size == reorderableFavorites.size && reorderableFavorites.isNotEmpty()
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                pluralStringResource(
                                    R.plurals.common_selected_count,
                                    selectedKeys.size,
                                    selectedKeys.size
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            HapticIconButton(onClick = { exitEditMode() }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_exit_multi_select)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                HapticTextButton(
                                    onClick = {
                                        selectedKeys = if (allSelected) {
                                            emptySet()
                                        } else {
                                            reorderableFavorites.map(::favoriteKey).toSet()
                                        }
                                    }
                                ) {
                                    Text(
                                        if (allSelected) {
                                            stringResource(R.string.action_deselect_all)
                                        } else {
                                            stringResource(R.string.action_select_all)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                HapticTextButton(
                                    enabled = selectedKeys.isNotEmpty(),
                                    onClick = { showDeleteSelectedConfirm = true }
                                ) {
                                    Text(stringResource(R.string.common_delete_selected))
                                }
                            }
                        }
                    )
                }
            }
        }
        if (favorites.isEmpty()) {
            item {
                Card(
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(cardShape)
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.playlist_no_favorite)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.playlist_favorite_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    )
                }
            }
        } else {
            items(
                items = reorderableFavorites,
                key = { favoriteKey(it) }
            ) { favorite ->
                val itemKey = favoriteKey(favorite)
                val isSelected = sortMode && selectedKeys.contains(itemKey)
                ReorderableItem(state = reorderState, key = itemKey) {
                    Card(
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                            } else if (sortMode) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .animateItem()
                            .clip(cardShape)
                            .combinedClickable(
                                onClick = {
                                    if (sortMode) {
                                        toggleSelection(itemKey)
                                        return@combinedClickable
                                    }
                                    when (favorite.source) {
                                        "netease" -> {
                                            onNeteasePlaylistClick(
                                                PlaylistSummary(
                                                    id = favorite.id,
                                                    name = favorite.name,
                                                    picUrl = favorite.coverUrl ?: "",
                                                    playCount = 0,
                                                    trackCount = favorite.trackCount
                                                )
                                            )
                                        }
                                        "neteaseAlbum" -> {
                                            onNeteaseAlbumClick(
                                                AlbumSummary(
                                                    id = favorite.id,
                                                    name = favorite.name,
                                                    picUrl = favorite.coverUrl.orEmpty(),
                                                    size = favorite.trackCount
                                                )
                                            )
                                        }
                                        "youtubeMusic" -> {
                                            val resolvedBrowseId = favorite.browseId
                                                ?.takeIf { it.isNotBlank() }
                                                ?: favorite.playlistId
                                                    ?.takeIf { it.isNotBlank() }
                                                    ?.let { "VL$it" }
                                            val resolvedPlaylistId = favorite.playlistId
                                                ?.takeIf { it.isNotBlank() }
                                                ?: resolvedBrowseId?.removePrefix("VL")
                                            if (
                                                !resolvedBrowseId.isNullOrBlank() &&
                                                !resolvedPlaylistId.isNullOrBlank()
                                            ) {
                                                onYouTubeMusicPlaylistClick(
                                                    YouTubeMusicPlaylist(
                                                        browseId = resolvedBrowseId,
                                                        playlistId = resolvedPlaylistId,
                                                        title = favorite.name,
                                                        subtitle = favorite.subtitle.orEmpty(),
                                                        coverUrl = favorite.coverUrl.orEmpty(),
                                                        trackCount = favorite.trackCount
                                                    )
                                                )
                                            }
                                        }
                                        "bili" -> {
                                            onBiliPlaylistClick(
                                                BiliPlaylist(
                                                    mediaId = favorite.id,
                                                    fid = 0L,
                                                    mid = 0L,
                                                    title = favorite.name,
                                                    count = favorite.trackCount,
                                                    coverUrl = favorite.coverUrl.orEmpty()
                                                )
                                            )
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!sortMode) sortMode = true
                                    toggleSelection(itemKey)
                                }
                            )
                    ) {
                        ListItem(
                            headlineContent = { Text(favorite.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.library_favorite_source_format,
                                        favorite.trackCount,
                                        favoriteSourceLabel(favorite.source)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = Color.Transparent
                            ),
                            leadingContent = {
                                if (!favorite.coverUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = offlineCachedImageRequest(
                                            context = context,
                                            data = favorite.coverUrl,
                                            sizePx = 192,
                                            allowHardware = false
                                        ),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                if (sortMode) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { toggleSelection(itemKey) }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .detectReorder(reorderState)
                                                .padding(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DragHandle,
                                                contentDescription = stringResource(R.string.common_drag_handle),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.dialog_confirm_delete)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.library_delete_selected_confirm,
                        selectedKeys.size,
                        selectedKeys.size
                    )
                )
            },
            confirmButton = {
                HapticTextButton(
                    onClick = {
                        val targets = reorderableFavorites.filter { favoriteKey(it) in selectedKeys }
                        scope.launch {
                            targets.forEach { favoriteRepo.removeFavorite(it.id, it.source) }
                            exitEditMode()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                HapticTextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private fun favoriteSourceLabel(source: String): String {
    return when (source) {
        "youtubeMusic" -> "YouTube"
        "neteaseAlbum" -> "Netease Album"
        "netease" -> "Netease"
        "bili" -> "Bilibili"
        else -> source
    }
}

@Composable
private fun QqMusicPlaylistList(
    listState: LazyListState
) {
    val miniPlayerHeight = LocalMiniPlayerHeight.current

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp + miniPlayerHeight),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val cardShape = RoundedCornerShape(12.dp)
        // TODO: Implement QQ Music playlist list when type is available
        item {
            Card(
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clip(cardShape)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_qqmusic_coming)) },
                    supportingContent = {
                        Text(stringResource(R.string.library_coming_soon), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                )
            }
        }
    }
}
