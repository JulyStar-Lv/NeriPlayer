package io.github.camtulip.metadata.lyrics.core.parser

import io.github.camtulip.metadata.lyrics.core.model.ISyncedLine
import io.github.camtulip.metadata.lyrics.core.model.SyncedLyrics
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeAlignment
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeLine
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeSyllable
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine

/**
 * Parser for NetEase Cloud Music YRC lyrics.
 *
 * Typical line format:
 * `[12580,3470](12580,250,0)难(12830,300,0)以...`
 *
 * Some payloads may also prepend JSON credit lines from the newer lyric API.
 * Those lines are ignored here because they are not part of timed karaoke data.
 */
object NeteaseYrcParser : ILyricsParser {
    private val lineRegex = Regex("""^\[(\d+),\s*(\d+)](.*)$""")
    private val syllableRegex = Regex("""\((\d+),\s*(\d+),\s*-?\d+\)([^()\r\n]*)""")

    override fun canParse(content: String): Boolean {
        return content.lineSequence().any { line ->
            lineRegex.matches(line.trim()) && line.contains('(')
        }
    }

    override fun parse(lines: List<String>): SyncedLyrics {
        val parsedLines = lines.mapNotNull(::parseLine)
        return SyncedLyrics(lines = parsedLines)
    }

    private fun parseLine(rawLine: String): ISyncedLine? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("{")) {
            return null
        }

        val match = lineRegex.matchEntire(line) ?: return null
        val lineStart = match.groupValues[1].toIntOrNull() ?: return null
        val lineDuration = match.groupValues[2].toIntOrNull() ?: return null
        val lineEnd = lineStart + lineDuration
        val content = match.groupValues[3]

        val syllables = syllableRegex.findAll(content).mapNotNull { syllableMatch ->
            val rawStart = syllableMatch.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val duration = syllableMatch.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val start = if (rawStart < lineStart) lineStart + rawStart else rawStart
            KaraokeSyllable(
                content = syllableMatch.groupValues[3],
                start = start,
                end = start + duration
            )
        }.toList()

        if (syllables.isEmpty()) {
            val plainText = content.trim()
            return if (plainText.isNotEmpty()) {
                SyncedLine(
                    content = plainText,
                    translation = null,
                    start = lineStart,
                    end = lineEnd
                )
            } else {
                null
            }
        }

        return KaraokeLine.MainKaraokeLine(
            syllables = syllables,
            translation = null,
            alignment = KaraokeAlignment.Unspecified,
            start = lineStart,
            end = lineEnd
        )
    }
}
