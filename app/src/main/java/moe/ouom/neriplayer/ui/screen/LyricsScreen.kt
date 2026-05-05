package moe.ouom.neriplayer.ui.screen

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
 * File: moe.ouom.neriplayer.ui.screen/LyricsScreen
 * Created: 2025/8/13
 */

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.player.PlayerManager
import moe.ouom.neriplayer.data.local.playlist.system.FavoritesPlaylist
import moe.ouom.neriplayer.data.local.playlist.system.LocalFilesPlaylist
import moe.ouom.neriplayer.data.settings.scaledLyricFontSize
import moe.ouom.neriplayer.data.model.displayArtist
import moe.ouom.neriplayer.data.model.displayCoverUrl
import moe.ouom.neriplayer.data.model.displayName
import moe.ouom.neriplayer.data.local.media.isLocalSong
import moe.ouom.neriplayer.data.model.sameIdentityAs
import moe.ouom.neriplayer.data.model.stableKey
import moe.ouom.neriplayer.ui.component.AdvancedLyricsView
import moe.ouom.neriplayer.ui.component.AppleMusicLyric
import moe.ouom.neriplayer.ui.component.flattenWordTimedEntries
import moe.ouom.neriplayer.ui.component.LyricEntry
import moe.ouom.neriplayer.ui.component.LocalSongDetailsDialog
import moe.ouom.neriplayer.ui.component.LocalSongSyncConfirmDialog
import moe.ouom.neriplayer.ui.component.LyricVisualSpec
import moe.ouom.neriplayer.ui.component.SleepTimerDialog
import moe.ouom.neriplayer.ui.component.WaveformSlider
import moe.ouom.neriplayer.ui.component.bottomSheetScrollGuard
import moe.ouom.neriplayer.ui.viewmodel.tab.AlbumSummary
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.HapticFilledIconButton
import moe.ouom.neriplayer.util.HapticIconButton
import moe.ouom.neriplayer.util.formatDuration
import moe.ouom.neriplayer.util.offlineCachedImageRequest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun LyricsScreen(
    lyrics: List<LyricEntry>,
    rawLyrics: String? = null,
    rawTranslatedLyrics: String? = null,
    lyricBlurEnabled: Boolean,
    lyricBlurAmount: Float,
    lyricFontScale: Float,
    onLyricFontScaleChange: (Float) -> Unit,
    onEnterAlbum: (AlbumSummary) -> Unit,
    onNavigateBack: () -> Unit,
    onCollapse: () -> Unit = onNavigateBack,
    onSeekTo: (Long) -> Unit,
    advancedLyricsEnabled: Boolean = true,
    translatedLyrics: List<LyricEntry>? = null,
    lyricOffsetMs: Long,
    showLyricTranslation: Boolean = true,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope? = null,
) {
    // 处理返回键
    androidx.activity.compose.BackHandler(onBack = onNavigateBack)

    val currentSong by PlayerManager.currentSongFlow.collectAsState()
    val isPlaying by PlayerManager.isPlayingFlow.collectAsState()
    val isPlaybackControlPlaying by PlayerManager.playbackControlPlayingFlow.collectAsState()
    val lyricsPlaybackSoundState by PlayerManager.playbackSoundStateFlow.collectAsState()
    val currentPlaybackAudioInfo by PlayerManager.currentPlaybackAudioInfoFlow.collectAsState()
    val plainLyrics = remember(lyrics) { lyrics.flattenWordTimedEntries() }
    val plainTranslatedLyrics = remember(translatedLyrics) {
        translatedLyrics.orEmpty().flattenWordTimedEntries()
    }
    val durationMs = currentSong?.durationMs ?: 0L
    val favoriteActionLabel = stringResource(R.string.favorite_add)
    val playlistAddActionLabel = stringResource(R.string.playlist_add_to)
    val topActionButtonSize = 52.dp
    val topActionIconSize = 28.dp
    val toolbarButtonSize = 52.dp
    val toolbarIconSize = 28.dp

    val context = LocalContext.current
    val currentCoverUrl = remember(currentSong, context) {
        currentSong?.displayCoverUrl(context)
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var showSongNameMenu by remember { mutableStateOf(false) }
    var showArtistMenu by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQualitySwitchDialog by remember { mutableStateOf(false) }
    var detailSong by remember { mutableStateOf<SongItem?>(null) }
    var pendingSyncConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSyncConfirmLabel by remember { mutableStateOf("") }

    var previewPositionOverrideMs by remember(currentSong?.id) { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentSong?.id) {
        showQualitySwitchDialog = false
    }

    fun launchWithLocalSyncWarning(
        song: SongItem?,
        actionLabel: String,
        warnForLocalSync: Boolean = true,
        action: () -> Unit
    ) {
        if (warnForLocalSync && song?.isLocalSong() == true) {
            pendingSyncConfirmLabel = actionLabel
            pendingSyncConfirmAction = action
        } else {
            action()
        }
    }

    val playlists by PlayerManager.playlistsFlow.collectAsState()
    val isFavoriteComputed = remember(currentSong, playlists) {
        val song = currentSong
        if (song == null) {
            false
        } else {
            val fav = playlists.firstOrNull { FavoritesPlaylist.isSystemPlaylist(it, context) }
            fav?.songs?.any { it.sameIdentityAs(song) } == true
        }
    }
    var favOverride by remember(currentSong) { mutableStateOf<Boolean?>(null) }
    val isFavorite = favOverride ?: isFavoriteComputed

    val headerCoverSize = 44.dp

    // 播放控件动画 - 轻微上浮/下沉，保持常驻在安全区域内

    // 使用填充整个屏幕，不创建新背景，复用现有背景
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    // 右滑返回
                    if (dragAmount > 50) {
                        onNavigateBack()
                    }
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // 顶部区域 - 包含缩小的封面和歌曲信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            HapticIconButton(onClick = onCollapse) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_back))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 外层只负责最终尺寸和位置，内层参与共享转场，避免 overlay 结束时再应用位移造成跳变。
            Box(
                modifier = Modifier
                    .size(headerCoverSize)
                    .offset(y = 2.dp)
                    .clip(PlayerCoverArtworkShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (sharedTransitionScope != null && animatedContentScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "cover_image"),
                                        animatedVisibilityScope = animatedContentScope,
                                        clipInOverlayDuringTransition = OverlayClip(
                                            PlayerCoverArtworkShape
                                        )
                                    )
                                }
                            } else Modifier
                        )
                        .clip(PlayerCoverArtworkShape)
                ) {
                    currentCoverUrl?.let { cover ->
                        AsyncImage(
                            model = remember(context, cover) {
                                offlineCachedImageRequest(
                                    context = context,
                                    data = cover,
                                    sizePx = 192,
                                    allowHardware = false
                                )
                            },
                            contentDescription = currentSong?.displayName() ?: "",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 标题区始终占用剩余空间，避免挤出边界
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = currentSong?.displayName() ?: stringResource(R.string.lyrics_unknown_song),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showSongNameMenu = true }
                            )
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    DropdownMenu(
                        expanded = showSongNameMenu,
                        onDismissRequest = { showSongNameMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_song_name)) },
                            onClick = {
                                currentSong?.displayName()?.let { text ->
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text)))
                                    }
                                }
                                showSongNameMenu = false
                            }
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = currentSong?.displayArtist() ?: stringResource(R.string.lyrics_unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showArtistMenu = true }
                            )
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    DropdownMenu(
                        expanded = showArtistMenu,
                        onDismissRequest = { showArtistMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_artist)) },
                            onClick = {
                                currentSong?.displayArtist()?.let { text ->
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("text", text)))
                                    }
                                }
                                showArtistMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            HapticIconButton(
                onClick = { showMoreOptions = true },
                modifier = Modifier
                    .size(topActionButtonSize)
                    .then(
                        if (sharedTransitionScope != null && animatedContentScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState(key = "btn_more"),
                                    animatedVisibilityScope = animatedContentScope,
                                    enter = EnterTransition.None,
                                    exit = ExitTransition.None,
                                ).zIndex(1f)
                            }
                        } else Modifier
                    )
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.lyrics_more_options),
                    modifier = Modifier.size(topActionIconSize)
                )
            }
        }

        if (showMoreOptions && currentSong != null) {
            val queue by PlayerManager.currentQueueFlow.collectAsState()
            val displayedQueue = remember(queue) { queue }
            val nowPlayingViewModel: moe.ouom.neriplayer.ui.viewmodel.NowPlayingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val snackbarHostState = remember { SnackbarHostState() }
            MoreOptionsSheet(
                viewModel = nowPlayingViewModel,
                originalSong = currentSong!!,
                queue = displayedQueue,
                displayedLyrics = lyrics,
                displayedTranslatedLyrics = translatedLyrics.orEmpty(),
                onDismiss = { showMoreOptions = false },
                onShowSongDetails = { detailSong = it },
                onEnterAlbum = onEnterAlbum,
                onNavigateUp = onCollapse,
                snackbarHostState = snackbarHostState,
                lyricFontScale = lyricFontScale,
                onLyricFontScaleChange = onLyricFontScaleChange,
                currentPlaybackAudioInfo = currentPlaybackAudioInfo,
                onShowQualitySwitch = { showQualitySwitchDialog = true },
                onShowSleepTimer = { showSleepTimerDialog = true },
                onShowAddToPlaylist = { showAddSheet = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 歌词区域
        Box(
            modifier = Modifier.weight(1f)
        ) {
            LyricsContentPane(
                lyrics = lyrics,
                plainLyrics = plainLyrics,
                plainTranslatedLyrics = plainTranslatedLyrics,
                translatedLyrics = translatedLyrics.orEmpty(),
                previewPositionOverrideMs = previewPositionOverrideMs,
                advancedLyricsEnabled = advancedLyricsEnabled,
                showLyricTranslation = showLyricTranslation,
                lyricFontScale = lyricFontScale,
                lyricOffsetMs = lyricOffsetMs,
                lyricBlurEnabled = lyricBlurEnabled,
                lyricBlurAmount = lyricBlurAmount,
                textColor = MaterialTheme.colorScheme.onBackground,
                rawLyrics = rawLyrics,
                rawTranslatedLyrics = rawTranslatedLyrics,
                playbackSpeed = lyricsPlaybackSoundState.speed,
                isPlaying = isPlaying,
                onSeekTo = onSeekTo
            )
        }

        // 底部控件：保留进度条，操作栏左侧放歌词和收藏，右侧放播放/暂停。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HapticIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .then(
                                if (sharedTransitionScope != null && animatedContentScope != null) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(key = "btn_lyrics"),
                                            animatedVisibilityScope = animatedContentScope,
                                            enter = EnterTransition.None,
                                            exit = ExitTransition.None,
                                        ).zIndex(1f)
                                    }
                                } else Modifier
                            )
                            .size(toolbarButtonSize)
                    ) {
                        AnimatedContent(
                            targetState = true,
                            label = "lyrics_icon"
                        ) { isShowingLyrics ->
                            Icon(
                                painter = painterResource(R.drawable.ic_lyrics_bubble_quote),
                                contentDescription = stringResource(R.string.lyrics_back_to_cover),
                                tint = if (lyrics.isEmpty()) {
                                    LocalContentColor.current.copy(alpha = 0.38f)
                                } else if (isShowingLyrics) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                                modifier = Modifier.size(toolbarIconSize)
                            )
                        }
                    }

                    HapticIconButton(
                        onClick = {
                            if (currentSong == null) return@HapticIconButton
                            val willFav = !isFavorite
                            launchWithLocalSyncWarning(
                                song = currentSong,
                                actionLabel = favoriteActionLabel,
                                warnForLocalSync = willFav
                            ) {
                                if (willFav) {
                                    PlayerManager.addCurrentToFavorites()
                                } else {
                                    PlayerManager.removeCurrentFromFavorites()
                                }
                            }
                        },
                        modifier = Modifier
                            .then(
                                if (sharedTransitionScope != null && animatedContentScope != null) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedBounds(
                                            rememberSharedContentState(key = "btn_favorite"),
                                            animatedVisibilityScope = animatedContentScope,
                                            enter = EnterTransition.None,
                                            exit = ExitTransition.None,
                                        ).zIndex(1f)
                                    }
                                } else Modifier
                            )
                            .size(toolbarButtonSize)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) {
                                stringResource(R.string.lyrics_favorited)
                            } else {
                                stringResource(R.string.lyrics_favorite)
                            },
                            tint = if (isFavorite) {
                                Color.Red.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(toolbarIconSize)
                        )
                    }
                }

                HapticFilledIconButton(
                    onClick = { PlayerManager.togglePlayPause() },
                    modifier = Modifier
                        .then(
                            if (sharedTransitionScope != null && animatedContentScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "play_button"),
                                        animatedVisibilityScope = animatedContentScope
                                    )
                                }
                            } else Modifier
                        )
                        .size(toolbarButtonSize)
                ) {
                    AnimatedContent(
                        targetState = isPlaybackControlPlaying,
                        label = "play_pause_icon",
                        transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) }
                    ) { currentlyPlaying ->
                        Icon(
                            imageVector = if (currentlyPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (currentlyPlaying) stringResource(R.string.lyrics_pause) else stringResource(R.string.lyrics_play),
                            modifier = Modifier.size(toolbarIconSize)
                        )
                    }
                }
            }
        }

        if (showQualitySwitchDialog && currentPlaybackAudioInfo != null) {
            NowPlayingQualityOptionsDialog(
                title = stringResource(R.string.nowplaying_quality_switch_title),
                selectedKey = currentPlaybackAudioInfo?.qualityKey,
                options = currentPlaybackAudioInfo?.qualityOptions.orEmpty(),
                onDismiss = { showQualitySwitchDialog = false },
                onSelect = { option ->
                    PlayerManager.changeCurrentPlaybackQuality(option.key)
                    showQualitySwitchDialog = false
                }
            )
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                onDismiss = { showSleepTimerDialog = false }
            )
        }

        if (showAddSheet && currentSong != null) {
            val selectablePlaylists = remember(playlists, context) {
                playlists.filterNot { LocalFilesPlaylist.isSystemPlaylist(it, context) }
            }
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                sheetGesturesEnabled = false
            ) {
                LazyColumn(modifier = Modifier.bottomSheetScrollGuard()) {
                    itemsIndexed(
                        items = selectablePlaylists,
                        key = { _, pl -> pl.id },
                        contentType = { _, _ -> "playlist_option" }
                    ) { _, pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    launchWithLocalSyncWarning(
                                        song = currentSong,
                                        actionLabel = playlistAddActionLabel
                                    ) {
                                        PlayerManager.addCurrentToPlaylist(pl.id)
                                        showAddSheet = false
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pl.name, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                pluralStringResource(
                                    R.plurals.lyrics_song_count,
                                    pl.songs.size,
                                    pl.songs.size
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        detailSong?.let { song ->
            LocalSongDetailsDialog(
                song = song,
                onDismiss = { detailSong = null }
            )
        }

        pendingSyncConfirmAction?.let { action ->
            LocalSongSyncConfirmDialog(
                actionLabel = pendingSyncConfirmLabel,
                onConfirm = {
                    pendingSyncConfirmAction = null
                    pendingSyncConfirmLabel = ""
                    action()
                },
                onDismiss = {
                    pendingSyncConfirmAction = null
                    pendingSyncConfirmLabel = ""
                }
            )
        }
    }
}

@Composable
private fun LyricsContentPane(
    lyrics: List<LyricEntry>,
    plainLyrics: List<LyricEntry>,
    plainTranslatedLyrics: List<LyricEntry>,
    translatedLyrics: List<LyricEntry>,
    previewPositionOverrideMs: Long?,
    advancedLyricsEnabled: Boolean,
    showLyricTranslation: Boolean,
    lyricFontScale: Float,
    lyricOffsetMs: Long,
    lyricBlurEnabled: Boolean,
    lyricBlurAmount: Float,
    textColor: Color,
    rawLyrics: String?,
    rawTranslatedLyrics: String?,
    playbackSpeed: Float,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.lyrics_no_lyrics),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        return
    }

    val currentPosition by PlayerManager.playbackPositionFlow.collectAsState()
    val effectiveLyricTimeMs = previewPositionOverrideMs ?: currentPosition
    val isPreviewingSeek = previewPositionOverrideMs != null
    val shouldAnimateFromPlayback = isPlaying && !isPreviewingSeek

    if (advancedLyricsEnabled) {
        AdvancedLyricsView(
            lyrics = lyrics,
            currentTimeMs = effectiveLyricTimeMs,
            modifier = Modifier.fillMaxSize(),
            textColor = textColor,
            lyricFontScale = lyricFontScale,
            baseFontSizeSp = 20f,
            lyricOffsetMs = lyricOffsetMs,
            lyricBlurEnabled = lyricBlurEnabled,
            lyricBlurAmount = lyricBlurAmount,
            translatedLyrics = translatedLyrics,
            showLyricTranslation = showLyricTranslation,
            rawLyrics = rawLyrics,
            rawTranslatedLyrics = rawTranslatedLyrics,
            isPlaying = shouldAnimateFromPlayback,
            animateViewportScroll = isPreviewingSeek,
            playbackSpeed = playbackSpeed,
            onSeekTo = onSeekTo
        )
        return
    }

    AppleMusicLyric(
        lyrics = plainLyrics,
        currentTimeMs = effectiveLyricTimeMs,
        modifier = Modifier.fillMaxSize(),
        textColor = textColor,
        fontSize = scaledLyricFontSize(20f, lyricFontScale).sp,
        centerPadding = 24.dp,
        visualSpec = LyricVisualSpec(
            activeScale = 1.06f,
            nearScale = 0.95f,
            farScale = 0.88f,
            inactiveBlurNear = 0.dp,
            inactiveBlurFar = 0.dp
        ),
        lyricOffsetMs = lyricOffsetMs,
        lyricBlurEnabled = lyricBlurEnabled,
        lyricBlurAmount = lyricBlurAmount,
        onLyricClick = { lyricEntry ->
            onSeekTo(lyricEntry.startTimeMs)
        },
        translatedLyrics = if (showLyricTranslation) plainTranslatedLyrics else null,
        translationFontSize = scaledLyricFontSize(16f, lyricFontScale).sp,
    )
}

@Composable
private fun LyricsProgressSection(
    songKey: String?,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onPreviewPositionChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPosition by PlayerManager.playbackPositionFlow.collectAsState()
    val latestOnPreviewPositionChange by rememberUpdatedState(onPreviewPositionChange)
    var isUserDraggingSlider by remember(songKey) { mutableStateOf(false) }
    var sliderPosition by remember(songKey) {
        mutableFloatStateOf(PlayerManager.playbackPositionFlow.value.toFloat())
    }
    var pendingSeekPreviewPositionMs by remember(songKey) { mutableStateOf<Long?>(null) }
    val effectivePreviewPositionMs = resolveLyricPreviewTimeMs(
        isDraggingSlider = isUserDraggingSlider,
        sliderPreviewPositionMs = sliderPosition.toLong(),
        pendingSeekPreviewPositionMs = pendingSeekPreviewPositionMs,
        playbackPositionMs = currentPosition
    )
    val previewOverridePositionMs = remember(
        effectivePreviewPositionMs,
        isUserDraggingSlider,
        pendingSeekPreviewPositionMs
    ) {
        if (isUserDraggingSlider || pendingSeekPreviewPositionMs != null) {
            effectivePreviewPositionMs
        } else {
            null
        }
    }

    LaunchedEffect(currentPosition, isUserDraggingSlider, pendingSeekPreviewPositionMs) {
        if (!isUserDraggingSlider && pendingSeekPreviewPositionMs == null) {
            sliderPosition = currentPosition.toFloat()
        }
        val pendingPreview = pendingSeekPreviewPositionMs
        if (!isUserDraggingSlider &&
            pendingPreview != null &&
            shouldReleaseLyricSeekPreview(
                playbackPositionMs = currentPosition,
                pendingSeekPreviewPositionMs = pendingPreview
            )
        ) {
            pendingSeekPreviewPositionMs = null
        }
    }
    LaunchedEffect(previewOverridePositionMs) {
        latestOnPreviewPositionChange(previewOverridePositionMs)
    }
    DisposableEffect(Unit) {
        onDispose {
            latestOnPreviewPositionChange(null)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = formatDuration(effectivePreviewPositionMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        WaveformSlider(
            modifier = Modifier.weight(1f),
            value = if (durationMs > 0) {
                effectivePreviewPositionMs.toFloat() / durationMs
            } else {
                0f
            },
            onValueChange = { newValue ->
                isUserDraggingSlider = true
                sliderPosition = newValue * durationMs.toFloat()
            },
            onValueChangeFinished = {
                val previewTarget = sliderPosition.toLong()
                pendingSeekPreviewPositionMs = previewTarget
                onSeekTo(previewTarget)
                isUserDraggingSlider = false
            },
            isPlaying = isPlaying
        )

        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
