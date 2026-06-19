package io.github.camtulip.metadata.lyrics.core.exporter

import io.github.camtulip.metadata.lyrics.core.model.SyncedLyrics
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeLine
import io.github.camtulip.metadata.lyrics.core.model.karaoke.mapper.contentToString
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine
import io.github.camtulip.metadata.lyrics.core.model.synced.mapper.toSyncedLine
import io.github.camtulip.metadata.lyrics.core.utils.toTimeFormattedString

/**
 * Exporter for the standard LRC format.
 *
 * It converts [SyncedLyrics] back into a string representation in the standard LRC format without syllables and background lines.
 * - Supports ID3 tags ([ti:...], [ar:...]).
 * - Supports standard line timestamps [mm:ss.xx].
 * - Supports translations as separate lines with the same timestamp.
 */
object LrcExporter : ILyricsExporter {
    override fun export(lyrics: SyncedLyrics): String {
        if (lyrics.lines.isEmpty()) return ""

        val builder = StringBuilder()

        if (lyrics.title.isNotBlank()) {
            builder.appendLine("[ti:${lyrics.title}]")
        }
        if (!lyrics.artists.isNullOrEmpty() && lyrics.artists.all { it.name.isNotBlank() }) {
            builder.appendLine(
                "[ar:${lyrics.artists.joinToString("/") { it.name }}]"
            )
        }

        lyrics.lines.forEach { line ->
            val normalizedLine = when (line) {
                is KaraokeLine -> line.toSyncedLine()
                is SyncedLine -> line
                else -> return@forEach
            }

            val timeTag = "[${normalizedLine.start.toTimeFormattedString()}]"

            builder.appendLine("${timeTag}${normalizedLine.content}")
            normalizedLine.translation?.let { builder.appendLine("${timeTag}${it}") }
        }

        return builder.toString()
    }
}