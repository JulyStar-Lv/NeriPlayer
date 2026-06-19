package io.github.camtulip.metadata.core

interface MetadataCache {
    suspend fun getSearch(provider: ProviderId, query: TrackQuery): List<TrackCandidate>?
    suspend fun putSearch(provider: ProviderId, query: TrackQuery, candidates: List<TrackCandidate>)

    suspend fun getTrack(provider: ProviderId, id: ProviderTrackId): TrackMetadata?
    suspend fun putTrack(metadata: TrackMetadata)

    suspend fun getLyrics(provider: ProviderId, id: ProviderTrackId): LyricsMetadata?
    suspend fun putLyrics(metadata: LyricsMetadata)
}

object NoopMetadataCache : MetadataCache {
    override suspend fun getSearch(provider: ProviderId, query: TrackQuery): List<TrackCandidate>? = null
    override suspend fun putSearch(provider: ProviderId, query: TrackQuery, candidates: List<TrackCandidate>) = Unit
    override suspend fun getTrack(provider: ProviderId, id: ProviderTrackId): TrackMetadata? = null
    override suspend fun putTrack(metadata: TrackMetadata) = Unit
    override suspend fun getLyrics(provider: ProviderId, id: ProviderTrackId): LyricsMetadata? = null
    override suspend fun putLyrics(metadata: LyricsMetadata) = Unit
}

class InMemoryMetadataCache : MetadataCache {
    private val searchEntries = mutableMapOf<String, List<TrackCandidate>>()
    private val trackEntries = mutableMapOf<String, TrackMetadata>()
    private val lyricsEntries = mutableMapOf<String, LyricsMetadata>()

    private var searchHits = 0
    private var searchMisses = 0
    private var trackHits = 0
    private var trackMisses = 0
    private var lyricsHits = 0
    private var lyricsMisses = 0

    override suspend fun getSearch(provider: ProviderId, query: TrackQuery): List<TrackCandidate>? {
        val value = searchEntries[searchKey(provider, query)]
        if (value == null) searchMisses++ else searchHits++
        return value
    }

    override suspend fun putSearch(provider: ProviderId, query: TrackQuery, candidates: List<TrackCandidate>) {
        searchEntries[searchKey(provider, query)] = candidates
    }

    override suspend fun getTrack(provider: ProviderId, id: ProviderTrackId): TrackMetadata? {
        val value = trackEntries[itemKey(provider, id)]
        if (value == null) trackMisses++ else trackHits++
        return value
    }

    override suspend fun putTrack(metadata: TrackMetadata) {
        trackEntries[itemKey(metadata.provider, metadata.id)] = metadata
    }

    override suspend fun getLyrics(provider: ProviderId, id: ProviderTrackId): LyricsMetadata? {
        val value = lyricsEntries[itemKey(provider, id)]
        if (value == null) lyricsMisses++ else lyricsHits++
        return value
    }

    override suspend fun putLyrics(metadata: LyricsMetadata) {
        lyricsEntries[itemKey(metadata.provider, metadata.trackId)] = metadata
    }

    fun snapshot(): MetadataCacheSnapshot =
        MetadataCacheSnapshot(
            searchEntries = searchEntries.size,
            trackEntries = trackEntries.size,
            lyricsEntries = lyricsEntries.size,
            searchHits = searchHits,
            searchMisses = searchMisses,
            trackHits = trackHits,
            trackMisses = trackMisses,
            lyricsHits = lyricsHits,
            lyricsMisses = lyricsMisses,
        )

    private fun searchKey(provider: ProviderId, query: TrackQuery): String =
        buildString {
            append(provider.value)
            append('|')
            append(TrackTextNormalizer.normalizeTitle(query.title))
            append('|')
            append(query.artists.joinToString(",") { TrackTextNormalizer.normalizeArtist(it) })
            append('|')
            append(query.album?.let(TrackTextNormalizer::normalizeTitle).orEmpty())
            append('|')
            append(query.durationMs?.toString().orEmpty())
            append('|')
            append(query.isrc?.uppercase().orEmpty())
            append('|')
            append(query.normalizedLimit)
        }

    private fun itemKey(provider: ProviderId, id: ProviderTrackId): String =
        "${provider.value}|${id.value}"
}

data class MetadataCacheSnapshot(
    val searchEntries: Int,
    val trackEntries: Int,
    val lyricsEntries: Int,
    val searchHits: Int,
    val searchMisses: Int,
    val trackHits: Int,
    val trackMisses: Int,
    val lyricsHits: Int,
    val lyricsMisses: Int,
)

class CachedMetadataProvider(
    private val delegate: MetadataProvider,
    private val cache: MetadataCache,
) : MetadataProvider {
    override val id: ProviderId = delegate.id

    override suspend fun searchTrack(query: TrackQuery): List<TrackCandidate> {
        cache.getSearch(id, query)?.let { return it }
        return delegate.searchTrack(query).also { cache.putSearch(id, query, it) }
    }

    override suspend fun getTrack(id: ProviderTrackId): TrackMetadata? {
        cache.getTrack(this.id, id)?.let { return it }
        return delegate.getTrack(id)?.also { cache.putTrack(it) }
    }

    override suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata? {
        cache.getLyrics(this.id, id)?.let { return it }
        return delegate.getLyricsMetadata(id)?.also { cache.putLyrics(it) }
    }
}

fun Iterable<MetadataProvider>.cached(cache: MetadataCache): List<MetadataProvider> =
    map { CachedMetadataProvider(it, cache) }
