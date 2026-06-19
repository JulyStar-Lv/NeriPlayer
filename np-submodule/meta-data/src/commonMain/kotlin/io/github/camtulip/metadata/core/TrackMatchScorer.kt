package io.github.camtulip.metadata.core

class TrackMatchScorer(
    private val titleWeight: Double = 0.45,
    private val artistWeight: Double = 0.24,
    private val albumWeight: Double = 0.04,
    private val durationWeight: Double = 0.18,
    private val versionWeight: Double = 0.09,
) {
    fun score(query: TrackQuery, candidate: TrackCandidate): ScoredTrackCandidate {
        val reasons = mutableListOf<String>()

        if (!query.isrc.isNullOrBlank() && query.isrc.equals(candidate.isrc, ignoreCase = true)) {
            return ScoredTrackCandidate(
                candidate = candidate,
                score = 1.0,
                reasons = listOf("ISRC exact match"),
            )
        }

        val titleScore = similarity(
            TrackTextNormalizer.normalizeTitle(query.title),
            TrackTextNormalizer.normalizeTitle(candidate.title),
        )
        if (titleScore >= 0.9) reasons += "title close match"
        if (titleScore < 0.65) reasons += "title weak match"

        val artistScore = artistSimilarity(query.artists, candidate.artists)
        if (artistScore >= 0.9 && query.artists.isNotEmpty()) reasons += "artist close match"
        if (artistScore < 0.5 && query.artists.isNotEmpty() && candidate.artists.isNotEmpty()) {
            reasons += "artist mismatch"
        }

        val albumScore = when {
            query.album.isNullOrBlank() || candidate.album.isNullOrBlank() -> 0.5
            else -> similarity(
                TrackTextNormalizer.normalizeTitle(query.album),
                TrackTextNormalizer.normalizeTitle(candidate.album),
            )
        }

        val durationScore = durationScore(query.durationMs, candidate.durationMs)
        if (durationScore >= 0.9 && query.durationMs != null && candidate.durationMs != null) {
            reasons += "duration close match"
        }
        if (durationScore == 0.0) reasons += "duration mismatch"

        val versionScore = versionScore(query.title, candidate.title)
        if (versionScore >= 0.95) reasons += "version compatible"
        if (versionScore < 0.7) reasons += "version mismatch"

        val weighted =
            titleScore * titleWeight +
                artistScore * artistWeight +
                albumScore * albumWeight +
                durationScore * durationWeight +
                versionScore * versionWeight +
                candidate.qualityBonus()
        val cappedScore = weighted.applyConfidenceCaps(
            titleWeakMatch = titleScore < MIN_CONFIDENT_TITLE_SCORE,
            artistMismatch = query.artists.isNotEmpty() &&
                candidate.artists.isNotEmpty() &&
                artistScore < MIN_CONFIDENT_ARTIST_SCORE,
            durationMismatch = query.durationMs != null &&
                candidate.durationMs != null &&
                durationScore == 0.0,
            reasons = reasons
        )

        return ScoredTrackCandidate(
            candidate = candidate,
            score = cappedScore.coerceIn(0.0, 1.0),
            reasons = reasons.ifEmpty {
                listOf(
                    "weighted metadata similarity",
                    "title=${titleScore.formatScore()}",
                    "artist=${artistScore.formatScore()}",
                    "duration=${durationScore.formatScore()}",
                    "version=${versionScore.formatScore()}",
                )
            },
        )
    }

    private fun artistSimilarity(queryArtists: List<String>, candidateArtists: List<String>): Double {
        if (queryArtists.isEmpty() && candidateArtists.isEmpty()) return 0.5
        if (queryArtists.isEmpty() || candidateArtists.isEmpty()) return 0.0
        val query = queryArtists.flatMap(TrackTextNormalizer::splitArtists).distinct()
        val candidate = candidateArtists.flatMap(TrackTextNormalizer::splitArtists).distinct()
        if (query.isEmpty() || candidate.isEmpty()) return 0.5
        val queryCoverage = query
            .map { queryArtist -> candidate.maxOf { candidateArtist -> artistNameSimilarity(queryArtist, candidateArtist) } }
            .average()
        val candidateCoverage = candidate
            .map { candidateArtist -> query.maxOf { queryArtist -> artistNameSimilarity(queryArtist, candidateArtist) } }
            .average()
        return (queryCoverage * 0.7 + candidateCoverage * 0.3).coerceIn(0.0, 1.0)
    }

    private fun artistNameSimilarity(queryArtist: String, candidateArtist: String): Double {
        if (queryArtist == candidateArtist) return 1.0

        val queryAliases = TrackTextNormalizer.artistAliases(queryArtist)
        val candidateAliases = TrackTextNormalizer.artistAliases(candidateArtist)
        if (queryAliases.intersect(candidateAliases).isNotEmpty()) return 0.96

        return queryAliases.maxOfOrNull { queryAlias ->
            candidateAliases.maxOfOrNull { candidateAlias ->
                aliasSimilarity(queryAlias, candidateAlias)
            } ?: 0.0
        } ?: 0.0
    }

    private fun aliasSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        val shorter = minOf(left.length, right.length)
        val longer = maxOf(left.length, right.length)
        if (shorter >= MIN_CONTAINED_ARTIST_ALIAS_LENGTH &&
            (left.contains(right) || right.contains(left))
        ) {
            return 0.86
        }
        if (longer < MIN_FUZZY_ARTIST_LENGTH) return 0.0
        val distance = levenshtein(left, right)
        val ratio = distance.toDouble() / longer.toDouble()
        return if (ratio <= FUZZY_ARTIST_DISTANCE_RATIO) {
            (1.0 - ratio).coerceAtLeast(0.72)
        } else {
            0.0
        }
    }

    private fun durationScore(queryDurationMs: Long?, candidateDurationMs: Long?): Double {
        if (queryDurationMs == null || candidateDurationMs == null) return 0.5
        val delta = kotlin.math.abs(queryDurationMs - candidateDurationMs)
        return when {
            delta <= 2_000 -> 1.0
            delta <= 10_000 -> 1.0 - (delta - 2_000).toDouble() / 16_000.0
            else -> 0.0
        }.coerceIn(0.0, 1.0)
    }

    private fun versionScore(queryTitle: String, candidateTitle: String): Double {
        val queryTags = TrackTextNormalizer.versionTags(queryTitle)
        val candidateTags = TrackTextNormalizer.versionTags(candidateTitle)
        if (queryTags.isEmpty() && candidateTags.isEmpty()) return 1.0
        if (queryTags == candidateTags) return 1.0

        val strictQueryTags = queryTags - softVersionTags
        val strictCandidateTags = candidateTags - softVersionTags
        if (strictQueryTags.isEmpty() && strictCandidateTags.isEmpty()) return 0.85
        if (strictQueryTags.isEmpty() && strictCandidateTags.isNotEmpty()) return 0.55
        if (strictQueryTags.isNotEmpty() && strictCandidateTags.isEmpty()) return 0.55

        val intersection = strictQueryTags.intersect(strictCandidateTags).size.toDouble()
        val union = strictQueryTags.union(strictCandidateTags).size.toDouble()
        return if (union == 0.0) 0.85 else (intersection / union).coerceAtLeast(0.25)
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isBlank() || right.isBlank()) return 0.0
        if (left == right) return 1.0
        val distance = levenshtein(left, right)
        val maxLength = maxOf(left.length, right.length)
        return (1.0 - distance.toDouble() / maxLength.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost,
                )
            }
            val next = previous
            previous = current
            current = next
        }

        return previous[right.length]
    }

    private fun Double.formatScore(): String =
        (this * 100).toInt().toString()

    private fun Double.applyConfidenceCaps(
        titleWeakMatch: Boolean,
        artistMismatch: Boolean,
        durationMismatch: Boolean,
        reasons: MutableList<String>
    ): Double {
        var score = this

        fun cap(maxScore: Double, reason: String) {
            if (score > maxScore) {
                score = maxScore
                reasons += reason
            }
        }

        if (titleWeakMatch) {
            cap(TITLE_MISMATCH_SCORE_CAP, "title mismatch score cap")
        }
        if (artistMismatch && durationMismatch) {
            cap(ARTIST_AND_DURATION_MISMATCH_SCORE_CAP, "artist and duration mismatch score cap")
        } else {
            if (artistMismatch) {
                cap(ARTIST_MISMATCH_SCORE_CAP, "artist mismatch score cap")
            }
            if (durationMismatch) {
                cap(DURATION_MISMATCH_SCORE_CAP, "duration mismatch score cap")
            }
        }

        return score
    }

    private fun TrackCandidate.qualityBonus(): Double =
        (lyrics.lyricsQualityBonus() + artwork.artworkQualityBonus()).coerceAtMost(MAX_QUALITY_BONUS)

    private fun LyricsMetadata?.lyricsQualityBonus(): Double {
        val lyrics = this ?: return 0.0
        val hasWordTimedLyrics = lyrics.syncPrecision == SyncPrecision.WordSynced ||
            lyrics.syncPrecision == SyncPrecision.SyllableSynced ||
            LyricsType.WordSynced in lyrics.availableTypes ||
            LyricsType.SyllableSynced in lyrics.availableTypes ||
            !lyrics.wordTimedLyrics.isNullOrBlank()
        if (hasWordTimedLyrics) return WORD_TIMED_LYRICS_BONUS

        val hasLineLyrics = lyrics.syncPrecision == SyncPrecision.LineSynced ||
            LyricsType.LineSynced in lyrics.availableTypes ||
            !lyrics.plainOrLineLyrics.isNullOrBlank()
        return if (hasLineLyrics) LINE_LYRICS_BONUS else 0.0
    }

    private fun Artwork?.artworkQualityBonus(): Double {
        val artwork = this ?: return 0.0
        if (artwork.url.isBlank()) return 0.0
        val width = artwork.width?.takeIf { it > 0 }
        val height = artwork.height?.takeIf { it > 0 }
        val pixels = if (width != null && height != null) width.toLong() * height.toLong() else 0L
        return when {
            pixels >= HIGH_RES_ARTWORK_PIXELS -> HIGH_RES_ARTWORK_BONUS
            pixels >= MEDIUM_RES_ARTWORK_PIXELS -> MEDIUM_RES_ARTWORK_BONUS
            else -> ARTWORK_URL_BONUS
        }
    }

    private companion object {
        const val TITLE_MISMATCH_SCORE_CAP = 0.50
        const val ARTIST_MISMATCH_SCORE_CAP = 0.58
        const val DURATION_MISMATCH_SCORE_CAP = 0.65
        const val ARTIST_AND_DURATION_MISMATCH_SCORE_CAP = 0.55
        const val MIN_CONFIDENT_TITLE_SCORE = 0.65
        const val MIN_CONFIDENT_ARTIST_SCORE = 0.35
        const val MIN_CONTAINED_ARTIST_ALIAS_LENGTH = 3
        const val MIN_FUZZY_ARTIST_LENGTH = 5
        const val FUZZY_ARTIST_DISTANCE_RATIO = 0.2
        const val MAX_QUALITY_BONUS = 0.05
        const val WORD_TIMED_LYRICS_BONUS = 0.03
        const val LINE_LYRICS_BONUS = 0.01
        const val HIGH_RES_ARTWORK_BONUS = 0.02
        const val MEDIUM_RES_ARTWORK_BONUS = 0.01
        const val ARTWORK_URL_BONUS = 0.005
        const val HIGH_RES_ARTWORK_PIXELS = 800L * 800L
        const val MEDIUM_RES_ARTWORK_PIXELS = 300L * 300L

        val softVersionTags = setOf(
            TrackVersionTag.Explicit,
            TrackVersionTag.Clean,
            TrackVersionTag.Remaster,
        )
    }
}
