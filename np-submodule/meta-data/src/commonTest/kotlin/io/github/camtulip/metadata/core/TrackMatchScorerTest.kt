package io.github.camtulip.metadata.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackMatchScorerTest {
    private val scorer = TrackMatchScorer()

    @Test
    fun scoresExactTitleArtistDurationHighly() {
        val query = TrackQuery(
            title = "RUNAWAY",
            artists = listOf("OneRepublic"),
            durationMs = 144_000,
        )
        val candidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("1"),
            title = "RUNAWAY",
            artists = listOf("OneRepublic"),
            durationMs = 144_500,
        )

        val result = scorer.score(query, candidate)

        assertTrue(result.score >= 0.9, "Expected high score, got ${result.score}")
    }

    @Test
    fun penalizesSameTitleWithWrongArtistAndDurationBelowConfidenceThreshold() {
        val query = TrackQuery(
            title = "唯一",
            artists = listOf("G.E.M. 邓紫棋"),
            durationMs = 266_000,
        )
        val candidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("wrong"),
            title = "唯一",
            artists = listOf("王力宏"),
            durationMs = 237_000,
        )

        val result = scorer.score(query, candidate)

        assertTrue(result.reasons.contains("artist mismatch"))
        assertTrue(result.reasons.contains("duration mismatch"))
        assertTrue(result.score < 0.60, "Expected wrong same-title match below confidence, got ${result.score}")
    }

    @Test
    fun capsDurationMismatchEvenWhenTitleAndArtistMatch() {
        val query = TrackQuery(
            title = "唯一",
            artists = listOf("G.E.M. 邓紫棋"),
            durationMs = 266_000,
        )
        val candidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("long"),
            title = "唯一",
            artists = listOf("G.E.M."),
            durationMs = 310_000,
        )

        val result = scorer.score(query, candidate)

        assertTrue(result.reasons.contains("duration mismatch"))
        assertTrue(result.reasons.contains("duration mismatch score cap"))
        assertTrue(result.score <= 0.65, "Expected duration mismatch cap, got ${result.score}")
    }

    @Test
    fun addsQualityBonusForWordLyricsAndHighResolutionArtwork() {
        val query = TrackQuery(
            title = "唯一",
            artists = listOf("G.E.M. 邓紫棋"),
        )
        val baseCandidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("base"),
            title = "唯一",
            artists = listOf("G.E.M."),
        )
        val qualityCandidate = baseCandidate.copy(
            id = ProviderTrackId("quality"),
            artwork = Artwork(url = "https://example.com/cover.jpg", width = 1000, height = 1000),
            lyrics = LyricsMetadata(
                provider = ProviderId("fake"),
                trackId = ProviderTrackId("quality"),
                availableTypes = setOf(LyricsType.WordSynced),
                syncPrecision = SyncPrecision.WordSynced,
                wordTimedLyrics = "[0,1000]唯一",
            )
        )

        val baseScore = scorer.score(query, baseCandidate)
        val qualityScore = scorer.score(query, qualityCandidate)

        assertTrue(
            qualityScore.score > baseScore.score,
            "Expected quality candidate to score above base: base=${baseScore.score}, quality=${qualityScore.score}"
        )
    }

    @Test
    fun qualityBonusDoesNotBypassDurationMismatchCap() {
        val query = TrackQuery(
            title = "唯一",
            artists = listOf("G.E.M. 邓紫棋"),
            durationMs = 266_000,
        )
        val candidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("long-quality"),
            title = "唯一",
            artists = listOf("G.E.M."),
            durationMs = 310_000,
            artwork = Artwork(url = "https://example.com/cover.jpg", width = 1000, height = 1000),
            lyrics = LyricsMetadata(
                provider = ProviderId("fake"),
                trackId = ProviderTrackId("long-quality"),
                availableTypes = setOf(LyricsType.WordSynced),
                syncPrecision = SyncPrecision.WordSynced,
                wordTimedLyrics = "[0,1000]唯一",
            )
        )

        val result = scorer.score(query, candidate)

        assertTrue(result.reasons.contains("duration mismatch score cap"))
        assertTrue(result.score <= 0.65, "Expected duration mismatch cap, got ${result.score}")
    }

    @Test
    fun capsWeakTitleMatchBelowConfidenceThreshold() {
        val query = TrackQuery(
            title = "唯一",
            artists = listOf("G.E.M. 邓紫棋"),
            durationMs = 266_000,
        )
        val candidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("wrong-title"),
            title = "泡沫",
            artists = listOf("G.E.M."),
            durationMs = 266_000,
        )

        val result = scorer.score(query, candidate)

        assertTrue(result.reasons.contains("title weak match"))
        assertTrue(result.reasons.contains("title mismatch score cap"))
        assertTrue(result.score <= 0.50, "Expected title mismatch cap, got ${result.score}")
    }

    @Test
    fun extractsEnglishChineseArtistAliases() {
        val aliases = TrackTextNormalizer.artistAliases("G.E.M. 邓紫棋")

        assertTrue("g e m" in aliases)
        assertTrue("gem" in aliases)
        assertTrue("邓紫棋" in aliases)
    }

    @Test
    fun normalizesVersionSuffixes() {
        assertEquals(
            "song title",
            TrackTextNormalizer.normalizeTitle("Song Title (Live Version)"),
        )
    }

    @Test
    fun extractsAndScoresVersionTags() {
        val query = TrackQuery(
            title = "Song Title (Live)",
            artists = listOf("Example Artist"),
            durationMs = 180_000,
        )
        val liveCandidate = TrackCandidate(
            provider = ProviderId("fake"),
            id = ProviderTrackId("live"),
            title = "Song Title - Live Version",
            artists = listOf("Example Artist"),
            durationMs = 180_000,
        )
        val studioCandidate = liveCandidate.copy(
            id = ProviderTrackId("studio"),
            title = "Song Title",
        )

        val liveScore = scorer.score(query, liveCandidate)
        val studioScore = scorer.score(query, studioCandidate)

        assertTrue(liveScore.score > studioScore.score)
        assertTrue(studioScore.reasons.contains("version mismatch"))
    }

    @Test
    fun splitsFeaturingAndJointArtists() {
        assertEquals(
            listOf("main artist", "guest artist", "third artist", "fourth artist"),
            TrackTextNormalizer.splitArtists("Main Artist feat. Guest Artist x Third Artist & Fourth Artist"),
        )
    }

    @Test
    fun foldsCommonTraditionalChineseVariants() {
        assertEquals(
            "说好的幸福呢",
            TrackTextNormalizer.normalizeTitle("說好的幸福呢"),
        )
    }
}
