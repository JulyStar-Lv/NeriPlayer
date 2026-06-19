package io.github.camtulip.metadata.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class MetadataResolver(
    private val providers: List<MetadataProvider>,
    private val scorer: TrackMatchScorer = TrackMatchScorer(),
    private val config: MetadataResolverConfig = MetadataResolverConfig(),
) {
    suspend fun resolve(query: TrackQuery): MetadataResolution {
        if (providers.isEmpty()) {
            return emptyResolution()
        }

        return coroutineScope {
            val resultChannel = Channel<ProviderSearchResult>(capacity = providers.size)
            providers.forEach { provider ->
                launch {
                    resultChannel.send(provider.searchToResult(query))
                }
            }

            val results = mutableListOf<ProviderSearchResult>()
            repeat(providers.size) {
                results += resultChannel.receive()
            }
            results.toResolution(query)
        }
    }

    private fun List<ProviderSearchResult>.toResolution(query: TrackQuery): MetadataResolution {
        val failures = flatMap { it.failures }
        val rawScored = flatMap { it.candidates }
            .map { scorer.score(query, it) }
            .rankTrackCandidates()
        val scored = if (config.dedupeCandidates) {
            rawScored.dedupeCandidates()
        } else {
            rawScored
        }.limitPerProvider(query.normalizedLimit)
        return MetadataResolution(
            best = scored.firstOrNull()?.candidate,
            candidates = scored,
            failures = failures,
        )
    }

    private data class ProviderSearchResult(
        val candidates: List<TrackCandidate> = emptyList(),
        val failures: List<ProviderFailure> = emptyList(),
    )

    private suspend fun MetadataProvider.searchToResult(query: TrackQuery): ProviderSearchResult =
        try {
            ProviderSearchResult(
                candidates = searchWithTimeout(query),
            )
        } catch (error: TimeoutCancellationException) {
            ProviderSearchResult(
                failures = listOf(error.toProviderFailure(id)),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ProviderSearchResult(
                failures = listOf(error.toProviderFailure(id)),
            )
        }

    private suspend fun MetadataProvider.searchWithTimeout(query: TrackQuery): List<TrackCandidate> {
        val timeoutMs = config.providerTimeoutMs
        return if (timeoutMs == null) {
            searchTrack(query)
        } else {
            withTimeout(timeoutMs) { searchTrack(query) }
        }
    }

    private fun emptyResolution(): MetadataResolution =
        MetadataResolution(
            best = null,
            candidates = emptyList(),
            failures = emptyList(),
        )
}

data class MetadataResolverConfig(
    val providerTimeoutMs: Long? = 15_000,
    val dedupeCandidates: Boolean = true,
)

private fun List<ScoredTrackCandidate>.dedupeCandidates(): List<ScoredTrackCandidate> =
    groupBy { TrackTextNormalizer.candidateKey(it.candidate) }
        .values
        .mapNotNull { candidates -> candidates.maxWithOrNull(candidateRankingComparator) }
        .rankTrackCandidates()

private fun List<ScoredTrackCandidate>.limitPerProvider(limit: Int): List<ScoredTrackCandidate> =
    groupBy { it.candidate.provider }
        .values
        .flatMap { providerCandidates -> providerCandidates.take(limit) }
        .rankTrackCandidates()

fun Iterable<ScoredTrackCandidate>.rankTrackCandidates(): List<ScoredTrackCandidate> =
    sortedWith(candidateRankingComparator.reversed())

private val candidateRankingComparator: Comparator<ScoredTrackCandidate> =
    compareBy<ScoredTrackCandidate> { it.score }
        .thenBy { it.candidate.lyrics.lyricsRankingTier() }
        .thenBy { it.candidate.artwork.artworkResolutionPixels() }

private fun LyricsMetadata?.lyricsRankingTier(): Int {
    val lyrics = this ?: return 0
    val hasWordTimedLyrics = lyrics.syncPrecision == SyncPrecision.WordSynced ||
        lyrics.syncPrecision == SyncPrecision.SyllableSynced ||
        LyricsType.WordSynced in lyrics.availableTypes ||
        LyricsType.SyllableSynced in lyrics.availableTypes ||
        !lyrics.wordTimedLyrics.isNullOrBlank()
    if (hasWordTimedLyrics) return WORD_TIMED_LYRICS_RANKING_TIER

    val hasLineSyncedLyrics = lyrics.syncPrecision == SyncPrecision.LineSynced ||
        LyricsType.LineSynced in lyrics.availableTypes
    if (hasLineSyncedLyrics) return LINE_SYNCED_LYRICS_RANKING_TIER

    val hasPlainLyrics = lyrics.syncPrecision == SyncPrecision.Unsynced ||
        LyricsType.Plain in lyrics.availableTypes ||
        !lyrics.plainOrLineLyrics.isNullOrBlank()
    return if (hasPlainLyrics) PLAIN_LYRICS_RANKING_TIER else 0
}

private fun Artwork?.artworkResolutionPixels(): Long {
    val artwork = this ?: return 0
    if (artwork.url.isBlank()) return 0

    val width = artwork.width?.takeIf { it > 0 }
    val height = artwork.height?.takeIf { it > 0 }
    return if (width != null && height != null) {
        width.toLong() * height.toLong()
    } else {
        ARTWORK_URL_RANKING_PIXELS
    }
}

private const val WORD_TIMED_LYRICS_RANKING_TIER = 3
private const val LINE_SYNCED_LYRICS_RANKING_TIER = 2
private const val PLAIN_LYRICS_RANKING_TIER = 1
private const val ARTWORK_URL_RANKING_PIXELS = 1L

private fun Throwable.toProviderFailure(provider: ProviderId): ProviderFailure =
    when (this) {
        is MetadataProviderException -> ProviderFailure(
            provider = provider,
            kind = kind,
            message = message,
        )
        is TimeoutCancellationException -> ProviderFailure(
            provider = provider,
            kind = ProviderFailureKind.Timeout,
            message = "Provider timed out",
        )
        else -> ProviderFailure(
            provider = provider,
            kind = ProviderFailureKind.Unknown,
            message = message ?: this::class.simpleName ?: "Unknown provider failure",
        )
    }
