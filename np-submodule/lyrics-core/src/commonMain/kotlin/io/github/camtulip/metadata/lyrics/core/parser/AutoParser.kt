package io.github.camtulip.metadata.lyrics.core.parser

import io.github.camtulip.metadata.lyrics.core.model.SyncedLyrics
import io.github.camtulip.metadata.lyrics.core.utils.PhoneticProvider

/**
 * A smart parser that automatically detects the lyrics format and uses the appropriate parser.
 *
 * This class combines the functionality of all individual parsers (`EnhancedLrcParser`,
 * `TTMLParser`, and `LyricifySyllableParser`) into a single, easy-to-use interface. It uses
 * `LyricsFormatGuesser` to determine the most likely format and then delegates the parsing task.
 *
 * This parser is extensible. You can register custom formats and their corresponding parsers.
 */
class AutoParser(
    private val fallbackPhoneticProvider: PhoneticProvider? = null,
    private val parsers: List<ILyricsParser> = listOf(
        TTMLParser(fallbackPhoneticProvider = fallbackPhoneticProvider),
        KugouKrcParser,
        QqQrcParser,
        NeteaseYrcParser,
        LyricifySyllableParser,
        EnhancedLrcParser,
    )
) : ILyricsParser {

    override fun canParse(content: String): Boolean =
        parsers.any { it.canParse(content) }

    override fun parse(content: String): SyncedLyrics {
        val parser = parsers.firstOrNull { it.canParse(content) }
        return parser?.parse(content) ?: SyncedLyrics(emptyList())
    }
}
