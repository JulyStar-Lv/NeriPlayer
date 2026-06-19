package io.github.camtulip.metadata.providers.netease

import io.github.camtulip.metadata.core.AlbumMetadata
import io.github.camtulip.metadata.core.ArtistMetadata
import io.github.camtulip.metadata.core.Artwork
import io.github.camtulip.metadata.core.LyricsMetadata
import io.github.camtulip.metadata.core.MetadataProvider
import io.github.camtulip.metadata.core.ProviderId
import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.TrackCandidate
import io.github.camtulip.metadata.core.TrackMetadata
import io.github.camtulip.metadata.core.TrackQuery
import io.github.camtulip.metadata.providers.util.buildLyricsMetadata
import io.github.camtulip.metadata.providers.util.classifiedProviderCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class NeteaseProvider(
    private val httpClient: HttpClient,
    private val endpoint: String = "https://music.163.com",
) : MetadataProvider {
    override val id: ProviderId = ProviderId("netease")

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        val response = classifiedProviderCall(id, "NetEase search") {
            json.decodeFromString<NeteaseSearchResponse>(httpClient.get("$endpoint/api/search/get/web") {
                neteaseHeaders()
                parameter("csrf_token", "")
                parameter("hlpretag", "")
                parameter("hlposttag", "")
                parameter("s", query.displayText)
                parameter("type", "1")
                parameter("offset", "0")
                parameter("total", "true")
                parameter("limit", query.normalizedLimit.toString())
            }.bodyAsText())
        }

        return response.result?.songs.orEmpty().map { it.toCandidate(id) }.take(query.normalizedLimit)
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        val response = classifiedProviderCall(this.id, "NetEase get track") {
            json.decodeFromString<NeteaseSongDetailResponse>(httpClient.get("$endpoint/api/song/detail/") {
                neteaseHeaders()
                parameter("id", id.value)
                parameter("ids", "[${id.value}]")
            }.bodyAsText())
        }

        val song = response.songs?.firstOrNull() ?: return null
        return song.toMetadata(this.id, getLyricsMetadata(id))
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata {
        val response = fetchLyrics(id)
        return response.toLyricsMetadata(this.id, id)
    }

    private suspend fun fetchLyrics(id: ProviderTrackId): NeteaseLyricResponse =
        classifiedProviderCall(this.id, "NetEase get lyrics") {
            json.decodeFromString<NeteaseLyricResponse>(httpClient.get("$endpoint/api/song/lyric") {
                neteaseHeaders()
                parameter("id", id.value)
                parameter("lv", "-1")
                parameter("tv", "-1")
                parameter("rv", "-1")
                parameter("yv", "-1")
                parameter("ytv", "-1")
                parameter("yrv", "-1")
            }.bodyAsText())
        }

    private fun io.ktor.client.request.HttpRequestBuilder.neteaseHeaders() {
        header("User-Agent", USER_AGENT)
        header("Referer", "$endpoint/")
        header("Cookie", "os=pc; appver=8.9.70;")
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private fun NeteaseSong.toCandidate(provider: ProviderId): TrackCandidate =
    TrackCandidate(
        provider = provider,
        id = ProviderTrackId(id.toString()),
        title = name,
        artists = normalizedArtists().map { it.name },
        album = normalizedAlbum()?.name,
        durationMs = duration ?: durationAlt,
        artwork = normalizedAlbum()?.artwork(),
    )

private fun NeteaseSong.toMetadata(provider: ProviderId, lyrics: LyricsMetadata?): TrackMetadata =
    TrackMetadata(
        provider = provider,
        id = ProviderTrackId(id.toString()),
        title = name,
        artists = normalizedArtists().map { ArtistMetadata(name = it.name, providerId = it.id?.toString()) },
        album = normalizedAlbum()?.let { album ->
            AlbumMetadata(
                title = album.name,
                providerId = album.id?.toString(),
                artwork = album.artwork(),
            )
        },
        durationMs = duration ?: durationAlt,
        artwork = normalizedAlbum()?.artwork(),
        lyrics = lyrics,
    )

private fun NeteaseSong.normalizedArtists(): List<NeteaseArtist> =
    artists ?: artistsAlt ?: emptyList()

private fun NeteaseSong.normalizedAlbum(): NeteaseAlbum? =
    album ?: albumAlt

private fun NeteaseAlbum.artwork(): Artwork? =
    (picUrl ?: blurPicUrl)
        ?.takeIf { it.isNotBlank() }
        ?.let { url ->
            Artwork(
                url = url.normalizedNeteaseArtworkUrl(),
                width = 800,
                height = 800,
            )
        }

private fun String.normalizedNeteaseArtworkUrl(): String {
    val httpsUrl = trim().replace("http://", "https://")
    if (!httpsUrl.contains("music.126.net") || httpsUrl.contains("param=")) {
        return httpsUrl
    }
    val separator = if (httpsUrl.contains("?")) "&" else "?"
    return "$httpsUrl${separator}param=800y800"
}

private fun NeteaseLyricResponse.toLyricsMetadata(provider: ProviderId, trackId: ProviderTrackId): LyricsMetadata =
    buildLyricsMetadata(
        provider = provider,
        trackId = trackId,
        plainOrLineLyrics = lrc?.lyric,
        wordTimedLyrics = yrc?.lyric,
        translatedLyrics = ytlrc?.lyric ?: tlyric?.lyric,
        romanizedLyrics = romalrc?.lyric,
    )
