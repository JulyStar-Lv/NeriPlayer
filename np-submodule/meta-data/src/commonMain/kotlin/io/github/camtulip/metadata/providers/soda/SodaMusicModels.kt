package io.github.camtulip.metadata.providers.soda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class SodaSearchResponse(
    @SerialName("result_groups") val resultGroups: List<SodaResultGroup>? = null,
)

@Serializable
internal data class SodaResultGroup(
    val id: String? = null,
    val data: List<SodaResultItem>? = null,
)

@Serializable
internal data class SodaResultItem(
    val meta: SodaResultMeta? = null,
    val entity: SodaEntity? = null,
)

@Serializable
internal data class SodaResultMeta(
    @SerialName("item_type") val itemType: String? = null,
)

@Serializable
internal data class SodaEntity(
    val track: SodaTrack? = null,
)

@Serializable
internal data class SodaTrackDetailResponse(
    val lyric: SodaLyricInfo? = null,
    val track: SodaTrack? = null,
)

@Serializable
internal data class SodaTrack(
    val id: String,
    val album: SodaAlbum? = null,
    val artists: List<SodaArtist>? = null,
    val duration: Long? = null,
    val name: String? = null,
)

@Serializable
internal data class SodaAlbum(
    val id: String? = null,
    val name: String? = null,
    @SerialName("url_cover") val coverUrl: SodaTemplateUrl? = null,
)

@Serializable
internal data class SodaArtist(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
internal data class SodaTemplateUrl(
    val uri: String? = null,
    val urls: List<String>? = null,
    @SerialName("template_prefix") val templatePrefix: String? = null,
)

@Serializable
internal data class SodaLyricInfo(
    val content: String? = null,
    val lang: String? = null,
    val type: String? = null,
    val translations: Map<String, JsonElement>? = null,
    @SerialName("lang_translations") val langTranslations: Map<String, SodaLyricTranslation>? = null,
)

@Serializable
internal data class SodaLyricTranslation(
    val content: String? = null,
    val lang: String? = null,
    val type: String? = null,
)
