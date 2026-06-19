package io.github.camtulip.metadata.core

object TrackTextNormalizer {
    private val bracketedVersionRegex = Regex("""\s*[\[(（][^\])）]*(remaster|remastered|live|acoustic|sped up|slowed|instrumental|karaoke|edit|version|remix|mix|demo|cover|explicit|clean|radio)[^\])）]*[\])）]\s*""", RegexOption.IGNORE_CASE)
    private val trailingVersionRegex = Regex("""\s*[-–—:]\s*[^\n]*(remaster|remastered|live|acoustic|sped up|slowed|instrumental|karaoke|edit|version|remix|mix|demo|cover|explicit|clean|radio)[^\n]*$""", RegexOption.IGNORE_CASE)
    private val featuringRegex = Regex("""\s+(feat\.?|featuring|ft\.?)\s+.+$""", RegexOption.IGNORE_CASE)
    private val artistFeaturingRegex = Regex("""\s*(feat\.?|featuring|ft\.?)\s*""", RegexOption.IGNORE_CASE)
    private val whitespaceRegex = Regex("""\s+""")
    private val artistSplitRegex = Regex("""\s*(,|/|;|、|&|\+|×| x |\band\b| with )\s*""", RegexOption.IGNORE_CASE)
    private val cjkRunRegex = Regex("""[\u3400-\u9FFF\uF900-\uFAFF]+""")
    private val latinRunRegex = Regex("""[a-z0-9]+(?:\s+[a-z0-9]+)*""")

    private val simplifiedMap = mapOf(
        '臺' to '台',
        '台' to '台',
        '妳' to '你',
        '裡' to '里',
        '裏' to '里',
        '後' to '后',
        '愛' to '爱',
        '說' to '说',
        '聽' to '听',
        '風' to '风',
        '夢' to '梦',
        '與' to '与',
        '為' to '为',
        '會' to '会',
        '來' to '来',
        '離' to '离',
        '開' to '开',
        '過' to '过',
        '還' to '还',
        '這' to '这',
        '那' to '那',
        '個' to '个',
        '們' to '们',
        '沒' to '没',
        '無' to '无',
        '時' to '时',
        '間' to '间',
        '長' to '长',
        '樂' to '乐',
        '聲' to '声',
        '詞' to '词',
        '曲' to '曲',
    )

    fun normalizeTitle(value: String): String =
        value
            .lowercase()
            .foldTraditionalVariants()
            .replace(bracketedVersionRegex, " ")
            .replace(trailingVersionRegex, " ")
            .replace(featuringRegex, " ")
            .replacePunctuationWithSpaces()
            .replace(whitespaceRegex, " ")
            .trim()

    fun normalizeArtist(value: String): String =
        value
            .lowercase()
            .foldTraditionalVariants()
            .replacePunctuationWithSpaces()
            .replace(whitespaceRegex, " ")
            .trim()

    fun splitArtists(value: String): List<String> =
        value
            .replace(artistFeaturingRegex, ",")
            .split(artistSplitRegex)
            .map(::normalizeArtist)
            .filter { it.isNotBlank() }
            .distinct()

    fun artistAliases(value: String): Set<String> =
        splitArtists(value).flatMap { part ->
            buildList {
                add(part)
                cjkRunRegex.findAll(part)
                    .map { it.value }
                    .filter { it.length >= 2 }
                    .forEach(::add)
                latinRunRegex.findAll(part)
                    .map { it.value.trim() }
                    .filter { it.length >= 2 }
                    .forEach { latinAlias ->
                        add(latinAlias)
                        add(latinAlias.replace(" ", ""))
                    }
            }
        }
            .filter { it.isNotBlank() }
            .toSet()

    fun versionTags(value: String): Set<TrackVersionTag> {
        val normalized = value.lowercase().foldTraditionalVariants()
        return buildSet {
            if (containsAny(normalized, "live", "现场", "現場", "演唱会", "演唱會")) add(TrackVersionTag.Live)
            if (containsAny(normalized, "acoustic", "unplugged", "不插电", "不插電")) add(TrackVersionTag.Acoustic)
            if (containsAny(normalized, "remix", "mix", "混音")) add(TrackVersionTag.Remix)
            if (containsAny(normalized, "instrumental", "伴奏", "纯音乐", "純音樂")) add(TrackVersionTag.Instrumental)
            if (containsAny(normalized, "karaoke", "卡拉ok")) add(TrackVersionTag.Karaoke)
            if (containsAny(normalized, "demo", "样带", "樣帶")) add(TrackVersionTag.Demo)
            if (containsAny(normalized, "cover", "翻唱")) add(TrackVersionTag.Cover)
            if (containsAny(normalized, "remaster", "remastered", "重制", "重製")) add(TrackVersionTag.Remaster)
            if (containsAny(normalized, "radio edit", "single edit", "edit", "电台", "電台")) add(TrackVersionTag.Edit)
            if (containsAny(normalized, "sped up", "speed up", "nightcore", "加速")) add(TrackVersionTag.SpedUp)
            if (containsAny(normalized, "slowed", "slow version", "减速", "慢速")) add(TrackVersionTag.Slowed)
            if (containsAny(normalized, "explicit")) add(TrackVersionTag.Explicit)
            if (containsAny(normalized, "clean")) add(TrackVersionTag.Clean)
        }
    }

    fun candidateKey(candidate: TrackCandidate): String =
        buildString {
            append(candidate.provider.value)
            append('|')
            append(normalizeTitle(candidate.title))
            append('|')
            append(candidate.artists.flatMap(::splitArtists).sorted().joinToString(","))
            append('|')
            append(candidate.album?.let(::normalizeTitle).orEmpty())
            append('|')
            append(candidate.durationMs?.let { (it / 3_000L).toString() }.orEmpty())
        }

    private fun containsAny(value: String, vararg needles: String): Boolean =
        needles.any { value.contains(it, ignoreCase = true) }

    private fun String.foldTraditionalVariants(): String =
        map { simplifiedMap[it] ?: it }.joinToString("")

    private fun String.replacePunctuationWithSpaces(): String =
        map { char ->
            when {
                char.isLetterOrDigit() || char.isWhitespace() -> char
                else -> ' '
            }
        }.joinToString("")
}

enum class TrackVersionTag {
    Live,
    Acoustic,
    Remix,
    Instrumental,
    Karaoke,
    Demo,
    Cover,
    Remaster,
    Edit,
    SpedUp,
    Slowed,
    Explicit,
    Clean,
}
