package io.github.camtulip.metadata.providers.apple

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class AppleMusicSearchResponse(
    val results: AppleMusicSearchResults? = null,
    val resources: AppleMusicResources? = null,
)

@Serializable
internal data class AppleMusicSearchResults(
    val songs: AppleMusicSongCollection? = null,
)

@Serializable
internal data class AppleMusicSongResponse(
    val data: List<AppleMusicSong>? = null,
)

@Serializable
internal data class AppleMusicLyricsResponse(
    val data: List<AppleMusicLyricsResource>? = null,
)

@Serializable
internal data class AppleMusicSongCollection(
    val data: List<AppleMusicSong>? = null,
)

@Serializable
internal data class AppleMusicResources(
    val songs: Map<String, AppleMusicSong>? = null,
)

@Serializable
internal data class AppleMusicSong(
    val id: String = "",
    val attributes: AppleMusicSongAttributes? = null,
    val relationships: AppleMusicSongRelationships? = null,
)

@Serializable
internal data class AppleMusicSongAttributes(
    val name: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("albumName") val albumName: String? = null,
    @SerialName("durationInMillis") val durationInMillis: Long? = null,
    val isrc: String? = null,
    val artwork: AppleMusicArtwork? = null,
)

@Serializable
internal data class AppleMusicArtwork(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
internal data class AppleMusicLyricsResource(
    val attributes: AppleMusicLyricsAttributes? = null,
)

@Serializable
internal data class AppleMusicLyricsAttributes(
    val ttml: String? = null,
    @SerialName("ttmlLocalizations") val ttmlLocalizations: JsonElement? = null,
)

@Serializable
internal data class AppleMusicSongRelationships(
    @SerialName("syllable-lyrics") val syllableLyrics: AppleMusicLyricsRelationship? = null,
    val lyrics: AppleMusicLyricsRelationship? = null,
)

@Serializable
internal data class AppleMusicLyricsRelationship(
    val data: List<AppleMusicLyricsResource>? = null,
)
