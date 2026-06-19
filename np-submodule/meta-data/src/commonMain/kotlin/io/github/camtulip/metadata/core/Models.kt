package io.github.camtulip.metadata.core

import kotlinx.serialization.Serializable

const val DEFAULT_CANDIDATES_PER_PROVIDER = 5
const val MAX_CANDIDATES_PER_PROVIDER = 50

@Serializable
data class ProviderId(val value: String) {
    override fun toString(): String = value
}

@Serializable
data class ProviderTrackId(val value: String) {
    override fun toString(): String = value
}

@Serializable
data class TrackQuery(
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val limit: Int = DEFAULT_CANDIDATES_PER_PROVIDER,
) {
    val displayText: String
        get() = buildString {
            append(title)
            if (artists.isNotEmpty()) {
                append(" - ")
                append(artists.joinToString(", "))
            }
        }

    val normalizedLimit: Int
        get() = limit.coerceIn(1, MAX_CANDIDATES_PER_PROVIDER)
}

@Serializable
data class TrackCandidate(
    val provider: ProviderId,
    val id: ProviderTrackId,
    val title: String,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val artwork: Artwork? = null,
    val lyrics: LyricsMetadata? = null,
    val rawScore: Double? = null,
)

@Serializable
data class TrackMetadata(
    val provider: ProviderId,
    val id: ProviderTrackId,
    val title: String,
    val artists: List<ArtistMetadata> = emptyList(),
    val album: AlbumMetadata? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val artwork: Artwork? = null,
    val lyrics: LyricsMetadata? = null,
)

@Serializable
data class ArtistMetadata(
    val name: String,
    val providerId: String? = null,
)

@Serializable
data class AlbumMetadata(
    val title: String,
    val providerId: String? = null,
    val artists: List<ArtistMetadata> = emptyList(),
    val artwork: Artwork? = null,
)

@Serializable
data class Artwork(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class LyricsMetadata(
    val provider: ProviderId,
    val trackId: ProviderTrackId,
    val availableTypes: Set<LyricsType> = emptySet(),
    val hasTranslation: Boolean = false,
    val hasRomanization: Boolean = false,
    val syncPrecision: SyncPrecision = SyncPrecision.Unknown,
    val plainOrLineLyrics: String? = null,
    val wordTimedLyrics: String? = null,
    val translatedLyrics: String? = null,
    val romanizedLyrics: String? = null,
)

@Serializable
enum class LyricsType {
    Plain,
    LineSynced,
    WordSynced,
    SyllableSynced,
    Translation,
    Romanization,
}

@Serializable
enum class SyncPrecision {
    Unsynced,
    LineSynced,
    WordSynced,
    SyllableSynced,
    Unknown,
}

@Serializable
data class ScoredTrackCandidate(
    val candidate: TrackCandidate,
    val score: Double,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class MetadataResolution(
    val best: TrackCandidate?,
    val candidates: List<ScoredTrackCandidate>,
    val failures: List<ProviderFailure> = emptyList(),
)

@Serializable
data class ProviderFailure(
    val provider: ProviderId,
    val kind: ProviderFailureKind,
    val message: String,
)

@Serializable
enum class ProviderFailureKind {
    Network,
    Timeout,
    Unauthorized,
    RateLimited,
    Unsupported,
    ParseError,
    Unknown,
}
