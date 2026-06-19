package io.github.camtulip.metadata.providers.qq

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
import io.github.camtulip.metadata.core.TrackTextNormalizer
import io.github.camtulip.metadata.providers.util.buildLyricsMetadata
import io.github.camtulip.metadata.providers.util.classifiedProviderCall
import io.github.camtulip.metadata.providers.util.isUsefulLyrics
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class QQMusicProvider(
    private val httpClient: HttpClient,
    private val searchEndpoint: String = "https://c.y.qq.com",
    private val detailEndpoint: String = "https://u.y.qq.com",
) : MetadataProvider {
    override val id: ProviderId = ProviderId("qq")

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        val candidates = mutableListOf<TrackCandidate>()
        for (searchText in query.qqSearchTexts()) {
            candidates += searchTrack(searchText, query.normalizedLimit)
            if (candidates.hasLikelyArtistMatch(query)) {
                break
            }
        }
        return candidates
            .distinctBy { it.id }
            .take(query.normalizedLimit * QQ_SEARCH_VARIANT_LIMIT)
    }

    private suspend fun searchTrack(searchText: String, limit: Int): List<TrackCandidate> {
        val response = classifiedProviderCall(id, "QQ Music search") {
            json.decodeFromString<QQSearchResponse>(httpClient.get("$searchEndpoint/soso/fcgi-bin/client_search_cp") {
                qqHeaders()
                parameter("format", "json")
                parameter("n", limit.toString())
                parameter("p", "1")
                parameter("w", searchText)
                parameter("cr", "1")
                parameter("g_tk", "5381")
            }.bodyAsText())
        }

        return response.data?.song?.list.orEmpty().map { it.toCandidate(id) }.take(limit)
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        val requestedIdentifier = id.toQQTrackIdentifier()
        val response = classifiedProviderCall(this.id, "QQ Music get track") {
            json.decodeFromString<QQDetailRoot>(httpClient.get("$detailEndpoint/cgi-bin/musicu.fcg") {
                qqHeaders()
                parameter("data", detailRequestData(requestedIdentifier))
            }.bodyAsText())
        }

        val track = response.songInfo?.data?.trackInfo ?: return null
        val resolvedId = ProviderTrackId(track.mid.toQQTrackId(track.id ?: requestedIdentifier.songId))
        return track.toMetadata(this.id, getLyricsMetadata(resolvedId))
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata {
        val identifier = id.toQQTrackIdentifier()
        val response = identifier.songMid?.let { songMid ->
            classifiedProviderCall(this.id, "QQ Music get lyrics") {
                json.decodeFromString<QQLyricResponse>(httpClient.get("$searchEndpoint/lyric/fcgi-bin/fcg_query_lyric_new.fcg") {
                    qqHeaders()
                    parameter("songmid", songMid)
                    parameter("format", "json")
                    parameter("inCharset", "utf8")
                    parameter("outCharset", "utf-8")
                    parameter("nobase64", "1")
                    parameter("g_tk", "5381")
                }.bodyAsText())
            }
        }
        val wordTimedLyrics = runCatching { getQrcLyrics(id) }.getOrNull()

        return buildLyricsMetadata(
            provider = this.id,
            trackId = id,
            plainOrLineLyrics = response?.lyric.decodeUsefulLyricPayload(),
            wordTimedLyrics = wordTimedLyrics?.qrc,
            translatedLyrics = response?.trans.decodeUsefulLyricPayload() ?: wordTimedLyrics?.translatedLyrics,
            romanizedLyrics = wordTimedLyrics?.romanizedLyrics,
        )
    }

    suspend fun getQrcLyrics(id: ProviderTrackId): QQMusicQrcLyrics? {
        val identifier = resolveQQTrackIdentifier(id) ?: return null
        val songId = identifier.songId ?: return null
        val response = classifiedProviderCall(this.id, "QQ Music get QRC lyrics") {
            json.decodeFromString<QQPlayLyricRoot>(httpClient.post("$detailEndpoint/cgi-bin/musicu.fcg?pcachetime=1675229492") {
                qqHeaders()
                contentType(ContentType.Application.Json)
                setBody(qrcRequestData(songId))
            }.bodyAsText())
        }
        val payload = response.playLyricInfo?.data?.preferredWordTimedPayload() ?: return null
        val trackId = ProviderTrackId((identifier.songMid ?: id.value).toQQTrackId(songId))
        return QQMusicQrcLyrics(
            provider = this.id,
            trackId = trackId,
            songId = songId,
            rawXml = payload.rawXml,
            qrc = payload.content,
            translatedLyrics = response.playLyricInfo?.data?.qqTranslatedLyrics(),
            romanizedLyrics = response.playLyricInfo?.data?.qqRomanizedLyrics(),
            lineCount = payload.content.countQrcLines(),
            preview = payload.content.toQrcPreview(),
            source = payload.source,
        )
    }

    private suspend fun resolveQQTrackIdentifier(id: ProviderTrackId): QQTrackIdentifier? {
        val identifier = id.toQQTrackIdentifier()
        if (identifier.songId != null) return identifier
        val songMid = identifier.songMid ?: return null
        val response = classifiedProviderCall(this.id, "QQ Music resolve QRC song id") {
            json.decodeFromString<QQDetailRoot>(httpClient.get("$detailEndpoint/cgi-bin/musicu.fcg") {
                qqHeaders()
                parameter("data", detailRequestData(identifier))
            }.bodyAsText())
        }
        val track = response.songInfo?.data?.trackInfo ?: return null
        return QQTrackIdentifier(
            songMid = track.mid.takeIf { it.isNotBlank() } ?: songMid,
            songId = track.id,
        )
    }

    private fun detailRequestData(identifier: QQTrackIdentifier): String =
        json.encodeToString(
            buildJsonObject {
                put(
                    "songinfo",
                    buildJsonObject {
                        put("method", "get_song_detail_yqq")
                        put("module", "music.pf_song_detail_svr")
                        put(
                            "param",
                            buildJsonObject {
                                if (!identifier.songMid.isNullOrBlank()) {
                                    put("song_mid", identifier.songMid)
                                } else if (identifier.songId != null) {
                                    put("song_id", identifier.songId)
                                }
                            },
                        )
                    },
                )
            },
        )

    private fun qrcRequestData(songId: Long): String =
        json.encodeToString(
            buildJsonObject {
                put(
                    "music.musichallSong.PlayLyricInfo.GetPlayLyricInfo",
                    buildJsonObject {
                        put("method", "GetPlayLyricInfo")
                        put("module", "music.musichallSong.PlayLyricInfo")
                        put(
                            "param",
                            buildJsonObject {
                                put("crypt", 1)
                                put("ct", 19)
                                put("cv", 2111)
                                put("lrc_t", 0)
                                put("qrc", 1)
                                put("qrc_t", 0)
                                put("qyc", 1)
                                put("roma", 1)
                                put("roma_t", 0)
                                put("songID", songId)
                                put("trans", 1)
                                put("trans_t", 0)
                                put("type", 0)
                            },
                        )
                    },
                )
            },
        )

    private fun io.ktor.client.request.HttpRequestBuilder.qqHeaders() {
        header("User-Agent", USER_AGENT)
        header("Referer", "https://y.qq.com/")
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private fun TrackQuery.qqSearchTexts(): List<String> {
    if (artists.isEmpty()) {
        return listOf(displayText)
    }

    val compactArtists = artists.joinToString(" ").trim()
    return listOf(
        displayText,
        "$title $compactArtists",
        title,
    ).map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun List<TrackCandidate>.hasLikelyArtistMatch(query: TrackQuery): Boolean {
    if (query.artists.isEmpty()) {
        return isNotEmpty()
    }
    return any { candidate ->
        query.artists.any { queryArtist ->
            candidate.artists.any { candidateArtist -> queryArtist.isLikelySameArtist(candidateArtist) }
        }
    }
}

private fun String.isLikelySameArtist(other: String): Boolean {
    val left = TrackTextNormalizer.normalizeArtist(this)
    val right = TrackTextNormalizer.normalizeArtist(other)
    if (left.isBlank() || right.isBlank()) return false
    if (left == right) return true
    val distance = levenshtein(left, right)
    val maxLength = maxOf(left.length, right.length)
    return maxLength >= MIN_FUZZY_ARTIST_LENGTH && distance.toDouble() / maxLength.toDouble() <= FUZZY_ARTIST_DISTANCE_RATIO
}

data class QQMusicQrcLyrics(
    val provider: ProviderId,
    val trackId: ProviderTrackId,
    val songId: Long,
    val rawXml: String,
    val qrc: String,
    val translatedLyrics: String? = null,
    val romanizedLyrics: String? = null,
    val lineCount: Int,
    val preview: String,
    val source: QQMusicWordLyricSource = QQMusicWordLyricSource.QRC,
)

enum class QQMusicWordLyricSource {
    QYC,
    QRC,
}

private data class QQWordTimedPayload(
    val source: QQMusicWordLyricSource,
    val rawXml: String,
    val content: String,
)

private fun QQSongSummary.toCandidate(provider: ProviderId): TrackCandidate =
    TrackCandidate(
        provider = provider,
        id = ProviderTrackId(songMid.toQQTrackId(songId)),
        title = songName,
        artists = singer.map { it.name },
        album = albumName,
        durationMs = interval?.times(1000),
        artwork = albumMid?.toAlbumArtwork(),
    )

private fun QQTrackInfo.toMetadata(provider: ProviderId, lyrics: LyricsMetadata?): TrackMetadata =
    TrackMetadata(
        provider = provider,
        id = ProviderTrackId(mid),
        title = title?.takeIf { it.isNotBlank() } ?: name,
        artists = singer.map { ArtistMetadata(name = it.name, providerId = it.mid ?: it.id?.toString()) },
        album = album?.let { album ->
            AlbumMetadata(
                title = album.title?.takeIf { it.isNotBlank() } ?: album.name,
                providerId = album.mid ?: album.id?.toString(),
                artwork = album.mid?.toAlbumArtwork(),
            )
        },
        durationMs = interval?.times(1000),
        artwork = album?.mid?.toAlbumArtwork(),
        lyrics = lyrics,
    )

private fun String.toAlbumArtwork(): Artwork =
    Artwork(url = "https://y.qq.com/music/photo_new/T002R800x800M000$this.jpg", width = 800, height = 800)

private data class QQTrackIdentifier(
    val songMid: String?,
    val songId: Long?,
)

private fun ProviderTrackId.toQQTrackIdentifier(): QQTrackIdentifier {
    val raw = value.trim()
    val parts = raw.split(QQ_TRACK_ID_SEPARATOR, limit = 2)
    val songMid = parts.firstOrNull()?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
    val songId = parts.getOrNull(1)?.toLongOrNull() ?: raw.takeIf { it.all(Char::isDigit) }?.toLongOrNull()
    return QQTrackIdentifier(songMid = songMid, songId = songId)
}

private fun String.toQQTrackId(songId: Long?): String =
    if (songId == null) this else "$this$QQ_TRACK_ID_SEPARATOR$songId"

private fun String?.decodeLyricPayload(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return value
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&#10;", "\n")
        .replace("&#xA;", "\n")
        .replace("&#13;", "\r")
        .replace("&#xD;", "\r")
}

private fun String?.decodeUsefulLyricPayload(): String? =
    decodeLyricPayload()?.takeIf { it.isUsefulLyrics() }

private fun QQPlayLyricData.preferredWordTimedPayload(): QQWordTimedPayload? {
    val candidates = listOf(
        QQMusicWordLyricSource.QYC to qyc.stringContentOrNull(),
        QQMusicWordLyricSource.QRC to lyric.takeIfLyricTimeAvailable(qrcLyricTime()),
    )

    for ((source, value) in candidates) {
        val rawXml = value.decodeQqWordTimedPayload() ?: continue
        val content = rawXml.extractQrcLyricContent().takeIf { it.isNotBlank() } ?: continue
        return QQWordTimedPayload(
            source = source,
            rawXml = rawXml,
            content = content,
        )
    }
    return null
}

private fun String?.decodeQqWordTimedPayload(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return if (value.isHexPayload()) {
        decodeQqQrcHex(value)
    } else {
        value
    }
}

private fun String?.decodeQqAuxiliaryLyricPayload(): String? {
    val value = decodeQqWordTimedPayload() ?: return null
    return value.extractQrcLyricContent()
        .ifBlank { value.decodeUsefulLyricPayload().orEmpty() }
        .takeIf { it.isUsefulLyrics() }
}

private fun QQPlayLyricData.qqTranslatedLyrics(): String? =
    trans.takeIfLyricTimeAvailable(transTime).decodeQqAuxiliaryLyricPayload()
        ?: lineTranslationLyric.decodeQqAuxiliaryLyricPayload()

private fun QQPlayLyricData.qqRomanizedLyrics(): String? =
    roma.takeIfLyricTimeAvailable(romaTime).decodeQqAuxiliaryLyricPayload()

private fun QQPlayLyricData.qrcLyricTime(): JsonElement? =
    qrcTime?.takeUnless { it.isExplicitlyUnavailable() } ?: lrcTime

private fun String?.takeIfLyricTimeAvailable(time: JsonElement?): String? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return value.takeUnless { time.isExplicitlyUnavailable() }
}

private fun JsonElement?.isExplicitlyUnavailable(): Boolean =
    (this as? JsonPrimitive)?.contentOrNull?.trim() == "0"

private fun String.isHexPayload(): Boolean =
    length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun levenshtein(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length

    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)

    for (i in left.indices) {
        current[0] = i + 1
        for (j in right.indices) {
            val cost = if (left[i] == right[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost,
            )
        }
        val next = previous
        previous = current
        current = next
    }

    return previous[right.length]
}

private fun kotlinx.serialization.json.JsonElement?.stringContentOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun String.extractQrcLyricContent(): String {
    val match = Regex("""LyricContent="(.*?)"""", RegexOption.DOT_MATCHES_ALL).find(this)
    return (match?.groupValues?.getOrNull(1) ?: this).decodeLyricPayload().orEmpty()
}

private fun String.countQrcLines(): Int =
    lineSequence().count { it.trimStart().startsWith("[") && qrcLineRegex.containsMatchIn(it) }

private fun String.toQrcPreview(maxLines: Int = 8): String =
    lineSequence()
        .map { line -> line.replace(qrcLineRegex, "").replace(qrcWordRegex, "").trim() }
        .filter { it.isNotBlank() && !it.startsWith("[") }
        .take(maxLines)
        .joinToString("\n")

private const val QQ_TRACK_ID_SEPARATOR = "#"
private const val QQ_SEARCH_VARIANT_LIMIT = 3
private const val MIN_FUZZY_ARTIST_LENGTH = 5
private const val FUZZY_ARTIST_DISTANCE_RATIO = 0.25
private val qrcLineRegex = Regex("""\[\s*\d+\s*,\s*\d+\s*]""")
private val qrcWordRegex = Regex("""\(\s*\d+\s*,\s*\d+\s*\)""")
