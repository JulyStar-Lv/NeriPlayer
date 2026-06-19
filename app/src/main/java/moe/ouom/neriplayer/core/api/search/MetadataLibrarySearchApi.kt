package moe.ouom.neriplayer.core.api.search

import io.github.camtulip.metadata.core.LyricsMetadata
import io.github.camtulip.metadata.core.LyricsType
import io.github.camtulip.metadata.core.InMemoryMetadataCache
import io.github.camtulip.metadata.core.MetadataProvider
import io.github.camtulip.metadata.core.MetadataResolution
import io.github.camtulip.metadata.core.MetadataResolver
import io.github.camtulip.metadata.core.MetadataResolverConfig
import io.github.camtulip.metadata.core.ProviderId
import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.ScoredTrackCandidate
import io.github.camtulip.metadata.core.TrackCandidate
import io.github.camtulip.metadata.core.TrackMetadata
import io.github.camtulip.metadata.core.TrackQuery
import io.github.camtulip.metadata.core.cached
import io.github.camtulip.metadata.core.rankTrackCandidates
import io.github.camtulip.metadata.providers.createMetadataProviders
import io.github.camtulip.metadata.providers.createPlatformHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val SEARCH_RESULT_LIMIT = 10
private const val PROVIDER_TIMEOUT_MS = 3_000L
private const val DETAIL_ENRICHMENT_TIMEOUT_MS = 1_200L
private const val DETAIL_ENRICHMENT_CANDIDATE_LIMIT = 6
private const val DETAIL_ENRICHMENT_PER_PROVIDER = 1
private const val NETEASE_PROVIDER_ID = "netease"

class MetadataLibrarySearchApi : SearchApi {
    private val sessionMutex = Mutex()
    private var session: MetadataSearchSession? = null

    override suspend fun search(keyword: String, page: Int): List<SongSearchInfo> =
        search(keyword = keyword, page = page, durationMs = null)

    suspend fun search(
        keyword: String,
        page: Int,
        durationMs: Long?,
        enrichDetails: Boolean = true
    ): List<SongSearchInfo> {
        val query = keyword.toMetadataTrackQuery(
            limit = SEARCH_RESULT_LIMIT,
            durationMs = durationMs
        )
        val activeSession = searchSession()
        val resolution = activeSession.resolver
            .resolve(query)
            .enrichCandidateDetails(activeSession.providers, enrichDetails)

        return resolution.candidates
            .take(SEARCH_RESULT_LIMIT)
            .map { it.toSongSearchInfo() }
    }

    suspend fun search(
        songName: String,
        songArtist: String,
        durationMs: Long? = null,
        enrichDetails: Boolean = true
    ): List<SongSearchInfo> {
        val query = TrackQuery(
            title = songName,
            artists = songArtist.splitArtists(),
            durationMs = durationMs.normalizedSearchDurationMs(),
            limit = SEARCH_RESULT_LIMIT
        )
        val activeSession = searchSession()
        val resolution = activeSession.resolver
            .resolve(query)
            .enrichCandidateDetails(activeSession.providers, enrichDetails)

        return resolution.candidates
            .take(SEARCH_RESULT_LIMIT)
            .map { it.toSongSearchInfo() }
    }

    override suspend fun getSongInfo(id: String): SongDetails {
        return getSongInfo(
            SongSearchInfo(
                id = id,
                songName = "",
                singer = "",
                duration = "",
                source = MusicPlatform.CLOUD_MUSIC,
                albumName = null,
                coverUrl = null,
                providerId = NETEASE_PROVIDER_ID
            )
        )
    }

    suspend fun getSongInfo(song: SongSearchInfo): SongDetails {
        val activeSession = searchSession()
        val providerId = song.providerId?.takeIf { it.isNotBlank() }
            ?: song.source.defaultMetadataProviderId()
        val provider = activeSession.providersById[ProviderId(providerId)]
            ?: throw IllegalArgumentException("Unsupported metadata provider: $providerId")
        val metadata = provider.getTrack(ProviderTrackId(song.id))
            ?: throw IllegalStateException("No metadata found for $providerId:${song.id}")

        return metadata.toSongDetails(fallback = song)
    }

    private suspend fun searchSession(): MetadataSearchSession =
        sessionMutex.withLock {
            session?.let { return@withLock it }

            val httpClient = createPlatformHttpClient()
            val providers = createMetadataProviders(httpClient = httpClient)
                .cached(InMemoryMetadataCache())
            MetadataSearchSession(
                httpClient = httpClient,
                providers = providers,
                resolver = MetadataResolver(
                    providers = providers,
                    config = MetadataResolverConfig(providerTimeoutMs = PROVIDER_TIMEOUT_MS)
                )
            ).also { session = it }
        }

    private class MetadataSearchSession(
        private val httpClient: HttpClient,
        val providers: List<MetadataProvider>,
        val resolver: MetadataResolver
    ) {
        val providersById: Map<ProviderId, MetadataProvider> = providers.associateBy { it.id }

        @Suppress("unused")
        fun close() {
            httpClient.close()
        }
    }

}

internal fun String.toMetadataTrackQuery(limit: Int, durationMs: Long? = null): TrackQuery {
    val input = trim()
    val split = input.split(Regex("\\s+-\\s+"), limit = 2)
    val normalizedDurationMs = durationMs.normalizedSearchDurationMs()
    return if (split.size == 2) {
        TrackQuery(
            title = split[0].trim(),
            artists = split[1].splitArtists(),
            durationMs = normalizedDurationMs,
            limit = limit
        )
    } else {
        TrackQuery(
            title = input,
            durationMs = normalizedDurationMs,
            limit = limit
        )
    }
}

private fun Long?.normalizedSearchDurationMs(): Long? =
    this?.takeIf { it > 0L }

private fun String.splitArtists(): List<String> =
    split(Regex("\\s*([/,、，&])\\s*|\\s+(feat\\.?|ft\\.?)\\s+|\\s+[xX]\\s+", RegexOption.IGNORE_CASE))
        .map { it.trim() }
        .filter { it.isNotBlank() }

private suspend fun MetadataResolution.enrichCandidateDetails(
    providers: List<MetadataProvider>,
    enrichDetails: Boolean
): MetadataResolution {
    if (!enrichDetails || candidates.isEmpty()) {
        return this
    }

    val providersById = providers.associateBy { it.id }
    val providerCandidateCounts = mutableMapOf<ProviderId, Int>()
    val indexedCandidates = candidates
        .mapIndexed { index, scored ->
            val providerRank = providerCandidateCounts.getOrElse(scored.candidate.provider) { 0 }
            providerCandidateCounts[scored.candidate.provider] = providerRank + 1
            IndexedScoredCandidate(
                index = index,
                providerRank = providerRank,
                scored = scored
            )
        }

    val enrichableCandidates = indexedCandidates
        .filter { indexed ->
            shouldEnrichDetails(indexed.index, indexed.providerRank) &&
                !indexed.scored.candidate.hasDisplayDetails()
        }
        .take(DETAIL_ENRICHMENT_CANDIDATE_LIMIT)
    if (enrichableCandidates.isEmpty()) {
        return this
    }

    val enrichedCandidates = coroutineScope {
        enrichableCandidates.map { indexed ->
            async {
                indexed.scored.enrichCandidateDetail(
                    providersById = providersById
                )
            }
        }.awaitAll()
    }

    val replacements = enrichedCandidates.associateBy { it.candidate.identityKey() }
    val currentCandidates = candidates
        .map { scored -> replacements[scored.candidate.identityKey()] ?: scored }
        .rankTrackCandidates()

    return copy(
        best = currentCandidates.firstOrNull()?.candidate ?: best,
        candidates = currentCandidates
    )
}

private data class IndexedScoredCandidate(
    val index: Int,
    val providerRank: Int,
    val scored: ScoredTrackCandidate
)

private suspend fun ScoredTrackCandidate.enrichCandidateDetail(
    providersById: Map<ProviderId, MetadataProvider>
): ScoredTrackCandidate {
    val provider = providersById[candidate.provider] ?: return this
    val metadata = withTimeoutOrNull(DETAIL_ENRICHMENT_TIMEOUT_MS) {
        try {
            provider.getTrack(candidate.id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
    val enrichedCandidate = metadata?.let { candidate.mergeMetadata(it) } ?: candidate
    return copy(candidate = enrichedCandidate)
}

private fun shouldEnrichDetails(index: Int, providerRank: Int): Boolean =
    index < DETAIL_ENRICHMENT_CANDIDATE_LIMIT ||
        providerRank < DETAIL_ENRICHMENT_PER_PROVIDER

private fun TrackCandidate.hasDisplayDetails(): Boolean =
    !artwork?.url.isNullOrBlank() && lyrics.hasPlayableLyricsContent()

private fun LyricsMetadata?.hasPlayableLyricsContent(): Boolean =
    !this?.wordTimedLyrics.isNullOrBlank() || !this?.plainOrLineLyrics.isNullOrBlank()

private fun TrackCandidate.mergeMetadata(metadata: TrackMetadata): TrackCandidate =
    copy(
        artists = artists.ifEmpty { metadata.artists.map { it.name } },
        album = album ?: metadata.album?.title,
        durationMs = durationMs ?: metadata.durationMs,
        isrc = isrc ?: metadata.isrc,
        artwork = artwork ?: metadata.artwork ?: metadata.album?.artwork,
        lyrics = lyrics ?: metadata.lyrics
    )

private fun ScoredTrackCandidate.toSongSearchInfo(): SongSearchInfo {
    val candidate = candidate
    return SongSearchInfo(
        id = candidate.id.value,
        songName = candidate.title,
        singer = candidate.artists.joinToString("/"),
        duration = candidate.durationMs.toDurationLabel(),
        source = candidate.provider.toLegacyMusicPlatform(),
        albumName = candidate.album,
        coverUrl = candidate.artwork?.url,
        providerId = candidate.provider.value,
        matchScore = score,
        lyricsType = candidate.lyrics.toSearchLyricsType(),
        durationMs = candidate.durationMs
    )
}

private fun TrackMetadata.toSongDetails(fallback: SongSearchInfo): SongDetails =
    SongDetails(
        id = id.value,
        songName = title.ifBlank { fallback.songName },
        singer = artists.map { it.name }.ifEmpty { listOf(fallback.singer) }.filter { it.isNotBlank() }
            .joinToString("/"),
        album = album?.title ?: fallback.albumName.orEmpty(),
        coverUrl = artwork?.url ?: album?.artwork?.url ?: fallback.coverUrl,
        lyric = lyrics.selectPrimaryLyricsForNeri(fallback.lyricsType),
        translatedLyric = lyrics?.translatedLyrics
    )

internal fun LyricsMetadata?.selectPrimaryLyricsForNeri(preferredType: SearchLyricsType): String? {
    val lyrics = this ?: return null
    return when (preferredType) {
        SearchLyricsType.WORD -> lyrics.wordTimedLyrics ?: lyrics.plainOrLineLyrics
        SearchLyricsType.LINE -> lyrics.plainOrLineLyrics ?: lyrics.wordTimedLyrics
        SearchLyricsType.NONE -> lyrics.wordTimedLyrics ?: lyrics.plainOrLineLyrics
    }
}

private fun LyricsMetadata?.toSearchLyricsType(): SearchLyricsType {
    val lyrics = this ?: return SearchLyricsType.NONE
    return when {
        !lyrics.wordTimedLyrics.isNullOrBlank() ||
            LyricsType.WordSynced in lyrics.availableTypes ||
            LyricsType.SyllableSynced in lyrics.availableTypes -> SearchLyricsType.WORD
        !lyrics.plainOrLineLyrics.isNullOrBlank() ||
            LyricsType.LineSynced in lyrics.availableTypes ||
            LyricsType.Plain in lyrics.availableTypes -> SearchLyricsType.LINE
        else -> SearchLyricsType.NONE
    }
}

private fun ProviderId.toLegacyMusicPlatform(): MusicPlatform =
    when (value) {
        "qq" -> MusicPlatform.QQ_MUSIC
        else -> MusicPlatform.CLOUD_MUSIC
    }

private fun MusicPlatform.defaultMetadataProviderId(): String =
    when (this) {
        MusicPlatform.CLOUD_MUSIC -> NETEASE_PROVIDER_ID
        MusicPlatform.QQ_MUSIC -> "qq"
    }

private fun Long?.toDurationLabel(): String {
    val durationMs = this ?: return ""
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun TrackCandidate.identityKey(): Pair<ProviderId, String> =
    provider to id.value
