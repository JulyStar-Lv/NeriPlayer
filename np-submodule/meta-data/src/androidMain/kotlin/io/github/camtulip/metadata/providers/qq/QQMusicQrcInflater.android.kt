package io.github.camtulip.metadata.providers.qq

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

internal actual fun inflateQqQrcPayload(payload: ByteArray): ByteArray? =
    runCatching {
        InflaterInputStream(ByteArrayInputStream(payload)).use { input -> input.readBytes() }
    }.recoverCatching {
        GZIPInputStream(ByteArrayInputStream(payload)).use { input -> input.readBytes() }
    }.getOrNull()
