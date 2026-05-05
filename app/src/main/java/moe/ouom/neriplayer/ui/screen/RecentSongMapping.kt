package moe.ouom.neriplayer.ui.screen

import moe.ouom.neriplayer.data.history.PlayedEntry
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem

internal fun PlayedEntry.toSongItem(): SongItem = SongItem(
    id = id,
    name = name,
    artist = artist,
    albumId = albumId,
    album = album,
    durationMs = durationMs,
    coverUrl = coverUrl,
    mediaUri = localFilePath ?: mediaUri,
    matchedLyric = matchedLyric,
    matchedTranslatedLyric = matchedTranslatedLyric,
    customCoverUrl = customCoverUrl,
    customName = customName,
    customArtist = customArtist,
    originalName = originalName,
    originalArtist = originalArtist,
    originalCoverUrl = originalCoverUrl,
    originalLyric = originalLyric,
    originalTranslatedLyric = originalTranslatedLyric,
    localFileName = localFileName,
    localFilePath = localFilePath
)

internal fun List<PlayedEntry>.toSongItems(): List<SongItem> = map { it.toSongItem() }
