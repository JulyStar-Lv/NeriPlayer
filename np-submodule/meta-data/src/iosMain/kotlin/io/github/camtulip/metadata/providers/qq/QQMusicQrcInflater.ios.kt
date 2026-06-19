@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.camtulip.metadata.providers.qq

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream_s

internal actual fun inflateQqQrcPayload(payload: ByteArray): ByteArray? =
    inflateWithWindowBits(payload, ZLIB_WINDOW_BITS)
        ?: inflateWithWindowBits(payload, GZIP_WINDOW_BITS)

private fun inflateWithWindowBits(
    payload: ByteArray,
    windowBits: Int,
): ByteArray? {
    if (payload.isEmpty()) return null

    return memScoped {
        val stream = alloc<z_stream_s>()
        val initStatus = inflateInit2_(
            stream.ptr,
            windowBits,
            ZLIB_VERSION,
            sizeOf<z_stream_s>().toInt(),
        )
        if (initStatus != Z_OK) return@memScoped null

        val chunks = mutableListOf<ByteArray>()
        var totalSize = 0
        try {
            payload.usePinned { inputPinned ->
                stream.next_in = inputPinned.addressOf(0).reinterpret()
                stream.avail_in = payload.size.convert()

                var status: Int
                do {
                    val output = ByteArray(INFLATE_CHUNK_SIZE)
                    status = output.usePinned { outputPinned ->
                        stream.next_out = outputPinned.addressOf(0).reinterpret()
                        stream.avail_out = output.size.convert()
                        inflate(stream.ptr, ZLIB_NO_FLUSH)
                    }

                    val produced = output.size - stream.avail_out.toInt()
                    if (produced > 0) {
                        chunks += output.copyOf(produced)
                        totalSize += produced
                    }
                } while (status == Z_OK)

                if (status != Z_STREAM_END || totalSize <= 0) {
                    return@memScoped null
                }
            }
            chunks.concat(totalSize)
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}

private fun List<ByteArray>.concat(totalSize: Int): ByteArray {
    val result = ByteArray(totalSize)
    var offset = 0
    for (chunk in this) {
        chunk.copyInto(result, destinationOffset = offset)
        offset += chunk.size
    }
    return result
}

private const val ZLIB_WINDOW_BITS = 15
private const val GZIP_WINDOW_BITS = 15 + 16
private const val ZLIB_NO_FLUSH = 0
private const val INFLATE_CHUNK_SIZE = 8192
