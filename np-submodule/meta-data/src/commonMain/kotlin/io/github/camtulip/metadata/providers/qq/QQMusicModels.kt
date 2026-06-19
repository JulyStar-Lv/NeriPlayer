package io.github.camtulip.metadata.providers.qq

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class QQSearchResponse(
    val data: QQSearchData? = null,
)

@Serializable
internal data class QQSearchData(
    val song: QQSearchSong? = null,
)

@Serializable
internal data class QQSearchSong(
    val list: List<QQSongSummary>? = null,
)

@Serializable
internal data class QQSongSummary(
    @SerialName("songmid") val songMid: String,
    @SerialName("songname") val songName: String,
    @SerialName("songid") val songId: Long? = null,
    val singer: List<QQArtist> = emptyList(),
    @SerialName("albummid") val albumMid: String? = null,
    @SerialName("albumname") val albumName: String? = null,
    val interval: Long? = null,
)

@Serializable
internal data class QQArtist(
    val id: Long? = null,
    val mid: String? = null,
    val name: String,
)

@Serializable
internal data class QQDetailRoot(
    @SerialName("songinfo") val songInfo: QQDetailEnvelope? = null,
)

@Serializable
internal data class QQDetailEnvelope(
    val data: QQDetailData? = null,
)

@Serializable
internal data class QQDetailData(
    @SerialName("track_info") val trackInfo: QQTrackInfo? = null,
)

@Serializable
internal data class QQTrackInfo(
    val id: Long? = null,
    val mid: String,
    val name: String,
    val title: String? = null,
    val singer: List<QQArtist> = emptyList(),
    val album: QQAlbum? = null,
    val interval: Long? = null,
)

@Serializable
internal data class QQAlbum(
    val id: Long? = null,
    val mid: String? = null,
    val name: String,
    val title: String? = null,
)

@Serializable
internal data class QQLyricResponse(
    val lyric: String? = null,
    val trans: String? = null,
)

@Serializable
internal data class QQPlayLyricRoot(
    @SerialName("music.musichallSong.PlayLyricInfo.GetPlayLyricInfo")
    val playLyricInfo: QQPlayLyricEnvelope? = null,
)

@Serializable
internal data class QQPlayLyricEnvelope(
    val data: QQPlayLyricData? = null,
)

@Serializable
internal data class QQPlayLyricData(
    val songID: Long? = null,
    val qrc: Int? = null,
    @SerialName("qrc_t") val qrcTime: JsonElement? = null,
    @SerialName("lrc_t") val lrcTime: JsonElement? = null,
    val qyc: JsonElement? = null,
    val crypt: Int? = null,
    val lyric: String? = null,
    val trans: String? = null,
    @SerialName("trans_t") val transTime: JsonElement? = null,
    val roma: String? = null,
    @SerialName("roma_t") val romaTime: JsonElement? = null,
    @SerialName("lt_lyric") val lineTranslationLyric: String? = null,
)
