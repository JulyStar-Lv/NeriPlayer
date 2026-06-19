package io.github.camtulip.metadata.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataCacheTest {
    @Test
    fun cachedProviderReusesSearchResults() = runTest {
        val delegate = CountingProvider(
            id = ProviderId("counting"),
            candidates = listOf(
                TrackCandidate(
                    provider = ProviderId("counting"),
                    id = ProviderTrackId("1"),
                    title = "RUNAWAY",
                ),
            ),
        )
        val cache = InMemoryMetadataCache()
        val provider = CachedMetadataProvider(delegate, cache)
        val query = TrackQuery(title = "RUNAWAY")

        provider.searchTrack(query)
        provider.searchTrack(query)

        assertEquals(1, delegate.searchCalls)
        assertEquals(1, cache.snapshot().searchHits)
        assertEquals(1, cache.snapshot().searchMisses)
    }

    @Test
    fun cachedProviderReusesTrackAndLyricsResults() = runTest {
        val providerId = ProviderId("counting")
        val trackId = ProviderTrackId("1")
        val delegate = CountingProvider(
            id = providerId,
            track = TrackMetadata(provider = providerId, id = trackId, title = "RUNAWAY"),
            lyrics = LyricsMetadata(provider = providerId, trackId = trackId, syncPrecision = SyncPrecision.LineSynced),
        )
        val cache = InMemoryMetadataCache()
        val provider = CachedMetadataProvider(delegate, cache)

        provider.getTrack(trackId)
        provider.getTrack(trackId)
        provider.getLyricsMetadata(trackId)
        provider.getLyricsMetadata(trackId)

        assertEquals(1, delegate.trackCalls)
        assertEquals(1, delegate.lyricsCalls)
        assertEquals(1, cache.snapshot().trackHits)
        assertEquals(1, cache.snapshot().lyricsHits)
    }
}

private class CountingProvider(
    override val id: ProviderId,
    private val candidates: List<TrackCandidate> = emptyList(),
    private val track: TrackMetadata? = null,
    private val lyrics: LyricsMetadata? = null,
) : MetadataProvider {
    var searchCalls = 0
        private set
    var trackCalls = 0
        private set
    var lyricsCalls = 0
        private set

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        searchCalls++
        return candidates
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        trackCalls++
        return track
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata? {
        lyricsCalls++
        return lyrics
    }
}
