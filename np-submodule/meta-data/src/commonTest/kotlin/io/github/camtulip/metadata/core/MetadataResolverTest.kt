package io.github.camtulip.metadata.core

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MetadataResolverTest {
    @Test
    fun selectsBestCandidateByScore() = runTest {
        val provider = ResolverTestProvider(
            id = ProviderId("fake"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("fake"),
                    id = ProviderTrackId("wrong"),
                    title = "Different Song",
                    artists = listOf("Someone Else"),
                ),
                TrackCandidate(
                    provider = ProviderId("fake"),
                    id = ProviderTrackId("right"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                ),
            ),
        )

        val result = MetadataResolver(listOf(provider)).resolve(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic")),
        )

        assertNotNull(result.best)
        assertEquals("right", result.best.id.value)
    }

    @Test
    fun ranksWordTimedLyricsAheadOfSameScoreMatches() = runTest {
        val plainProvider = ResolverTestProvider(
            id = ProviderId("plain"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("plain"),
                    id = ProviderTrackId("plain"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                ),
            ),
        )
        val wordTimedProvider = ResolverTestProvider(
            id = ProviderId("word"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("word"),
                    id = ProviderTrackId("word"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                    lyrics = LyricsMetadata(
                        provider = ProviderId("word"),
                        trackId = ProviderTrackId("word"),
                        availableTypes = setOf(LyricsType.WordSynced),
                        syncPrecision = SyncPrecision.WordSynced,
                        wordTimedLyrics = "[0,1000]Runaway",
                    ),
                ),
            ),
        )

        val result = MetadataResolver(listOf(plainProvider, wordTimedProvider)).resolve(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic")),
        )

        assertEquals("word", result.best?.id?.value)
        assertEquals("word", result.candidates.first().candidate.id.value)
    }

    @Test
    fun ranksSyllableSyncedLyricsAheadOfSameScoreMatches() = runTest {
        val plainProvider = ResolverTestProvider(
            id = ProviderId("plain"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("plain"),
                    id = ProviderTrackId("plain"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                ),
            ),
        )
        val syllableProvider = ResolverTestProvider(
            id = ProviderId("syllable"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("syllable"),
                    id = ProviderTrackId("syllable"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                    lyrics = LyricsMetadata(
                        provider = ProviderId("syllable"),
                        trackId = ProviderTrackId("syllable"),
                        availableTypes = setOf(LyricsType.SyllableSynced),
                        syncPrecision = SyncPrecision.SyllableSynced,
                        wordTimedLyrics = "<tt></tt>",
                    ),
                ),
            ),
        )

        val result = MetadataResolver(listOf(plainProvider, syllableProvider)).resolve(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic")),
        )

        assertEquals("syllable", result.best?.id?.value)
        assertEquals("syllable", result.candidates.first().candidate.id.value)
    }

    @Test
    fun ranksHighResolutionArtworkAheadOfSameScoreMatches() = runTest {
        val lowArtworkProvider = ResolverTestProvider(
            id = ProviderId("low"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("low"),
                    id = ProviderTrackId("low"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                    artwork = Artwork(url = "https://example.com/low.jpg", width = 300, height = 300),
                ),
            ),
        )
        val highArtworkProvider = ResolverTestProvider(
            id = ProviderId("high"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("high"),
                    id = ProviderTrackId("high"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                    artwork = Artwork(url = "https://example.com/high.jpg", width = 1000, height = 1000),
                ),
            ),
        )

        val result = MetadataResolver(listOf(lowArtworkProvider, highArtworkProvider)).resolve(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic")),
        )

        assertEquals("high", result.best?.id?.value)
        assertEquals("high", result.candidates.first().candidate.id.value)
    }

    @Test
    fun keepsHigherScoreAheadOfLyricsAndArtworkTieBreakers() = runTest {
        val exactProvider = ResolverTestProvider(
            id = ProviderId("exact"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("exact"),
                    id = ProviderTrackId("exact"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                ),
            ),
        )
        val wrongProvider = ResolverTestProvider(
            id = ProviderId("wrong"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("wrong"),
                    id = ProviderTrackId("wrong"),
                    title = "Different Song",
                    artists = listOf("Someone Else"),
                    artwork = Artwork(url = "https://example.com/high.jpg", width = 2000, height = 2000),
                    lyrics = LyricsMetadata(
                        provider = ProviderId("wrong"),
                        trackId = ProviderTrackId("wrong"),
                        availableTypes = setOf(LyricsType.WordSynced),
                        syncPrecision = SyncPrecision.WordSynced,
                        wordTimedLyrics = "[0,1000]Different",
                    ),
                ),
            ),
        )

        val result = MetadataResolver(listOf(wrongProvider, exactProvider)).resolve(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic")),
        )

        assertEquals("exact", result.best?.id?.value)
    }

    @Test
    fun classifiesProviderExceptions() = runTest {
        val provider = FailingProvider(
            id = ProviderId("failing"),
            error = MetadataProviderException(
                kind = ProviderFailureKind.RateLimited,
                message = "Too many requests",
            ),
        )

        val result = MetadataResolver(listOf(provider)).resolve(TrackQuery(title = "RUNAWAY"))

        assertEquals(null, result.best)
        assertEquals(ProviderFailureKind.RateLimited, result.failures.single().kind)
    }

    @Test
    fun classifiesProviderTimeout() = runTest {
        val provider = SlowProvider(ProviderId("slow"))

        val result = MetadataResolver(
            providers = listOf(provider),
            config = MetadataResolverConfig(providerTimeoutMs = 50),
        ).resolve(TrackQuery(title = "RUNAWAY"))

        assertEquals(null, result.best)
        assertEquals(ProviderFailureKind.Timeout, result.failures.single().kind)
    }

    @Test
    fun dedupesEquivalentCandidatesFromSameProviderByDefault() = runTest {
        val providerId = ProviderId("fake")
        val provider = ResolverTestProvider(
            id = providerId,
            candidates = listOf(
                TrackCandidate(
                    provider = providerId,
                    id = ProviderTrackId("1"),
                    title = "RUNAWAY",
                    artists = listOf("OneRepublic"),
                    album = "RUNAWAY",
                    durationMs = 143_000,
                ),
                TrackCandidate(
                    provider = providerId,
                    id = ProviderTrackId("2"),
                    title = "Runaway",
                    artists = listOf("OneRepublic"),
                    album = "RUNAWAY",
                    durationMs = 143_500,
                ),
            ),
        )

        val result = MetadataResolver(listOf(provider)).resolve(
            TrackQuery(title = "RUNAWAY", artists = listOf("OneRepublic"), album = "RUNAWAY", durationMs = 143_000),
        )

        assertEquals(1, result.candidates.size)
    }

    @Test
    fun canDisableCandidateDeduplication() = runTest {
        val providerId = ProviderId("fake")
        val provider = ResolverTestProvider(
            id = providerId,
            candidates = listOf(
                TrackCandidate(provider = providerId, id = ProviderTrackId("1"), title = "RUNAWAY"),
                TrackCandidate(provider = providerId, id = ProviderTrackId("2"), title = "RUNAWAY"),
            ),
        )

        val result = MetadataResolver(
            providers = listOf(provider),
            config = MetadataResolverConfig(dedupeCandidates = false),
        ).resolve(TrackQuery(title = "RUNAWAY"))

        assertEquals(2, result.candidates.size)
    }

    @Test
    fun limitsCandidatesPerProviderToDefaultFive() = runTest {
        val providerId = ProviderId("fake")
        val provider = ResolverTestProvider(
            id = providerId,
            candidates = (1..10).map { index ->
                TrackCandidate(
                    provider = providerId,
                    id = ProviderTrackId(index.toString()),
                    title = "RUNAWAY $index",
                )
            },
        )

        val result = MetadataResolver(listOf(provider)).resolve(TrackQuery(title = "RUNAWAY"))

        assertEquals(DEFAULT_CANDIDATES_PER_PROVIDER, result.candidates.size)
    }

    @Test
    fun supportsCustomCandidatesPerProvider() = runTest {
        val providerId = ProviderId("fake")
        val provider = ResolverTestProvider(
            id = providerId,
            candidates = (1..10).map { index ->
                TrackCandidate(
                    provider = providerId,
                    id = ProviderTrackId(index.toString()),
                    title = "RUNAWAY $index",
                )
            },
        )

        val result = MetadataResolver(listOf(provider)).resolve(
            TrackQuery(title = "RUNAWAY", limit = 7),
        )

        assertEquals(7, result.candidates.size)
    }
}

private class ResolverTestProvider(
    override val id: ProviderId,
    private val candidates: List<TrackCandidate>,
    private val delayMs: Long = 0,
) : MetadataProvider {
    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        if (delayMs > 0) {
            delay(delayMs)
        }
        return candidates
    }
}

private class FailingProvider(
    override val id: ProviderId,
    private val error: Throwable,
) : MetadataProvider {
    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        throw error
    }
}

private class SlowProvider(
    override val id: ProviderId,
) : MetadataProvider {
    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        delay(10_000)
        return emptyList()
    }
}
