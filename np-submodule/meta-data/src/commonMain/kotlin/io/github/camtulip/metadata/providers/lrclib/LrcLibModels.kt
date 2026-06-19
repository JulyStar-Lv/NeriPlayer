package io.github.camtulip.metadata.providers.lrclib

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LrcLibTrackResponse(
    val id: Long,
    val name: String,
    val trackName: String,
    val artistName: String,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

@Serializable
internal data class LrcLibErrorResponse(
    @SerialName("message")
    val message: String? = null,
)
