package io.github.camtulip.metadata.providers.netease

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NeteaseSearchResponse(
    val result: NeteaseSearchResult? = null,
)

@Serializable
internal data class NeteaseSearchResult(
    val songs: List<NeteaseSong>? = null,
)

@Serializable
internal data class NeteaseSongDetailResponse(
    val songs: List<NeteaseSong>? = null,
)

@Serializable
internal data class NeteaseSong(
    val id: Long,
    val name: String,
    val artists: List<NeteaseArtist>? = null,
    val album: NeteaseAlbum? = null,
    val duration: Long? = null,
    @SerialName("dt") val durationAlt: Long? = null,
    @SerialName("ar") val artistsAlt: List<NeteaseArtist>? = null,
    @SerialName("al") val albumAlt: NeteaseAlbum? = null,
)

@Serializable
internal data class NeteaseArtist(
    val id: Long? = null,
    val name: String,
)

@Serializable
internal data class NeteaseAlbum(
    val id: Long? = null,
    val name: String,
    val picUrl: String? = null,
    val blurPicUrl: String? = null,
)

@Serializable
internal data class NeteaseLyricResponse(
    val lrc: NeteaseLyricPayload? = null,
    val tlyric: NeteaseLyricPayload? = null,
    val yrc: NeteaseLyricPayload? = null,
    val ytlrc: NeteaseLyricPayload? = null,
    val romalrc: NeteaseLyricPayload? = null,
)

@Serializable
internal data class NeteaseLyricPayload(
    val lyric: String? = null,
)
