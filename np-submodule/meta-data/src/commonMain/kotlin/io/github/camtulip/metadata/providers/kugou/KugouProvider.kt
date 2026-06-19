package io.github.camtulip.metadata.providers.kugou

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
import io.github.camtulip.metadata.providers.util.buildLyricsMetadata
import io.github.camtulip.metadata.providers.util.classifiedProviderCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class KugouProvider(
    private val httpClient: HttpClient,
    private val searchEndpoint: String = "https://songsearch.kugou.com",
    private val lyricsEndpoint: String = "https://lyrics.kugou.com",
    private val detailEndpoint: String = "https://m.kugou.com",
) : MetadataProvider {
    override val id: ProviderId = ProviderId("kugou")

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        val response = classifiedProviderCall(id, "Kugou search") {
            json.decodeFromString<KugouSearchResponse>(httpClient.get("$searchEndpoint/song_search_v2") {
                kugouHeaders()
                parameter("keyword", query.displayText)
                parameter("page", "1")
                parameter("pagesize", query.normalizedLimit.toString())
                parameter("platform", "WebFilter")
                parameter("tag", "em")
                parameter("filter", "2")
                parameter("iscorrection", "1")
                parameter("privilege_filter", "0")
            }.bodyAsText())
        }

        return response.data?.songs.orEmpty().mapNotNull { it.toCandidate(id) }.take(query.normalizedLimit)
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        val response = classifiedProviderCall(this.id, "Kugou get track") {
            json.decodeFromString<KugouSongInfoResponse>(httpClient.get("$detailEndpoint/app/i/getSongInfo.php") {
                kugouHeaders()
                parameter("cmd", "playInfo")
                parameter("hash", id.value)
            }.bodyAsText())
        }
        val title = response.songName?.takeIf { it.isNotBlank() }
            ?: response.fileName?.substringAfter(" - ", missingDelimiterValue = response.fileName)?.takeIf { it.isNotBlank() }
            ?: return null
        val providerTrackId = ProviderTrackId(response.hash?.takeIf { it.isNotBlank() } ?: id.value)

        return TrackMetadata(
            provider = this.id,
            id = providerTrackId,
            title = title,
            artists = response.artistNames().map { ArtistMetadata(it) },
            durationMs = response.durationMs(),
            artwork = (response.albumImageUrl ?: response.imageUrl)?.toArtwork(),
            lyrics = runCatching { getLyricsMetadata(providerTrackId) }.getOrNull(),
        )
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata {
        val response = fetchLyricsCandidates(id)
        val downloadedLyrics = fetchPreferredLyrics(response)
        return response.toLyricsMetadata(this.id, id, downloadedLyrics)
    }

    private suspend fun fetchLyricsCandidates(id: ProviderTrackId): KugouLyricsSearchResponse =
        classifiedProviderCall(this.id, "Kugou get lyrics metadata") {
            json.decodeFromString<KugouLyricsSearchResponse>(httpClient.get("$lyricsEndpoint/search") {
                kugouHeaders()
                parameter("ver", "1")
                parameter("man", "yes")
                parameter("client", "pc")
                parameter("keyword", "")
                parameter("hash", id.value)
            }.bodyAsText())
        }

    private suspend fun fetchPreferredLyrics(response: KugouLyricsSearchResponse): KugouDownloadedLyrics? {
        val candidates = response.validCandidates()
        if (candidates.isEmpty()) return null

        val krcCandidate = candidates.firstOrNull { it.hasKrcCapability() }
        val krcLyrics = krcCandidate
            ?.let { candidate -> runCatching { fetchLyricsDownload(candidate, KUGOU_KRC_FORMAT) }.getOrNull() }
            ?.content
            ?.let(::decodeKugouKrcBase64)
            ?.takeIf { it.isNotBlank() }

        if (krcLyrics != null) {
            return KugouDownloadedLyrics(
                wordTimedLyrics = krcLyrics,
                translatedLyrics = krcLyrics.extractKugouKrcTranslations(),
            )
        }

        val lrcCandidate = krcCandidate ?: candidates.first()
        val lrcLyrics = runCatching { fetchLyricsDownload(lrcCandidate, KUGOU_LRC_FORMAT) }.getOrNull()
            ?.content
            ?.let(::decodeKugouLrcBase64)
            ?.takeIf { it.isNotBlank() }

        return lrcLyrics?.let { KugouDownloadedLyrics(plainOrLineLyrics = it) }
    }

    private suspend fun fetchLyricsDownload(
        candidate: KugouLyricsCandidate,
        format: String,
    ): KugouLyricsDownloadResponse =
        classifiedProviderCall(this.id, "Kugou download $format lyrics") {
            json.decodeFromString<KugouLyricsDownloadResponse>(httpClient.get("$lyricsEndpoint/download") {
                kugouHeaders()
                parameter("ver", "1")
                parameter("client", "pc")
                parameter("id", candidate.id)
                parameter("accesskey", candidate.accesskey)
                parameter("fmt", format)
                parameter("charset", "utf8")
            }.bodyAsText())
        }

    private fun io.ktor.client.request.HttpRequestBuilder.kugouHeaders() {
        header("User-Agent", USER_AGENT)
        header("Referer", "https://www.kugou.com/")
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private val KugouSearchData.songs: List<KugouSongSummary>
    get() = lists ?: info ?: emptyList()

private fun KugouSongSummary.toCandidate(provider: ProviderId): TrackCandidate? {
    val trackHash = trackHash() ?: return null

    return TrackCandidate(
        provider = provider,
        id = ProviderTrackId(trackHash),
        title = firstCleanText(
            songNameOriginal,
            songname,
            songName,
            filename?.substringAfter(" - "),
            fileName?.substringAfter(" - "),
        ) ?: trackHash,
        artists = artistNames(),
        album = firstCleanText(albumName, webAlbumName),
        durationMs = (duration ?: highQualityDuration?.takeIf { it > 0 } ?: webDuration)?.times(1000),
    )
}

private fun KugouSongSummary.trackHash(): String? =
    firstCleanText(hash, fileHash)

private fun KugouSongSummary.artistNames(): List<String> =
    firstCleanText(
        singername,
        singerName,
        filename?.substringBefore(" - ", missingDelimiterValue = ""),
        fileName?.substringBefore(" - ", missingDelimiterValue = ""),
    )?.splitArtists()
        ?: emptyList()

private fun KugouSongInfoResponse.artistNames(): List<String> =
    (authorName ?: singerName ?: fileName?.substringBefore(" - ", missingDelimiterValue = ""))
        .splitArtists()

private fun String?.splitArtists(): List<String> =
    this
        .cleanKugouText()
        ?.split(Regex("""\s*(,|/|;|、|&|\+|×| x |\band\b| with )\s*""", RegexOption.IGNORE_CASE))
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?: emptyList()

private fun firstCleanText(vararg values: String?): String? =
    values.firstNotNullOfOrNull { it.cleanKugouText()?.takeIf(String::isNotBlank) }

private fun String?.cleanKugouText(): String? =
    this
        ?.replace(Regex("""</?em>""", RegexOption.IGNORE_CASE), "")
        ?.replace("&amp;", "&")
        ?.trim()

private fun String.toArtwork(): Artwork =
    toKugouArtworkUrl().let { artworkUrl ->
        Artwork(
            url = artworkUrl,
            width = KUGOU_ARTWORK_SIZE,
            height = KUGOU_ARTWORK_SIZE,
        )
    }

private fun String.toKugouArtworkUrl(): String {
    val secureUrl = replace("http://", "https://")
    return if (secureUrl.contains("{size}")) {
        secureUrl.replace("{size}", KUGOU_ARTWORK_SIZE.toString())
    } else {
        kugouSizedArtworkPathRegex.replace(secureUrl) { match ->
            "/${match.groupValues[1]}/$KUGOU_ARTWORK_SIZE/"
        }
    }
}

private const val KUGOU_ARTWORK_SIZE = 1000
private val kugouSizedArtworkPathRegex = Regex("""/(stdmusic|uploadpic)/\d+/""")

private fun KugouSongInfoResponse.durationMs(): Long? =
    timeLength?.takeIf { it > 0 }?.times(1000)
        ?: extra?.standardTimeLength?.takeIf { it > 0 }
        ?: extra?.highTimeLength?.takeIf { it > 0 }
        ?: extra?.losslessTimeLength?.takeIf { it > 0 }
        ?: extra?.superQualityTimeLength?.takeIf { it > 0 }

private fun KugouLyricsSearchResponse.toLyricsMetadata(
    provider: ProviderId,
    trackId: ProviderTrackId,
    downloadedLyrics: KugouDownloadedLyrics?,
): LyricsMetadata {
    val capabilities = capabilityTypes()
    if (!downloadedLyrics?.wordTimedLyrics.isNullOrBlank() || !downloadedLyrics?.plainOrLineLyrics.isNullOrBlank()) {
        val contentMetadata = buildLyricsMetadata(
            provider = provider,
            trackId = trackId,
            plainOrLineLyrics = downloadedLyrics?.plainOrLineLyrics,
            wordTimedLyrics = downloadedLyrics?.wordTimedLyrics,
            translatedLyrics = downloadedLyrics?.translatedLyrics,
        )
        return contentMetadata.copy(
            availableTypes = contentMetadata.availableTypes + capabilities,
        )
    }

    return LyricsMetadata(
        provider = provider,
        trackId = trackId,
        availableTypes = capabilities,
        syncPrecision = SyncPrecision.Unknown,
    )
}

private fun KugouLyricsSearchResponse.validCandidates(): List<KugouLyricsCandidate> =
    candidates.orEmpty().filter { !it.id.isNullOrBlank() && !it.accesskey.isNullOrBlank() }

private fun KugouLyricsSearchResponse.capabilityTypes(): Set<LyricsType> {
    val candidates = validCandidates()
    return buildSet {
        if (candidates.isNotEmpty()) {
            add(LyricsType.Plain)
            add(LyricsType.LineSynced)
        }
        if (candidates.any { it.hasKrcCapability() }) add(LyricsType.WordSynced)
    }
}

private fun KugouLyricsCandidate.hasKrcCapability(): Boolean =
    (krctype ?: 0) > 0 || (contentFormat ?: 0) > 1

private data class KugouDownloadedLyrics(
    val wordTimedLyrics: String? = null,
    val plainOrLineLyrics: String? = null,
    val translatedLyrics: String? = null,
)

@OptIn(ExperimentalEncodingApi::class)
internal fun String.extractKugouKrcTranslations(): String? {
    val languageHeader = lineSequence()
        .firstOrNull { it.trim().startsWith(KUGOU_LANGUAGE_TAG_START) }
        ?: return null
    val contentBase64 = languageHeader
        .substringAfter(KUGOU_LANGUAGE_TAG_START)
        .substringBeforeLast("]")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return null

    val translations = runCatching {
        val json = Base64.decode(contentBase64).decodeToString()
        Json.parseToJsonElement(json)
            .jsonObject["content"]
            ?.jsonArray
            .orEmpty()
            .filter { contentItem ->
                contentItem.jsonObject["type"]?.jsonPrimitive?.intOrNull == KUGOU_TRANSLATION_CONTENT_TYPE
            }
            .flatMap { contentItem ->
                contentItem.jsonObject["lyricContent"]
                    ?.jsonArray
                    .orEmpty()
                    .mapNotNull { row ->
                        row.jsonArray.joinToString("") { part -> part.jsonPrimitive.content }
                            .trim()
                            .takeIf { it.isNotBlank() }
                    }
            }
    }.getOrNull().orEmpty()

    return translations.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private const val KUGOU_KRC_FORMAT = "krc"
private const val KUGOU_LRC_FORMAT = "lrc"
private const val KUGOU_LANGUAGE_TAG_START = "[language:"
private const val KUGOU_TRANSLATION_CONTENT_TYPE = 1
