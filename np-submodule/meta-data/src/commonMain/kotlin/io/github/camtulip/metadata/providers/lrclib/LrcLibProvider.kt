package io.github.camtulip.metadata.providers.lrclib

import io.github.camtulip.metadata.core.LyricsMetadata
import io.github.camtulip.metadata.core.LyricsType
import io.github.camtulip.metadata.core.MetadataProvider
import io.github.camtulip.metadata.core.ProviderId
import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision
import io.github.camtulip.metadata.core.TrackCandidate
import io.github.camtulip.metadata.core.TrackMetadata
import io.github.camtulip.metadata.core.TrackQuery
import io.github.camtulip.metadata.providers.util.classifiedProviderCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

class LrcLibProvider(
    private val httpClient: HttpClient,
    private val endpoint: String = "https://lrclib.net",
) : MetadataProvider {
    override val id: ProviderId = ProviderId("lrclib")

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        val response = classifiedProviderCall(id, "LRCLIB search") {
            httpClient.get("$endpoint/api/search") {
                header("User-Agent", USER_AGENT)
                parameter("track_name", query.title)
                query.artists.firstOrNull()?.let { parameter("artist_name", it) }
                query.album?.let { parameter("album_name", it) }
            }.body<List<LrcLibTrackResponse>>()
        }

        return response.map { it.toCandidate(id) }.take(query.normalizedLimit)
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        val response = getTrackResponse(id) ?: return null
        return response.toMetadata(this.id)
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata? {
        val response = getTrackResponse(id) ?: return null
        return response.toLyricsMetadata(this.id)
    }

    private suspend fun getTrackResponse(id: ProviderTrackId): LrcLibTrackResponse? =
        runCatching {
            classifiedProviderCall(this.id, "LRCLIB get track") {
            httpClient.get("$endpoint/api/get/${id.value}") {
                header("User-Agent", USER_AGENT)
            }.body<LrcLibTrackResponse>()
            }
        }.getOrNull()

    private companion object {
        const val USER_AGENT = "music-metadata-kmp/0.1.0 (https://github.com/neriplayer/music-metadata-kmp)"
    }
}

private fun LrcLibTrackResponse.toCandidate(provider: ProviderId): TrackCandidate =
    TrackCandidate(
        provider = provider,
        id = ProviderTrackId(id.toString()),
        title = trackName.ifBlank { name },
        artists = artistName.splitArtists(),
        album = albumName,
        durationMs = duration?.let { (it * 1000).toLong() },
        lyrics = toLyricsMetadata(provider),
    )

private fun LrcLibTrackResponse.toMetadata(provider: ProviderId): TrackMetadata =
    TrackMetadata(
        provider = provider,
        id = ProviderTrackId(id.toString()),
        title = trackName.ifBlank { name },
        artists = artistName.splitArtists().map { io.github.camtulip.metadata.core.ArtistMetadata(it) },
        album = albumName?.let { io.github.camtulip.metadata.core.AlbumMetadata(it) },
        durationMs = duration?.let { (it * 1000).toLong() },
        lyrics = toLyricsMetadata(provider),
    )

private fun LrcLibTrackResponse.toLyricsMetadata(provider: ProviderId): LyricsMetadata =
    LyricsMetadata(
        provider = provider,
        trackId = ProviderTrackId(id.toString()),
        availableTypes = buildSet {
            if (!plainLyrics.isNullOrBlank()) add(LyricsType.Plain)
            if (!syncedLyrics.isNullOrBlank()) add(LyricsType.LineSynced)
        },
        syncPrecision = when {
            !syncedLyrics.isNullOrBlank() -> SyncPrecision.LineSynced
            !plainLyrics.isNullOrBlank() -> SyncPrecision.Unsynced
            else -> SyncPrecision.Unknown
        },
        plainOrLineLyrics = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics?.takeIf { it.isNotBlank() },
    )

private fun String.splitArtists(): List<String> =
    split(Regex("""\s*(,|/|;|、|&|\band\b)\s*""", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
