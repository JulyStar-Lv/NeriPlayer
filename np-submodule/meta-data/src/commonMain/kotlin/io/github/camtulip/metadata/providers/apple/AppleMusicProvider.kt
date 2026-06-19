package io.github.camtulip.metadata.providers.apple

import io.github.camtulip.metadata.core.AlbumMetadata
import io.github.camtulip.metadata.core.ArtistMetadata
import io.github.camtulip.metadata.core.Artwork
import io.github.camtulip.metadata.core.LyricsMetadata
import io.github.camtulip.metadata.core.LyricsType
import io.github.camtulip.metadata.core.MetadataProvider
import io.github.camtulip.metadata.core.MetadataProviderException
import io.github.camtulip.metadata.core.ProviderFailureKind
import io.github.camtulip.metadata.core.ProviderId
import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision
import io.github.camtulip.metadata.core.TrackCandidate
import io.github.camtulip.metadata.core.TrackMetadata
import io.github.camtulip.metadata.core.TrackQuery
import io.github.camtulip.metadata.providers.util.classifiedProviderCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull

class AppleMusicProvider(
    private val httpClient: HttpClient,
    accessToken: String? = null,
    cookie: String? = null,
    private val webEndpoint: String = "https://music.apple.com",
    private val apiEndpoint: String = "https://amp-api.music.apple.com",
    private val storefront: String = "us",
    private val language: String = "en-US",
    private val tokenEndpoint: String = "https://beta.music.apple.com",
) : MetadataProvider {
    override val id: ProviderId = ProviderId("applemusic")

    private var accessToken: String? = accessToken.normalizedBearerToken()
    private var cookieSession: AppleMusicCookieSession? = cookie.toAppleMusicCookieSession()

    fun setCookie(cookie: String?): AppleMusicProvider {
        cookieSession = cookie.toAppleMusicCookieSession()
        return this
    }

    suspend fun prewarmAccessToken() {
        getAccessToken()
    }

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        val response = classifiedProviderCall(id, "Apple Music search") {
            json.decodeFromString<AppleMusicSearchResponse>(apiGetText("/v1/catalog/${resolvedStorefront()}/search") {
                parameter("term", query.displayText)
                parameter("types", "songs")
                parameter("limit", query.normalizedLimit.toString())
                parameter("l", resolvedLanguage())
                parameter("platform", "web")
                parameter("format[resources]", "map")
                parameter("include[songs]", "artists")
                parameter("extend", "artistUrl")
            })
        }

        val resourceSongs = response.resources?.songs.orEmpty()
        return response.results?.songs?.data.orEmpty()
            .mapNotNull { song ->
                val songId = song.id.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                (resourceSongs[songId]?.withFallbackId(songId) ?: song).toCandidate(id)
            }
            .take(query.normalizedLimit)
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        val response = classifiedProviderCall(this.id, "Apple Music get track") {
            json.decodeFromString<AppleMusicSongResponse>(apiGetText("/v1/catalog/${resolvedStorefront()}/songs/${id.value}") {
                parameter("l", resolvedLanguage())
                parameter("platform", "web")
            })
        }

        val lyrics = runCatching { getLyricsMetadata(id) }.getOrNull()
        return response.data?.firstOrNull()?.toMetadata(this.id, lyrics)
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata? {
        if (!hasLyricsCookie()) return null

        return classifiedProviderCall(this.id, "Apple Music get lyrics metadata") {
            val responseText = apiGetTextOrNull("/v1/catalog/${resolvedStorefront()}/songs/${id.value}/syllable-lyrics") {
                parameter("l", resolvedLanguage())
                parameter("platform", "web")
                parameter("extend", "ttmlLocalizations")
            }
            responseText
                ?.let { json.decodeFromString<AppleMusicLyricsResponse>(it).toLyricsMetadata(this.id, id) }
                ?: getIncludedLyricsMetadata(id)
        }
    }

    private suspend fun apiGetText(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): String {
        val token = getAccessToken()
        return try {
            apiRequest(token, path, block)
        } catch (error: ResponseException) {
            if (error.response.status.value in setOf(401, 403)) {
                val refreshedToken = getAccessToken(forceRefresh = true)
                try {
                    apiRequest(refreshedToken, path, block)
                } catch (retryError: ResponseException) {
                    if (retryError.response.status.value in setOf(401, 403)) {
                        clearAccessToken()
                        throw MetadataProviderException(
                            kind = ProviderFailureKind.Unauthorized,
                            message = "Apple Music web access token is invalid or expired",
                            cause = retryError,
                        )
                    } else {
                        throw retryError
                    }
                }
            } else {
                throw error
            }
        }
    }

    private suspend fun apiGetTextOrNull(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): String? =
        try {
            apiGetText(path, block)
        } catch (error: ResponseException) {
            if (error.response.status.value == 404) {
                null
            } else {
                throw error
            }
        }

    private suspend fun apiRequest(
        token: String,
        path: String,
        block: HttpRequestBuilder.() -> Unit,
    ): String =
        httpClient.get("${apiEndpoint.trimEnd('/')}$path") {
            appleApiHeaders(token)
            block()
        }.bodyAsText()

    private suspend fun getIncludedLyricsMetadata(id: ProviderTrackId): LyricsMetadata? {
        val responseText = apiGetTextOrNull("/v1/catalog/${resolvedStorefront()}/songs/${id.value}") {
            parameter("l", resolvedLanguage())
            parameter("platform", "web")
            parameter("include[songs]", "syllable-lyrics")
            parameter("extend", "ttmlLocalizations")
        } ?: return null

        return json.decodeFromString<AppleMusicSongResponse>(responseText)
            .toLyricsMetadata(this.id, id)
    }

    private fun hasLyricsCookie(): Boolean =
        cookieSession?.mediaUserToken?.isNotBlank() == true

    private fun resolvedStorefront(): String =
        cookieSession?.storefront ?: storefront

    private fun resolvedLanguage(): String =
        cookieSession?.language ?: language

    private suspend fun getAccessToken(forceRefresh: Boolean = false): String {
        if (!forceRefresh) {
            accessToken?.let { return it }
        } else {
            clearAccessToken()
        }

        val token = AppleMusicWebTokenCache.getOrFetch(tokenCacheKey()) {
            fetchWebAccessToken()
        }
        accessToken = token
        return token
    }

    private suspend fun clearAccessToken() {
        accessToken = null
        AppleMusicWebTokenCache.invalidate(tokenCacheKey())
    }

    private fun tokenCacheKey(): String =
        tokenEndpoint.trimEnd('/')

    private suspend fun fetchWebAccessToken(): String {
        val endpoint = tokenEndpoint.trimEnd('/')
        val page = httpClient.get(endpoint) {
            appleWebHeaders()
        }.bodyAsText()
        val scriptPath = INDEX_SCRIPT_REGEX.find(page)?.value
            ?: throw MetadataProviderException(
                kind = ProviderFailureKind.ParseError,
                message = "Apple Music web index script was not found",
            )
        val scriptUrl = if (scriptPath.startsWith("http")) scriptPath else "$endpoint$scriptPath"
        val script = httpClient.get(scriptUrl) {
            appleWebHeaders()
        }.bodyAsText()
        return WEB_TOKEN_REGEX.find(script)?.value.normalizedBearerToken()
            ?: throw MetadataProviderException(
                kind = ProviderFailureKind.ParseError,
                message = "Apple Music web access token was not found",
            )
    }

    private fun HttpRequestBuilder.appleApiHeaders(token: String) {
        header("User-Agent", USER_AGENT)
        header("Origin", webEndpoint.trimEnd('/'))
        header("Referer", "${webEndpoint.trimEnd('/')}/")
        header("Accept", "application/json")
        header("Accept-Language", "${resolvedLanguage()},en;q=0.9")
        header("Authorization", "Bearer $token")
        header("x-apple-renewal", "true")
        cookieSession?.header?.let { header("Cookie", it) }
        cookieSession?.mediaUserToken?.let { header("media-user-token", it) }
        cookieSession?.mediaUserToken?.let { header("Music-User-Token", it) }
    }

    private fun HttpRequestBuilder.appleWebHeaders() {
        header("User-Agent", USER_AGENT)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        header("Accept-Language", "${resolvedLanguage()},en;q=0.9")
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36"
        val INDEX_SCRIPT_REGEX = Regex("""/assets/index~[^"' <]+\.js""")
        val WEB_TOKEN_REGEX = Regex("""eyJ[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+\.[A-Za-z0-9\-_=]+""")
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

private object AppleMusicWebTokenCache {
    private val mutex = Mutex()
    private val tokens = mutableMapOf<String, String>()

    suspend fun getOrFetch(
        key: String,
        fetch: suspend () -> String,
    ): String =
        mutex.withLock {
            tokens[key] ?: fetch().also { token ->
                tokens[key] = token
            }
        }

    suspend fun invalidate(key: String) {
        mutex.withLock {
            tokens.remove(key)
        }
    }
}

private data class AppleMusicCookieSession(
    val header: String,
    val mediaUserToken: String?,
    val storefront: String?,
    val language: String?,
)

private fun AppleMusicSong.withFallbackId(fallbackId: String): AppleMusicSong =
    if (id.isNotBlank()) this else copy(id = fallbackId)

private fun AppleMusicSong.toCandidate(provider: ProviderId): TrackCandidate? {
    val attrs = attributes ?: return null
    val title = attrs.name?.takeIf { it.isNotBlank() } ?: return null
    return TrackCandidate(
        provider = provider,
        id = ProviderTrackId(id),
        title = title,
        artists = attrs.artistName.splitArtists(),
        album = attrs.albumName?.takeIf { it.isNotBlank() },
        durationMs = attrs.durationInMillis,
        isrc = attrs.isrc?.takeIf { it.isNotBlank() },
        artwork = attrs.artwork?.toArtwork(),
    )
}

private fun AppleMusicSong.toMetadata(provider: ProviderId, lyrics: LyricsMetadata?): TrackMetadata? {
    val attrs = attributes ?: return null
    val title = attrs.name?.takeIf { it.isNotBlank() } ?: return null
    return TrackMetadata(
        provider = provider,
        id = ProviderTrackId(id),
        title = title,
        artists = attrs.artistName.splitArtists().map { ArtistMetadata(it) },
        album = attrs.albumName?.takeIf { it.isNotBlank() }?.let { album ->
            AlbumMetadata(title = album, artwork = attrs.artwork?.toArtwork())
        },
        durationMs = attrs.durationInMillis,
        isrc = attrs.isrc?.takeIf { it.isNotBlank() },
        artwork = attrs.artwork?.toArtwork(),
        lyrics = lyrics,
    )
}

private fun AppleMusicLyricsResponse.toLyricsMetadata(
    provider: ProviderId,
    trackId: ProviderTrackId,
): LyricsMetadata? {
    val ttml = data.firstUsefulTtml() ?: return null

    return LyricsMetadata(
        provider = provider,
        trackId = trackId,
        availableTypes = setOf(
            LyricsType.LineSynced,
            LyricsType.WordSynced,
            LyricsType.SyllableSynced,
        ),
        syncPrecision = SyncPrecision.SyllableSynced,
        wordTimedLyrics = ttml,
    )
}

private fun AppleMusicSongResponse.toLyricsMetadata(
    provider: ProviderId,
    trackId: ProviderTrackId,
): LyricsMetadata? {
    val ttml = data.orEmpty()
        .asSequence()
        .mapNotNull { song ->
            song.relationships?.syllableLyrics?.data.firstUsefulTtml()
                ?: song.relationships?.lyrics?.data.firstUsefulTtml()
        }
        .firstOrNull()
        ?: return null

    return LyricsMetadata(
        provider = provider,
        trackId = trackId,
        availableTypes = setOf(
            LyricsType.LineSynced,
            LyricsType.WordSynced,
            LyricsType.SyllableSynced,
        ),
        syncPrecision = SyncPrecision.SyllableSynced,
        wordTimedLyrics = ttml,
    )
}

private fun List<AppleMusicLyricsResource>?.firstUsefulTtml(): String? =
    orEmpty()
        .asSequence()
        .mapNotNull { it.attributes?.ttmlText() }
        .firstOrNull()

private fun AppleMusicLyricsAttributes.ttmlText(): String? =
    ttml.takeUsefulTtml() ?: ttmlLocalizations.findUsefulTtml()

private fun JsonElement?.findUsefulTtml(): String? =
    when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull.takeUsefulTtml()
        is JsonObject -> values
            .asSequence()
            .mapNotNull { it.findUsefulTtml() }
            .firstOrNull()
        is JsonArray -> asSequence()
            .mapNotNull { it.findUsefulTtml() }
            .firstOrNull()
    }

private fun AppleMusicArtwork.toArtwork(): Artwork? {
    val template = url?.takeIf { it.isNotBlank() } ?: return null
    val size = minOf(width ?: DEFAULT_ARTWORK_SIZE, height ?: DEFAULT_ARTWORK_SIZE, DEFAULT_ARTWORK_SIZE)
    return Artwork(
        url = template
            .replace("{w}", size.toString())
            .replace("{h}", size.toString()),
        width = size,
        height = size,
    )
}

private fun String?.splitArtists(): List<String> {
    val value = this?.takeIf { it.isNotBlank() } ?: return emptyList()
    val commaParts = value.split(", ").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
    if (commaParts.isEmpty()) return emptyList()

    val last = commaParts.removeAt(commaParts.lastIndex)
    val ampParts = last.split(" & ").map { it.trim() }.filter { it.isNotBlank() }
    commaParts += ampParts
    return commaParts.distinct()
}

private fun String?.normalizedBearerToken(): String? =
    this
        ?.trim()
        ?.removePrefix("Bearer ")
        ?.removePrefix("bearer ")
        ?.takeIf { it.isNotBlank() }

private fun String?.toAppleMusicCookieSession(): AppleMusicCookieSession? {
    val header = this
        ?.trim()
        ?.trimEnd(';')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val values = header
        .split(';')
        .mapNotNull { part ->
            val trimmed = part.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0) {
                null
            } else {
                trimmed.substring(0, separator).trim() to trimmed.substring(separator + 1).trim()
            }
        }
        .toMap()
    val podStorefront = values["POD"]?.substringBefore('~')?.normalizedStorefront()
    val language = values["dslang"].normalizedAppleLanguage()
        ?: values["POD"].normalizedPodLanguage(podStorefront)

    return AppleMusicCookieSession(
        header = header,
        mediaUserToken = values["media-user-token"]?.takeIf { it.isNotBlank() },
        storefront = podStorefront,
        language = language,
    )
}

private fun String?.normalizedStorefront(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.takeIf { STOREFRONT_REGEX.matches(it) }

private fun String?.normalizedAppleLanguage(): String? {
    val parts = this
        ?.trim()
        ?.replace('_', '-')
        ?.split('-')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: return null

    return when (parts.size) {
        1 -> parts.single().lowercase().takeIf { it.length == 2 }
        2 -> {
            val first = parts[0]
            val second = parts[1]
            if (first.length == 2 && second.length == 2 && first.all { it.isUpperCase() }) {
                "${second.lowercase()}-${first.uppercase()}"
            } else if (first.length == 2 && second.length == 2) {
                "${first.lowercase()}-${second.uppercase()}"
            } else {
                null
            }
        }
        else -> null
    }
}

private fun String?.normalizedPodLanguage(storefront: String?): String? {
    val country = storefront?.uppercase() ?: return null
    val language = this
        ?.substringAfter('~', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.lowercase()
        ?: return null
    return "$language-$country"
}

private fun String?.takeUsefulTtml(): String? =
    this
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { it.contains("<tt", ignoreCase = true) }

private const val DEFAULT_ARTWORK_SIZE = 1000
private val STOREFRONT_REGEX = Regex("""[a-z]{2}""")
