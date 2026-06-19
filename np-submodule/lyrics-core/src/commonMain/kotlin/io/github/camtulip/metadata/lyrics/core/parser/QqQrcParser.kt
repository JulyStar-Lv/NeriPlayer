package io.github.camtulip.metadata.lyrics.core.parser

import io.github.camtulip.metadata.lyrics.core.model.ISyncedLine
import io.github.camtulip.metadata.lyrics.core.model.SyncedLyrics
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeAlignment
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeLine
import io.github.camtulip.metadata.lyrics.core.model.karaoke.KaraokeSyllable
import io.github.camtulip.metadata.lyrics.core.model.synced.SyncedLine

/**
 * Parser for QQ Music QRC/QYC word-timed lyrics.
 *
 * Typical line format:
 * `[0,1000]Qyc(0,400) lyric(400,600)`
 *
 * QQ places the lyric text before each `(start,duration)` pair. This differs
 * from NetEase YRC, where the timing tuple comes before the syllable text.
 */
object QqQrcParser : ILyricsParser {
    private val lineRegex = Regex("""^\[(\d+),\s*(\d+)](.*)$""")
    private val syllableRegex = Regex("""((?:(?!\(\d+,\s*\d+\)).)*?)\((\d+),\s*(\d+)\)""")

    override fun canParse(content: String): Boolean {
        return content.lineSequence().any { rawLine ->
            val line = rawLine.trim()
            val match = lineRegex.matchEntire(line) ?: return@any false
            syllableRegex.containsMatchIn(match.groupValues[3])
        }
    }

    override fun parse(lines: List<String>): SyncedLyrics {
        val parsedLines = lines.mapNotNull(::parseLine)
        return SyncedLyrics(lines = parsedLines)
    }

    private fun parseLine(rawLine: String): ISyncedLine? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("[").not()) {
            return null
        }

        val match = lineRegex.matchEntire(line) ?: return null
        val lineStart = match.groupValues[1].toIntOrNull() ?: return null
        val lineDuration = match.groupValues[2].toIntOrNull() ?: return null
        val lineEnd = lineStart + lineDuration
        val content = match.groupValues[3]

        val rawSyllables = syllableRegex.findAll(content)
            .mapNotNull { syllableMatch ->
                val syllableContent = syllableMatch.groupValues[1]
                if (syllableContent.isEmpty()) {
                    return@mapNotNull null
                }
                val rawStart = syllableMatch.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val duration = syllableMatch.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                RawQqSyllable(syllableContent, rawStart, duration)
            }
            .toList()
        val usesRelativeTiming = rawSyllables.isNotEmpty() &&
            rawSyllables.all { it.start < lineStart } &&
            rawSyllables.maxOf { it.start } <= lineDuration
        val syllables = rawSyllables.map { syllable ->
            val start = if (usesRelativeTiming) lineStart + syllable.start else syllable.start
            KaraokeSyllable(
                content = syllable.content,
                start = start,
                end = start + syllable.duration,
            )
        }

        if (syllables.isEmpty()) {
            val plainText = content.trim()
            return if (plainText.isNotEmpty()) {
                SyncedLine(
                    content = plainText,
                    translation = null,
                    start = lineStart,
                    end = lineEnd,
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
            end = maxOf(lineEnd, syllables.maxOf { it.end }),
        )
    }

    private data class RawQqSyllable(
        val content: String,
        val start: Int,
        val duration: Int,
    )
}
