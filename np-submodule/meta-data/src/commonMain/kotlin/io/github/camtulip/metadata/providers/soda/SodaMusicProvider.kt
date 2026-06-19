package io.github.camtulip.metadata.providers.soda

import io.github.camtulip.metadata.core.AlbumMetadata
import io.github.camtulip.metadata.core.ArtistMetadata
import io.github.camtulip.metadata.core.Artwork
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
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class SodaMusicProvider(
    private val httpClient: HttpClient,
    private val endpoint: String = "https://api.qishui.com",
) : MetadataProvider {
    override val id: ProviderId = ProviderId("soda")

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        val response = classifiedProviderCall(id, "Soda Music search") {
            json.decodeFromString<SodaSearchResponse>(httpClient.get("$endpoint/luna/pc/search/track") {
                sodaHeaders()
                searchParameters(query)
            }.bodyAsText())
        }

        return response.resultGroups
            .orEmpty()
            .flatMap { it.data.orEmpty() }
            .filter { it.meta?.itemType == "track" }
            .mapNotNull { it.entity?.track?.toCandidate(id) }
            .take(query.normalizedLimit)
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        val response = fetchTrackDetail(id)
        val track = response.track ?: return null
        return track.toMetadata(this.id, response.lyric?.toLyricsMetadata(this.id, ProviderTrackId(track.id)))
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata {
        val response = fetchTrackDetail(id)
        return response.lyric.toLyricsMetadata(this.id, ProviderTrackId(response.track?.id ?: id.value))
    }

    private suspend fun fetchTrackDetail(id: ProviderTrackId): SodaTrackDetailResponse =
        classifiedProviderCall(this.id, "Soda Music get track") {
            json.decodeFromString<SodaTrackDetailResponse>(httpClient.get("$endpoint/luna/pc/track_v2") {
                sodaHeaders()
                detailParameters(id)
            }.bodyAsText())
        }

    private fun io.ktor.client.request.HttpRequestBuilder.searchParameters(query: TrackQuery) {
        parameter("aid", "386088")
        parameter("app_name", "")
        parameter("region", "")
        parameter("geo_region", "")
        parameter("os_region", "")
        parameter("sim_region", "")
        parameter("device_id", "")
        parameter("cdid", "")
        parameter("iid", "")
        parameter("version_name", "")
        parameter("version_code", "")
        parameter("channel", "")
        parameter("build_mode", "")
        parameter("network_carrier", "")
        parameter("ac", "")
        parameter("tz_name", "")
        parameter("resolution", "")
        parameter("device_platform", "")
        parameter("device_type", "")
        parameter("os_version", "")
        parameter("fp", "")
        parameter("q", query.displayText)
        parameter("cursor", "")
        parameter("search_id", "")
        parameter("search_method", "input")
        parameter("debug_params", "")
        parameter("from_search_id", "")
        parameter("search_scene", "")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.detailParameters(id: ProviderTrackId) {
        parameter("track_id", id.value)
        parameter("media_type", "track")
        parameter("queue_type", "")
        parameter("aid", "386088")
        parameter("device_platform", "web")
        parameter("channel", "pc_web")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.sodaHeaders() {
        header("User-Agent", USER_AGENT)
        header("Referer", "$endpoint/")
    }

    private companion object {
        const val USER_AGENT = "LunaPC/2.6.5(197449790)"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private fun SodaTrack.toCandidate(provider: ProviderId): TrackCandidate? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return TrackCandidate(
        provider = provider,
        id = ProviderTrackId(id),
        title = title,
        artists = artistNames(),
        album = album?.name,
        durationMs = duration,
        artwork = album?.coverUrl?.toArtwork(),
    )
}

private fun SodaTrack.toMetadata(provider: ProviderId, lyrics: LyricsMetadata?): TrackMetadata? {
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return TrackMetadata(
        provider = provider,
        id = ProviderTrackId(id),
        title = title,
        artists = artists.orEmpty().mapNotNull { artist ->
            artist.name?.takeIf { it.isNotBlank() }?.let { ArtistMetadata(name = it, providerId = artist.id) }
        },
        album = album?.name?.takeIf { it.isNotBlank() }?.let { name ->
            AlbumMetadata(title = name, providerId = album.id, artwork = album.coverUrl?.toArtwork())
        },
        durationMs = duration,
        artwork = album?.coverUrl?.toArtwork(),
        lyrics = lyrics,
    )
}

private fun SodaTrack.artistNames(): List<String> =
    artists.orEmpty().mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }.distinct()

private fun SodaTemplateUrl.toArtwork(): Artwork? {
    val base = urls?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val path = uri?.takeIf { it.isNotBlank() } ?: return null
    val template = templatePrefix?.takeIf { it.isNotBlank() }
    val url = buildString {
        append(base)
        append(path)
        if (template != null && base.contains("/img/")) {
            append('~')
            append(template)
            append("-resize:720:720.image")
        }
    }
    return Artwork(url = url, width = if (template == null) null else 720, height = if (template == null) null else 720)
}

private fun SodaLyricInfo?.toLyricsMetadata(provider: ProviderId, trackId: ProviderTrackId): LyricsMetadata {
    val lyric = this
    val content = lyric?.content
    val translation = lyric?.translationContents().orEmpty().firstOrNull { it.isUsefulLyrics() }
    val hasWordSynced = content.hasSodaWordTiming() || lyric?.type.equals("krc", ignoreCase = true)
    val hasLineSynced = hasWordSynced || content.hasSodaLineTiming()
    val hasPlain = content.isUsefulLyrics()
    val hasTranslation = translation.isUsefulLyrics()

    return LyricsMetadata(
        provider = provider,
        trackId = trackId,
        availableTypes = buildSet {
            if (hasPlain) add(LyricsType.Plain)
            if (hasLineSynced) add(LyricsType.LineSynced)
            if (hasWordSynced) add(LyricsType.WordSynced)
            if (hasTranslation) add(LyricsType.Translation)
        },
        hasTranslation = hasTranslation,
        syncPrecision = when {
            hasWordSynced -> SyncPrecision.WordSynced
            hasLineSynced -> SyncPrecision.LineSynced
            hasPlain -> SyncPrecision.Unsynced
            else -> SyncPrecision.Unknown
        },
        plainOrLineLyrics = content?.takeIf { it.isUsefulLyrics() },
        wordTimedLyrics = content?.takeIf { hasWordSynced && it.isUsefulLyrics() },
        translatedLyrics = translation?.takeIf { it.isUsefulLyrics() },
    )
}

private fun SodaLyricInfo.translationContents(): List<String> =
    translations.orEmpty().values.mapNotNull { it.translationContent() } +
        langTranslations.orEmpty().values.mapNotNull { it.content }

private fun JsonElement.translationContent(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["content"]?.jsonPrimitive?.contentOrNull
        else -> null
    }

private fun String?.isUsefulLyrics(): Boolean =
    !this.isNullOrBlank()

private fun String?.hasSodaLineTiming(): Boolean =
    this?.let { sodaLineRegex.containsMatchIn(it) || lrcLineRegex.containsMatchIn(it) } ?: false

private fun String?.hasSodaWordTiming(): Boolean =
    this?.let { sodaWordRegex.containsMatchIn(it) } ?: false

private val sodaLineRegex = Regex("""\[\d+,\d+]""")
private val sodaWordRegex = Regex("""<\d+,\d+,\d+>""")
private val lrcLineRegex = Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")
