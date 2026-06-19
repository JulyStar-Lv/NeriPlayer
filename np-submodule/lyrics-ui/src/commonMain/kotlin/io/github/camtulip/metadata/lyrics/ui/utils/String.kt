package io.github.camtulip.metadata.lyrics.ui.utils

import kotlin.text.CharCategory

fun Char.isCjk(): Boolean {
    val code = code
    return code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF ||
        code in 0x3000..0x303F ||
        code in 0x3040..0x309F ||
        code in 0x30A0..0x30FF ||
        code in 0xAC00..0xD7AF ||
        code in 0x1100..0x11FF ||
        code in 0x3130..0x318F
}

fun Char.isJapanese(): Boolean {
    return this.code in 0x3040..0x309F ||
        this.code in 0x30A0..0x30FF ||
        this.code in 0xFF66..0xFF9F
}

fun Char.isKorean(): Boolean {
    return this.code in 0xAC00..0xD7AF || this.code in 0x1100..0x11FF
}

fun Char.isArabic(): Boolean {
    val code = code
    return code in 0x0600..0x06FF ||
        code in 0x0750..0x077F ||
        code in 0x08A0..0x08FF ||
        code in 0xFB50..0xFDFF ||
        code in 0xFE70..0xFEFF
}

fun Char.isDevanagari(): Boolean {
    val code = code
    return code in 0x0900..0x097F || code in 0xA8E0..0xA8FF
}

fun String.isPureCjk(): Boolean {
    val cleanedStr = filter { it != ' ' && it != ',' && it != '\n' && it != '\r' }
    if (cleanedStr.isEmpty()) {
        return false
    }
    return cleanedStr.all { it.isCjk() }
}

fun String.containsJapanese(): Boolean = any { it.isJapanese() }

fun String.containsKorean(): Boolean = any { it.isKorean() }

fun String.isRtl(): Boolean = any { it.isArabic() }

fun String.isPunctuation(): Boolean {
    return isNotEmpty() && all { char ->
        char.isWhitespace() ||
            char in ".,!?;:\"'()[]{}…—–-、。，！？；：\"\"''（）【】《》～·" ||
            char.category in setOf(
                CharCategory.CONNECTOR_PUNCTUATION,
                CharCategory.DASH_PUNCTUATION,
                CharCategory.END_PUNCTUATION,
                CharCategory.FINAL_QUOTE_PUNCTUATION,
                CharCategory.INITIAL_QUOTE_PUNCTUATION,
                CharCategory.OTHER_PUNCTUATION,
                CharCategory.START_PUNCTUATION
            )
    }
}
