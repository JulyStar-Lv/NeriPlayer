package moe.ouom.neriplayer.core.player.metadata

import moe.ouom.neriplayer.core.api.search.MetadataSearchQuery
import moe.ouom.neriplayer.core.api.search.MusicPlatform
import moe.ouom.neriplayer.listentogether.ListenTogetherChannels
import moe.ouom.neriplayer.ui.viewmodel.playlist.SongItem

private val biliPartPrefixRegex = Regex("^\\d+\\.\\s*")
private val biliSpacedTitleSeparatorRegex = Regex("\\s+[-\\u2013\\u2014]\\s+")
private val biliCompactTitleSeparatorRegex = Regex("[-\\u2013\\u2014]")
private val cjkTextRegex = Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")

internal fun SongItem.isBiliMetadataSource(biliSourceTag: String): Boolean =
    channelId.equals(ListenTogetherChannels.BILIBILI, ignoreCase = true) ||
        album.startsWith(biliSourceTag)

internal fun SongItem.toMetadataSearchQueries(isBiliSong: Boolean): List<MetadataSearchQuery> {
    val searchDurationMs = durationMs.takeIf { it > 0L }
    return if (isBiliSong) {
        buildBiliMetadataSearchQueries(title = name, durationMs = searchDurationMs)
    } else {
        listOf(
            MetadataSearchQuery(
                songName = name,
                songArtist = artist,
                durationMs = searchDurationMs
            )
        )
    }
}

internal fun buildBiliMetadataSearchQueries(
    title: String,
    durationMs: Long?
): List<MetadataSearchQuery> {
    val normalizedTitle = title.trim()
        .replace(biliPartPrefixRegex, "")
        .trim()
    if (normalizedTitle.isBlank()) return emptyList()

    val queries = mutableListOf<MetadataSearchQuery>()
    splitBiliTitleArtistPair(normalizedTitle)?.let { (left, right) ->
        queries += MetadataSearchQuery(songName = right, songArtist = left, durationMs = durationMs)
        queries += MetadataSearchQuery(songName = left, songArtist = right, durationMs = durationMs)
    }
    queries += MetadataSearchQuery(songName = normalizedTitle, songArtist = "", durationMs = durationMs)
    return queries.distinct()
}

private fun splitBiliTitleArtistPair(title: String): Pair<String, String>? {
    splitTitleBySingleSeparator(title, biliSpacedTitleSeparatorRegex)?.let { return it }

    val compact = splitTitleBySingleSeparator(title, biliCompactTitleSeparatorRegex) ?: return null
    return compact.takeIf { (left, right) ->
        cjkTextRegex.containsMatchIn(left) || cjkTextRegex.containsMatchIn(right)
    }
}

private fun splitTitleBySingleSeparator(
    title: String,
    separatorRegex: Regex
): Pair<String, String>? {
    val separators = separatorRegex.findAll(title).toList()
    val separator = separators.singleOrNull() ?: return null
    val left = title.substring(0, separator.range.first).trim()
    val right = title.substring(separator.range.last + 1).trim()
    return if (left.isBlank() || right.isBlank()) null else left to right
}

internal fun SongItem.withUpdatedLyricsPreservingOriginal(
    newLyrics: String? = matchedLyric,
    newTranslatedLyric: String? = matchedTranslatedLyric
): SongItem {
    return copy(
        matchedLyric = newLyrics,
        matchedTranslatedLyric = newTranslatedLyric,
        originalLyric = originalLyric ?: matchedLyric,
        originalTranslatedLyric = originalTranslatedLyric ?: matchedTranslatedLyric
    )
}

internal fun shouldAutoMatchExternalMetadata(
    song: SongItem,
    isLocalSong: Boolean,
    isBiliSong: Boolean
): Boolean {
    if (isLocalSong || isBiliSong) return false
    if (song.matchedSongId != null || !song.matchedLyric.isNullOrEmpty()) return false
    return song.customName == null && song.customArtist == null && song.customCoverUrl == null
}

internal fun shouldAutoSearchMetadataForMissingLyrics(
    song: SongItem,
    isLocalSong: Boolean,
    isBiliSong: Boolean
): Boolean {
    if (!isLocalSong && !isBiliSong) return false
    if (song.matchedLyric != null || song.originalLyric != null) return false
    return song.customName == null && song.customArtist == null && song.customCoverUrl == null
}

internal fun normalizeCustomMetadataValue(
    desiredValue: String?,
    baseValue: String?
): String? {
    val normalizedDesired = desiredValue?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return normalizedDesired.takeIf { it != baseValue }
}

internal fun applyManualSearchMetadata(
    originalSong: SongItem,
    songName: String,
    singer: String,
    coverUrl: String?,
    album: String?,
    lyric: String?,
    translatedLyric: String?,
    matchedSource: MusicPlatform,
    matchedSongId: String,
    useCustomOverride: Boolean
): SongItem {
    val originalName = originalSong.originalName ?: originalSong.name
    val originalArtist = originalSong.originalArtist ?: originalSong.artist
    val originalCoverUrl = originalSong.originalCoverUrl ?: originalSong.coverUrl

    return if (useCustomOverride) {
        originalSong.copy(
            matchedLyric = lyric,
            matchedTranslatedLyric = translatedLyric,
            matchedLyricSource = matchedSource,
            matchedSongId = matchedSongId,
            matchedAlbum = normalizeCustomMetadataValue(album, originalSong.album),
            customCoverUrl = normalizeCustomMetadataValue(coverUrl, originalSong.coverUrl),
            customName = normalizeCustomMetadataValue(songName, originalSong.name),
            customArtist = normalizeCustomMetadataValue(singer, originalSong.artist),
            originalName = originalName,
            originalArtist = originalArtist,
            originalCoverUrl = originalCoverUrl,
            originalLyric = originalSong.originalLyric ?: originalSong.matchedLyric,
            originalTranslatedLyric = originalSong.originalTranslatedLyric ?: originalSong.matchedTranslatedLyric
        )
    } else {
        originalSong.copy(
            name = songName,
            artist = singer,
            coverUrl = coverUrl,
            matchedLyric = lyric,
            matchedTranslatedLyric = translatedLyric,
            matchedLyricSource = matchedSource,
            matchedSongId = matchedSongId,
            matchedAlbum = normalizeCustomMetadataValue(album, originalSong.album),
            customCoverUrl = null,
            customName = null,
            customArtist = null,
            originalName = originalName,
            originalArtist = originalArtist,
            originalCoverUrl = originalCoverUrl,
            originalLyric = originalSong.originalLyric ?: originalSong.matchedLyric,
            originalTranslatedLyric = originalSong.originalTranslatedLyric ?: originalSong.matchedTranslatedLyric
        )
    }
}

internal fun applyAutoSearchMetadata(
    originalSong: SongItem,
    songName: String,
    singer: String,
    coverUrl: String?,
    album: String?,
    lyric: String?,
    translatedLyric: String?,
    matchedSource: MusicPlatform,
    matchedSongId: String
): SongItem {
    return originalSong.withUpdatedLyricsPreservingOriginal(
        newLyrics = lyric ?: originalSong.matchedLyric,
        newTranslatedLyric = translatedLyric ?: originalSong.matchedTranslatedLyric
    ).copy(
        name = songName.trim().takeIf { it.isNotBlank() } ?: originalSong.name,
        artist = singer.trim().takeIf { it.isNotBlank() } ?: originalSong.artist,
        coverUrl = coverUrl ?: originalSong.coverUrl,
        matchedLyricSource = matchedSource,
        matchedSongId = matchedSongId,
        matchedAlbum = normalizeCustomMetadataValue(album, originalSong.album),
        originalName = originalSong.originalName ?: originalSong.name,
        originalArtist = originalSong.originalArtist ?: originalSong.artist,
        originalCoverUrl = originalSong.originalCoverUrl ?: originalSong.coverUrl
    )
}
