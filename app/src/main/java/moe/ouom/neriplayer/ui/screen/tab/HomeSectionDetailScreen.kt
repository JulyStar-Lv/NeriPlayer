package moe.ouom.neriplayer.ui.screen.tab

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.parcelize.Parcelize
import moe.ouom.neriplayer.R
import moe.ouom.neriplayer.data.model.displayCoverUrl
import moe.ouom.neriplayer.data.playlist.usage.UsageEntry
import moe.ouom.neriplayer.ui.LocalMiniPlayerHeight
import moe.ouom.neriplayer.ui.screen.playlist.DetailScreen
import moe.ouom.neriplayer.ui.viewmodel.playlist.NeteaseCollectionDetailUiState
import moe.ouom.neriplayer.ui.viewmodel.playlist.NeteaseCollectionHeader
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem
import moe.ouom.neriplayer.ui.viewmodel.tab.PlaylistSummary
import moe.ouom.neriplayer.ui.viewmodel.tab.YouTubeMusicPlaylist
import moe.ouom.neriplayer.ui.viewmodel.tab.favoriteId
import moe.ouom.neriplayer.util.HapticIconButton
import moe.ouom.neriplayer.util.fastScrollableImageRequest
import moe.ouom.neriplayer.util.formatPlayCount

@Parcelize
data class HomePlaylistGridItem(
    val id: Long,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val source: String,
    val fid: Long = 0L,
    val mid: Long = 0L,
    val browseId: String? = null,
    val playlistId: String? = null
) : Parcelable

@Composable
fun HomeSongCollectionDetailScreen(
    title: String,
    songs: List<SongItem>,
    onBack: () -> Unit,
    onSongClick: (List<SongItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val header = remember(title, songs, context) {
        NeteaseCollectionHeader(
            id = stableHomeSectionId(title, songs),
            isAlbum = false,
            name = title,
            coverUrl = songs.firstNotNullOfOrNull { song ->
                song.displayCoverUrl(context)?.takeIf { it.isNotBlank() }
            }.orEmpty(),
            playCount = 0L,
            trackCount = songs.size
        )
    }
    val ui = remember(header, songs) {
        NeteaseCollectionDetailUiState(
            loading = false,
            header = header,
            tracks = songs
        )
    }

    DetailScreen(
        ui = ui,
        playlistId = header.id,
        playlistSource = "homeSection",
        onRetry = {},
        onBack = onBack,
        onSongClick = onSongClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePlaylistGridScreen(
    title: String,
    playlists: List<HomePlaylistGridItem>,
    onBack: () -> Unit,
    onPlaylistClick: (HomePlaylistGridItem) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    HapticIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_section_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 20.dp + LocalMiniPlayerHeight.current
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    itemsIndexed(
                        items = playlists,
                        key = { index, item ->
                            "${item.source}:${item.id}:${item.browseId.orEmpty()}:$index"
                        }
                    ) { _, item ->
                        HomePlaylistGridCard(
                            item = item,
                            onClick = { onPlaylistClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePlaylistGridCard(
    item: HomePlaylistGridItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = fastScrollableImageRequest(context, item.coverUrl, sizePx = 384),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        HomePlaylistGridSubtitle(
            item = item,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
        )
    }
}

@Composable
private fun HomePlaylistGridSubtitle(
    item: HomePlaylistGridItem,
    modifier: Modifier = Modifier
) {
    val text = when {
        item.subtitle.isNotBlank() -> item.subtitle
        item.source.equals("netease", ignoreCase = true) && item.playCount > 0L -> {
            stringResource(
                R.string.home_play_count_format,
                formatPlayCount(LocalContext.current, item.playCount),
                item.trackCount
            )
        }
        item.source.equals("bili", ignoreCase = true) -> {
            pluralStringResource(
                R.plurals.bili_content_count,
                item.trackCount,
                item.trackCount
            )
        }
        item.trackCount > 0 -> {
            pluralStringResource(
                R.plurals.home_song_count_format,
                item.trackCount,
                item.trackCount
            )
        }
        else -> ""
    }
    if (text.isBlank()) {
        return
    }
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

internal fun PlaylistSummary.toHomePlaylistGridItem(): HomePlaylistGridItem {
    return HomePlaylistGridItem(
        id = id,
        title = name,
        coverUrl = picUrl,
        trackCount = trackCount,
        playCount = playCount,
        source = "netease"
    )
}

internal fun YouTubeMusicPlaylist.toHomePlaylistGridItem(): HomePlaylistGridItem {
    return HomePlaylistGridItem(
        id = favoriteId(),
        title = title,
        subtitle = subtitle,
        coverUrl = coverUrl,
        trackCount = trackCount,
        source = "youtubeMusic",
        browseId = browseId,
        playlistId = playlistId
    )
}

internal fun UsageEntry.toHomePlaylistGridItem(displayName: String = name): HomePlaylistGridItem {
    return HomePlaylistGridItem(
        id = id,
        title = displayName,
        coverUrl = picUrl.orEmpty(),
        trackCount = trackCount,
        source = source,
        fid = fid ?: 0L,
        mid = mid ?: 0L,
        browseId = browseId,
        playlistId = playlistId
    )
}

private fun stableHomeSectionId(title: String, songs: List<SongItem>): Long {
    var acc = 1125899906842597L
    title.forEach { acc = acc * 31 + it.code }
    songs.take(48).forEach { song ->
        acc = acc * 31 + song.id
        acc = acc * 31 + song.displayNameHash()
    }
    return acc and Long.MAX_VALUE
}

private fun SongItem.displayNameHash(): Int {
    return (customName ?: name).hashCode() * 31 + (customArtist ?: artist).hashCode()
}
