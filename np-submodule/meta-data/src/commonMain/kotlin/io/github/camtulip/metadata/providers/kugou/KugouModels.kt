package io.github.camtulip.metadata.providers.kugou

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class KugouSearchResponse(
    val status: Int? = null,
    val data: KugouSearchData? = null,
)

@Serializable
internal data class KugouSearchData(
    val info: List<KugouSongSummary>? = null,
    val lists: List<KugouSongSummary>? = null,
)

@Serializable
internal data class KugouSongSummary(
    val hash: String? = null,
    @SerialName("FileHash") val fileHash: String? = null,
    val songname: String? = null,
    @SerialName("SongName") val songName: String? = null,
    @SerialName("songname_original") val songNameOriginal: String? = null,
    val singername: String? = null,
    @SerialName("SingerName") val singerName: String? = null,
    val filename: String? = null,
    @SerialName("FileName") val fileName: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("AlbumName") val webAlbumName: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    val duration: Long? = null,
    @SerialName("Duration") val webDuration: Long? = null,
    @SerialName("HQDuration") val highQualityDuration: Long? = null,
)

@Serializable
internal data class KugouSongInfoResponse(
    val hash: String? = null,
    @SerialName("songName") val songName: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("singerName") val singerName: String? = null,
    @SerialName("fileName") val fileName: String? = null,
    @SerialName("imgUrl") val imageUrl: String? = null,
    @SerialName("album_img") val albumImageUrl: String? = null,
    @SerialName("albumid") val albumId: Long? = null,
    @SerialName("req_albumid") val requestedAlbumId: String? = null,
    @SerialName("timeLength") val timeLength: Long? = null,
    val extra: KugouSongExtra? = null,
    val status: Int? = null,
    val errcode: Int? = null,
)

@Serializable
internal data class KugouSongExtra(
    @SerialName("128timelength") val standardTimeLength: Long? = null,
    @SerialName("320timelength") val highTimeLength: Long? = null,
    @SerialName("hightimelength") val losslessTimeLength: Long? = null,
    @SerialName("sqtimelength") val superQualityTimeLength: Long? = null,
)

@Serializable
internal data class KugouLyricsSearchResponse(
    val status: Int? = null,
    val candidates: List<KugouLyricsCandidate>? = null,
)

@Serializable
internal data class KugouLyricsCandidate(
    val id: String? = null,
    val accesskey: String? = null,
    val singer: String? = null,
    val song: String? = null,
    val duration: Long? = null,
    val krctype: Int? = null,
    @SerialName("content_format") val contentFormat: Int? = null,
    val language: String? = null,
)

@Serializable
internal data class KugouLyricsDownloadResponse(
    val status: Int? = null,
    val fmt: String? = null,
    val charset: String? = null,
    val content: String? = null,
    @SerialName("error_code") val errorCode: Int? = null,
)
