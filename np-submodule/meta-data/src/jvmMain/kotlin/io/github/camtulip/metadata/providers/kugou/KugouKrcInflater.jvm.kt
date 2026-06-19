package io.github.camtulip.metadata.providers.kugou

import io.github.camtulip.metadata.providers.qq.inflateQqQrcPayload

internal actual fun inflateKugouKrcPayload(payload: ByteArray): ByteArray? =
    inflateQqQrcPayload(payload)
