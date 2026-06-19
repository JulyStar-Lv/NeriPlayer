package moe.ouom.neriplayer.ui.viewmodel.tab

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
 * File: moe.ouom.neriplayer.ui.viewmodel.tab/LibraryViewModel
 * Created: 2025/8/11
 */

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.core.api.bili.BiliClient
import moe.ouom.neriplayer.core.api.youtube.YouTubeMusicLibraryPlaylist
import moe.ouom.neriplayer.core.di.AppContainer
import moe.ouom.neriplayer.data.local.playlist.model.LocalPlaylist
import moe.ouom.neriplayer.data.local.playlist.LocalPlaylistRepository
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.util.NPLogger
import org.json.JSONObject
import java.io.IOException

private const val BILI_FOLDER_PAGE_SIZE = 20
private const val BILI_FOLDER_PAGE_FETCH_BATCH_SIZE = 4
private const val BILI_INVALID_FOLDER_TITLE_MARKER = "失效"

/** 媒体库页面 UI 状态 */
data class LibraryUiState(
    val localPlaylists: List<LocalPlaylist> = emptyList(),
    val neteasePlaylists: List<PlaylistSummary> = emptyList(),
    val neteaseAlbums: List<AlbumSummary> = emptyList(),
    val neteaseError: String? = null,
    val youtubeMusicPlaylists: List<YouTubeMusicPlaylist> = emptyList(),
    val youtubeMusicError: String? = null,
    val biliPlaylists: List<BiliPlaylist> = emptyList(),
    val biliError: String? = null,
    val biliFolderCollections: Map<Long, List<BiliPlaylist>> = emptyMap(),
    val biliFolderCollectionLoading: Set<Long> = emptySet(),
    val biliFolderCollectionErrors: Map<Long, String> = emptyMap()
)

@Suppress("unused")
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepo = LocalPlaylistRepository.getInstance(application)

    private val neteaseCookieRepo = AppContainer.neteaseCookieRepo
    private val neteaseClient = AppContainer.neteaseClient

    private val biliCookieRepo = AppContainer.biliCookieRepo
    private val biliClient = AppContainer.biliClient
    private val youtubeAuthRepo = AppContainer.youtubeAuthRepo
    private val youtubeMusicClient = AppContainer.youtubeMusicClient


    private val _uiState = MutableStateFlow(
        LibraryUiState(localPlaylists = localRepo.playlists.value)
    )
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        // 本地歌单
        viewModelScope.launch {
            localRepo.playlists.collect { list ->
                _uiState.value = _uiState.value.copy(localPlaylists = list)
            }
        }

        // 网易云 歌单
        viewModelScope.launch {
            neteaseCookieRepo.cookieFlow.collect { cookies ->
                val mutable = cookies.toMutableMap()
                mutable.putIfAbsent("os", "pc")
                if (!cookies["MUSIC_U"].isNullOrBlank()) {
                    refreshNeteasePlaylists()
                } else {
                    _uiState.value = _uiState.value.copy(
                        neteasePlaylists = emptyList(),
                        neteaseError = null
                    )
                }
            }
        }
        // 网易云 专辑
        viewModelScope.launch {
            neteaseCookieRepo.cookieFlow.collect { cookies ->
                val mutable = cookies.toMutableMap()
                mutable.putIfAbsent("os", "pc")
                if (!cookies["MUSIC_U"].isNullOrBlank()) {
                    refreshNeteaseAlbums()
                } else {
                    _uiState.value = _uiState.value.copy(
                        neteaseAlbums = emptyList(),
                        neteaseError = null
                    )
                }
            }
        }

        // YouTube Music
        viewModelScope.launch {
            youtubeAuthRepo.authFlow.collect { bundle ->
                if (!bundle.hasLoginCookies()) {
                    _uiState.value = _uiState.value.copy(
                        youtubeMusicPlaylists = emptyList(),
                        youtubeMusicError = null
                    )
                } else {
                    refreshYouTubeMusicPlaylists()
                }
            }
        }

        // Bilibili
        viewModelScope.launch {
            biliCookieRepo.cookieFlow.collect { cookies ->
                if (!cookies["SESSDATA"].isNullOrBlank()) {
                    refreshBilibili()
                } else {
                    _uiState.value = _uiState.value.copy(
                        biliPlaylists = emptyList(),
                        biliError = null
                    )
                }
            }
        }
    }

    fun refreshBilibili() {
        viewModelScope.launch {
            try {
                val mid = biliCookieRepo.getCookiesOnce()["DedeUserID"]?.toLongOrNull() ?: 0L
                if (mid == 0L) {
                    _uiState.value = _uiState.value.copy(biliError = getApplication<Application>().getString(R.string.error_get_user_id))
                    return@launch
                }
                val rawList = withContext(Dispatchers.IO) { biliClient.getUserCreatedFavFolders(mid) }

                // 并发获取每个收藏夹的详细信息
                val mapped = withContext(Dispatchers.IO) {
                    rawList.map { folder ->
                        async {
                            try {
                                val folderInfo = biliClient.getFavFolderInfo(folder.mediaId)
                                BiliPlaylist(
                                    mediaId = folderInfo.mediaId,
                                    fid = folderInfo.fid,
                                    mid = folderInfo.mid,
                                    title = normalizeBiliPlaylistTitle(folderInfo.title),
                                    count = folderInfo.count,
                                    coverUrl = folderInfo.coverUrl.replace("http://", "https://")
                                )
                            } catch (e: Exception) {
                                // 获取详情失败，使用原始数据并提供一个空的封面URL
                                NPLogger.e("LibraryViewModel-Bili", getApplication<Application>().getString(R.string.music_get_detail_failed), e)
                                BiliPlaylist(
                                    mediaId = folder.mediaId,
                                    fid = folder.fid,
                                    mid = folder.mid,
                                    title = normalizeBiliPlaylistTitle(folder.title),
                                    count = folder.count,
                                    coverUrl = ""
                                )
                            }
                        }
                    }.awaitAll()
                }

                NPLogger.d("LibraryViewModel-Bili",mapped)

                _uiState.value = _uiState.value.copy(biliPlaylists = mapped, biliError = null)
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(biliError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(biliError = e.message)
            }
        }
    }

    fun refreshBiliFolderCollections(folder: BiliPlaylist) {
        val folderId = folder.mediaId
        if (_uiState.value.biliFolderCollectionLoading.contains(folderId)) return
        if (_uiState.value.biliFolderCollections.containsKey(folderId)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                biliFolderCollectionLoading = _uiState.value.biliFolderCollectionLoading + folderId,
                biliFolderCollectionErrors = _uiState.value.biliFolderCollectionErrors - folderId
            )

            try {
                val firstPage = withContext(Dispatchers.IO) {
                    biliClient.getFavFolderContents(
                        mediaId = folderId,
                        page = 1,
                        pageSize = BILI_FOLDER_PAGE_SIZE
                    )
                }
                val totalPages = ((firstPage.info.count + BILI_FOLDER_PAGE_SIZE - 1) /
                    BILI_FOLDER_PAGE_SIZE).coerceAtLeast(1)
                val mapped = firstPage.items.toBiliImportPlaylists()
                val allPlaylists = mapped.toMutableList()

                _uiState.value = _uiState.value.copy(
                    biliFolderCollections = _uiState.value.biliFolderCollections + (folderId to mapped)
                )

                if (totalPages > 1) {
                    (2..totalPages)
                        .chunked(BILI_FOLDER_PAGE_FETCH_BATCH_SIZE)
                        .forEach { batch ->
                            val pageResults = withContext(Dispatchers.IO) {
                                batch.map { page ->
                                    async {
                                        page to runCatching {
                                            biliClient.getFavFolderContents(
                                                mediaId = folderId,
                                                page = page,
                                                pageSize = BILI_FOLDER_PAGE_SIZE
                                            ).items
                                        }.getOrElse { error ->
                                            NPLogger.e(
                                                "LibraryViewModel-Bili",
                                                "Failed to fetch page $page for mediaId $folderId",
                                                error
                                            )
                                            emptyList()
                                        }
                                    }
                                }.awaitAll()
                            }
                            val nextPlaylists = pageResults
                                .sortedBy { it.first }
                                .flatMap { it.second.toBiliImportPlaylists() }
                            if (nextPlaylists.isNotEmpty()) {
                                allPlaylists += nextPlaylists
                                _uiState.value = _uiState.value.copy(
                                    biliFolderCollections = _uiState.value.biliFolderCollections +
                                        (folderId to allPlaylists.toList())
                                )
                            }
                        }
                }

                _uiState.value = _uiState.value.copy(
                    biliFolderCollections = _uiState.value.biliFolderCollections +
                        (folderId to allPlaylists.toList()),
                    biliFolderCollectionLoading = _uiState.value.biliFolderCollectionLoading - folderId,
                    biliFolderCollectionErrors = _uiState.value.biliFolderCollectionErrors - folderId
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    biliFolderCollectionLoading = _uiState.value.biliFolderCollectionLoading - folderId,
                    biliFolderCollectionErrors = _uiState.value.biliFolderCollectionErrors + (folderId to (e.message ?: "Network error"))
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    biliFolderCollectionLoading = _uiState.value.biliFolderCollectionLoading - folderId,
                    biliFolderCollectionErrors = _uiState.value.biliFolderCollectionErrors + (folderId to (e.message ?: e.javaClass.simpleName))
                )
            }
        }
    }

    suspend fun removeBiliVideoFromFolder(folder: BiliPlaylist, video: BiliPlaylist): Result<Unit> {
        if (video.fid != BILI_SINGLE_VIDEO_FID || video.mediaId == 0L) {
            return Result.failure(IllegalArgumentException("Unsupported Bilibili favorite item"))
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                biliClient.removeVideoFromFavFolder(folder.mediaId, video.mediaId)
            }
            val current = _uiState.value
            val updatedCollections = current.biliFolderCollections[folder.mediaId]
                ?.filterNot { it.mediaId == video.mediaId }
            val updatedTopFolders = current.biliPlaylists.map { playlist ->
                if (playlist.mediaId == folder.mediaId) {
                    playlist.copy(count = (playlist.count - 1).coerceAtLeast(0))
                } else {
                    playlist
                }
            }
            _uiState.value = current.copy(
                biliPlaylists = updatedTopFolders,
                biliFolderCollections = if (updatedCollections != null) {
                    current.biliFolderCollections + (folder.mediaId to updatedCollections)
                } else {
                    current.biliFolderCollections
                }
            )
        }
    }

    suspend fun removeInvalidBiliFolderVideos(folder: BiliPlaylist): Result<Int> {
        if (!folder.title.contains(BILI_INVALID_FOLDER_TITLE_MARKER)) {
            return Result.failure(IllegalArgumentException("Only invalid Bilibili folders can be cleaned"))
        }
        return runCatching {
            val items = withContext(Dispatchers.IO) {
                biliClient.getAllFavFolderItems(folder.mediaId)
            }
            items.forEach { item ->
                withContext(Dispatchers.IO) {
                    biliClient.removeResourceFromFavFolder(
                        mediaId = folder.mediaId,
                        rid = item.id,
                        type = item.type
                    )
                }
            }
            val current = _uiState.value
            _uiState.value = current.copy(
                biliPlaylists = current.biliPlaylists.filterNot { it.mediaId == folder.mediaId },
                biliFolderCollections = current.biliFolderCollections - folder.mediaId,
                biliFolderCollectionErrors = current.biliFolderCollectionErrors - folder.mediaId,
                biliFolderCollectionLoading = current.biliFolderCollectionLoading - folder.mediaId
            )
            items.size
        }
    }

    private fun List<BiliClient.FavResourceItem>.toBiliImportPlaylists(): List<BiliPlaylist> {
        return mapNotNull { item ->
            if (item.type == 2) item.toBiliImportPlaylist() else null
        }
    }

    private fun BiliClient.FavResourceItem.toBiliImportPlaylist(): BiliPlaylist {
        return BiliPlaylist(
            mediaId = id,
            fid = BILI_SINGLE_VIDEO_FID,
            mid = upperMid,
            title = normalizeBiliPlaylistTitle(title),
            count = pageCount.takeIf { it > 0 } ?: 1,
            coverUrl = coverUrl.replace("http://", "https://"),
            bvid = bvid.orEmpty()
        )
    }


    fun refreshNeteasePlaylists() {
        viewModelScope.launch {
            try {
                val uid = withContext(Dispatchers.IO) { neteaseClient.getCurrentUserId() }
                val raw = withContext(Dispatchers.IO) { neteaseClient.getUserPlaylists(uid) }
                val mapped = parseNeteasePlaylists(raw)
                _uiState.value = _uiState.value.copy(
                    neteasePlaylists = mapped,
                    neteaseError = null
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            }
        }
    }
    
    fun refreshNeteaseAlbums() {
        viewModelScope.launch {
            try {
                val uid = withContext(Dispatchers.IO) { neteaseClient.getCurrentUserId() }
                val raw = withContext(Dispatchers.IO) { neteaseClient.getUserStaredAlbums(uid) }
                val mapped = parseNeteaseAlbums(raw)
                _uiState.value = _uiState.value.copy(
                    neteaseAlbums = mapped,
                    neteaseError = null
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(neteaseError = e.message)
            }
        }
    }

    fun refreshYouTubeMusicPlaylists() {
        viewModelScope.launch {
            try {
                val playlists = withContext(Dispatchers.IO) {
                    youtubeMusicClient.getLibraryPlaylists()
                }
                _uiState.value = _uiState.value.copy(
                    youtubeMusicPlaylists = playlists.map(::mapYouTubeMusicPlaylist),
                    youtubeMusicError = null
                )
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    youtubeMusicPlaylists = emptyList(),
                    youtubeMusicError = e.message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    youtubeMusicPlaylists = emptyList(),
                    youtubeMusicError = e.message
                )
            }
        }
    }

    fun createLocalPlaylist(name: String) {
        viewModelScope.launch { localRepo.createPlaylist(name) }
    }

    fun addSongToFavorites(song: SongItem) {
        viewModelScope.launch { localRepo.addToFavorites(song) }
    }

    fun renameLocalPlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch { localRepo.renamePlaylist(playlistId, newName) }
    }

    fun deleteLocalPlaylist(playlistId: Long) {
        viewModelScope.launch { localRepo.deletePlaylist(playlistId) }
    }

    fun reorderLocalPlaylists(order: List<Long>) {
        viewModelScope.launch { localRepo.reorderPlaylists(order) }
    }

    private fun parseNeteasePlaylists(raw: String): List<PlaylistSummary> {
        val result = mutableListOf<PlaylistSummary>()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) return emptyList()
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val size = arr.length()
        for (i in 0 until size) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name", "")
            val cover = obj.optString("coverImgUrl", "").replaceFirst("http://", "https://")
            val playCount = obj.optLong("playCount", 0L)
            val trackCount = obj.optInt("trackCount", 0)
            if (id != 0L && name.isNotBlank()) {
                result.add(PlaylistSummary(id, name, cover, playCount, trackCount))
            }
        }
        return result
    }
    
    private fun parseNeteaseAlbums(raw: String): List<AlbumSummary> {
        val result = mutableListOf<AlbumSummary>()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) return emptyList()
        val arr = root.optJSONArray("playlist") ?: return emptyList()
        val size = arr.length()
        for (i in 0 until size) {
            val obj = arr.optJSONObject(i)?.optJSONObject("dataInfo")?.optJSONObject("data") ?: continue
            val id = obj.optLong("id", 0L)
            val name = obj.optString("name", "")
            val cover = arr.optJSONObject(i)?.optJSONObject("dataInfo")?.optString("picUrl", "")?.replaceFirst("http://", "https://") ?: continue
            val songSize = obj.optInt("size", 0)
            if (id != 0L && name.isNotBlank()) {
                result.add(AlbumSummary(id, name, cover, songSize))
            }
        }
        return result
    }

    private fun mapYouTubeMusicPlaylist(
        playlist: YouTubeMusicLibraryPlaylist
    ): YouTubeMusicPlaylist {
        return YouTubeMusicPlaylist(
            browseId = playlist.browseId,
            playlistId = playlist.playlistId,
            title = playlist.title,
            subtitle = playlist.subtitle,
            coverUrl = playlist.coverUrl,
            trackCount = playlist.trackCount ?: 0
        )
    }
}
