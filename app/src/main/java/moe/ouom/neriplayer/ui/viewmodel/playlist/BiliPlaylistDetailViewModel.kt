package moe.ouom.neriplayer.ui.viewmodel.playlist

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
 * File: moe.ouom.neriplayer.ui.viewmodel.playlist/BiliPlaylistDetailViewModel
 * Created: 2025/8/15
 */

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import moe.ouom.neriplayer.core.api.bili.buildBiliPartSong
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.ui.viewmodel.tab.BILI_COLLECTION_FID
import moe.ouom.neriplayer.ui.viewmodel.tab.BILI_SINGLE_VIDEO_FID
import moe.ouom.neriplayer.ui.viewmodel.tab.BiliPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.normalizeBiliPlaylistTitle
import java.io.IOException

/** Bilibili 视频条目数据模型 */
@Parcelize
data class BiliVideoItem(
    val id: Long, // avid
    val bvid: String,
    val title: String,
    val uploader: String,
    val coverUrl: String,
    val durationSec: Int,
    val cid: Long = 0L,
    val page: Int = 0
) : Parcelable {
    val stableKey: String
        get() = when {
            cid > 0L -> "$id:$cid"
            bvid.isNotBlank() -> bvid
            else -> id.toString()
        }
}

/** Bilibili 收藏夹详情页 UI 状态 */
data class BiliPlaylistDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val header: BiliPlaylist? = null,
    val videos: List<BiliVideoItem> = emptyList()
)

class BiliPlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AppContainer.biliClient

    private val _uiState = MutableStateFlow(BiliPlaylistDetailUiState())
    val uiState: StateFlow<BiliPlaylistDetailUiState> = _uiState

    private var mediaId: Long = 0L

    fun start(playlist: BiliPlaylist) {
        // 移除缓存检查，确保每次进入都能获取最新数据
        mediaId = playlist.mediaId

        _uiState.value = BiliPlaylistDetailUiState(
            loading = true,
            header = playlist.copy(title = normalizeBiliPlaylistTitle(playlist.title)),
            videos = emptyList()
        )
        loadContent()
    }

    fun retry() {
        uiState.value.header?.let { start(it) }
    }


    /**
     * 获取单个视频的详细信息，包括分P列表
     * @param bvid 视频的 BV 号
     * @return 包含所有分P信息的 VideoBasicInfo 对象
     */
    suspend fun getVideoInfo(bvid: String): BiliClient.VideoBasicInfo {
        return withContext(Dispatchers.IO) {
            client.getVideoBasicInfoByBvid(bvid)
        }
    }

    suspend fun getVideoInfo(video: BiliVideoItem): BiliClient.VideoBasicInfo {
        return withContext(Dispatchers.IO) {
            if (video.bvid.isNotBlank()) {
                client.getVideoBasicInfoByBvid(video.bvid)
            } else {
                client.getVideoBasicInfoByAvid(video.id)
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val header = _uiState.value.header
                val videos = if (header?.fid == BILI_SINGLE_VIDEO_FID) {
                    withContext(Dispatchers.IO) {
                        val info = if (header.bvid.isNotBlank()) {
                            client.getVideoBasicInfoByBvid(header.bvid)
                        } else {
                            client.getVideoBasicInfoByAvid(header.mediaId)
                        }
                        info.toDisplayItems(fallbackCoverUrl = header.coverUrl)
                    }
                } else if (header?.fid == BILI_COLLECTION_FID) {
                    withContext(Dispatchers.IO) {
                        client.getAllSeasonArchives(
                            mid = header.mid,
                            seasonId = header.mediaId
                        ).map { item ->
                            BiliVideoItem(
                                id = item.aid,
                                bvid = item.bvid,
                                title = item.title,
                                uploader = item.author,
                                coverUrl = item.coverUrl,
                                durationSec = item.durationSec
                            )
                        }
                    }
                } else {
                    val items = withContext(Dispatchers.IO) {
                        client.getAllFavFolderItems(mediaId)
                    }

                    items.mapNotNull {
                        // 仅保留视频类型的内容
                        if (it.type == 2) {
                            BiliVideoItem(
                                id = it.id,
                                bvid = it.bvid ?: "",
                                title = it.title,
                                uploader = it.upperName,
                                coverUrl = it.coverUrl
                                    .replaceFirst("http://", "https://")
                                    .ifBlank { header?.coverUrl.orEmpty() },
                                durationSec = it.durationSec
                            )
                        } else {
                            null
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    loading = false,
                    header = header?.copy(count = videos.size),
                    videos = videos
                )

            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Network error: ${e.message}"  // Localized in UI
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "Load failed: ${e.message}"  // Localized in UI
                )
            }
        }
    }

    private fun BiliClient.VideoBasicInfo.toDisplayItems(fallbackCoverUrl: String): List<BiliVideoItem> {
        val cover = fallbackCoverUrl.ifBlank { coverUrl }
        if (pages.size > 1) {
            return pages.map { page ->
                BiliVideoItem(
                    id = aid,
                    bvid = bvid,
                    title = normalizeBiliPlaylistTitle(page.part.ifBlank { "${title} P${page.page}" }),
                    uploader = ownerName,
                    coverUrl = cover,
                    durationSec = page.durationSec.takeIf { it > 0 } ?: durationSec,
                    cid = page.cid,
                    page = page.page
                )
            }
        }

        return listOf(
            BiliVideoItem(
                id = aid,
                bvid = bvid,
                title = normalizeBiliPlaylistTitle(title),
                uploader = ownerName,
                coverUrl = cover,
                durationSec = durationSec.takeIf { it > 0 } ?: pages.firstOrNull()?.durationSec.orZero()
            )
        )
    }

    private fun Int?.orZero(): Int = this ?: 0

    /**
     * 将 Bilibili 视频的分P转换为通用的 SongItem
     * @param page 分P信息
     * @param basicInfo 视频的基本信息
     * @param coverUrl 视频封面
     * @return 转换后的 SongItem
     */
    fun toSongItem(page: BiliClient.VideoPage, basicInfo: BiliClient.VideoBasicInfo, coverUrl: String): SongItem {
        return buildBiliPartSong(page, basicInfo, coverUrl)
    }
}
