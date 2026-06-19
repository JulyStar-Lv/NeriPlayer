package io.github.camtulip.metadata.providers.kugou

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeKugouKrcBase64(content: String): String? {
    val raw = runCatching { Base64.decode(content.trim()) }.getOrNull() ?: return null
    if (raw.size <= KRC_HEADER.size || !raw.startsWith(KRC_HEADER)) return null

    val encrypted = raw.copyOfRange(KRC_HEADER.size, raw.size)
    val compressed = ByteArray(encrypted.size) { index ->
        (encrypted[index].toInt() xor KRC_ENCODE_KEY[index % KRC_ENCODE_KEY.size].toInt()).toByte()
    }
    return inflateKugouKrcPayload(compressed)
        ?.decodeToString()
        ?.normalizeKugouLyricsText()
}

@OptIn(ExperimentalEncodingApi::class)
internal fun decodeKugouLrcBase64(content: String): String? =
    runCatching { Base64.decode(content.trim()).decodeToString().normalizeKugouLyricsText() }.getOrNull()

internal expect fun inflateKugouKrcPayload(payload: ByteArray): ByteArray?

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

private fun String.normalizeKugouLyricsText(): String =
    trimStart('\uFEFF')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

private val KRC_HEADER = byteArrayOf(
    'k'.code.toByte(),
    'r'.code.toByte(),
    'c'.code.toByte(),
    '1'.code.toByte(),
)

private val KRC_ENCODE_KEY = byteArrayOf(
    64.toByte(),
    71.toByte(),
    97.toByte(),
    119.toByte(),
    94.toByte(),
    50.toByte(),
    116.toByte(),
    71.toByte(),
    81.toByte(),
    54.toByte(),
    49.toByte(),
    45.toByte(),
    206.toByte(),
    210.toByte(),
    110.toByte(),
    105.toByte(),
)
