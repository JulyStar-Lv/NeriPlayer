package io.github.camtulip.metadata.core

interface MetadataProvider {
    val id: ProviderId

    suspend fun searchTrack(query: TrackQuery): List<TrackCandidate>

    suspend fun getTrack(id: ProviderTrackId): TrackMetadata? = null

    suspend fun getLyricsMetadata(id: ProviderTrackId): LyricsMetadata? = null
}
