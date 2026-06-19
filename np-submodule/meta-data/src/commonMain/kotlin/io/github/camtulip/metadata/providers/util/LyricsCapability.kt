package io.github.camtulip.metadata.providers.util

import io.github.camtulip.metadata.core.LyricsMetadata
import io.github.camtulip.metadata.core.LyricsType
import io.github.camtulip.metadata.core.ProviderId
import io.github.camtulip.metadata.core.ProviderTrackId
import io.github.camtulip.metadata.core.SyncPrecision

private val lrcTimestampRegex = Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")
private val neteaseYrcWordRegex = Regex("""\(\d+,\d+,\d+\)""")
private val qrcWordRegex = Regex("""\(\s*\d+\s*,\s*\d+\s*\)""")
private val kugouKrcWordRegex = Regex("""<\s*\d+\s*,\s*\d+\s*,\s*-?\d+\s*>""")

fun buildLyricsMetadata(
    provider: ProviderId,
    trackId: ProviderTrackId,
    plainOrLineLyrics: String?,
    wordTimedLyrics: String? = null,
    translatedLyrics: String? = null,
    romanizedLyrics: String? = null,
): LyricsMetadata {
    val hasWordTimed = wordTimedLyrics.hasWordTiming()
    val hasLineSynced = plainOrLineLyrics.hasLineTiming()
    val hasPlain = plainOrLineLyrics.isUsefulLyrics() || wordTimedLyrics.isUsefulLyrics()

    return LyricsMetadata(
        provider = provider,
        trackId = trackId,
        availableTypes = buildSet {
            if (hasPlain) add(LyricsType.Plain)
            if (hasLineSynced) add(LyricsType.LineSynced)
            if (hasWordTimed) add(LyricsType.WordSynced)
            if (translatedLyrics.isUsefulLyrics()) add(LyricsType.Translation)
            if (romanizedLyrics.isUsefulLyrics()) add(LyricsType.Romanization)
        },
        hasTranslation = translatedLyrics.isUsefulLyrics(),
        hasRomanization = romanizedLyrics.isUsefulLyrics(),
        syncPrecision = when {
            hasWordTimed -> SyncPrecision.WordSynced
            hasLineSynced -> SyncPrecision.LineSynced
            hasPlain -> SyncPrecision.Unsynced
            else -> SyncPrecision.Unknown
        },
        plainOrLineLyrics = plainOrLineLyrics?.takeIf { it.isUsefulLyrics() },
        wordTimedLyrics = wordTimedLyrics?.takeIf { it.isUsefulLyrics() },
        translatedLyrics = translatedLyrics?.takeIf { it.isUsefulLyrics() },
        romanizedLyrics = romanizedLyrics?.takeIf { it.isUsefulLyrics() },
    )
}

internal fun String?.isUsefulLyrics(): Boolean {
    val value = this?.trim() ?: return false
    if (value.isBlank()) return false
    if (value == "暂无歌词") return false
    return true
}

internal fun String?.hasLineTiming(): Boolean =
    isUsefulLyrics() && lrcTimestampRegex.containsMatchIn(this ?: "")

internal fun String?.hasWordTiming(): Boolean =
    isUsefulLyrics() && (
        neteaseYrcWordRegex.containsMatchIn(this ?: "") ||
            qrcWordRegex.containsMatchIn(this ?: "") ||
            kugouKrcWordRegex.containsMatchIn(this ?: "")
        )
