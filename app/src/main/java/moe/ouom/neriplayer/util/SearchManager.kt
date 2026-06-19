package moe.ouom.neriplayer.util

import moe.ouom.neriplayer.core.api.search.MetadataLibrarySearchApi
import moe.ouom.neriplayer.core.api.search.MetadataSearchQuery
import moe.ouom.neriplayer.core.api.search.SongDetails
import moe.ouom.neriplayer.core.api.search.SongSearchInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/*
 * NeriPlayer - A unified Android player for streaming music and videos from multiple online platforms.
 * Copyright (C) 2025-2025 NeriPlayer developers
 * https://github.com/cwuom/NeriPlayer
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * File: moe.ouom.neriplayer.util/SearchManager
 * Created: 2025/8/17
 */

private const val AUTOMATIC_MATCH_SCORE = 0.70
private const val AUTOMATIC_METADATA_LOAD_TIMEOUT_MS = 4_800L
private const val AUTOMATIC_DETAIL_TIMEOUT_MS = 1_400L
private const val AUTOMATIC_DETAIL_CANDIDATE_LIMIT = 6
private const val AUTOMATIC_DETAIL_CANDIDATE_LIMIT_WITHOUT_LYRICS = 3

object SearchManager {
    private val metadataApi = MetadataLibrarySearchApi()

    data class MetadataSongMatch(
        val candidate: SongSearchInfo,
        val details: SongDetails
    )

    suspend fun search(
        keyword: String,
        durationMs: Long? = null
    ): List<SongSearchInfo> {
        NPLogger.d(
            "SearchManager",
            "try to search metadata library: $keyword, durationMs=${durationMs?.takeIf { it > 0L }}"
        )
        return try {
            metadataApi.search(keyword, page = 1, durationMs = durationMs).take(10)
        } catch (e: Exception) {
            e.throwIfCancellation()
            NPLogger.e("SearchManager", "Failed to find match", e)
            emptyList()
        }
    }

    suspend fun getSongInfo(selectedSong: SongSearchInfo): SongDetails {
        return metadataApi.getSongInfo(selectedSong)
    }

    suspend fun findBestSearchCandidate(
        songName: String,
        songArtist: String,
        durationMs: Long? = null
    ): SongSearchInfo? {
        return withAutomaticMetadataTimeout("candidate") {
            findConfidentSearchCandidates(
                MetadataSearchQuery(songName, songArtist, durationMs)
            ).firstOrNull()
        }
    }

    suspend fun findBestSongDetails(
        songName: String,
        songArtist: String,
        durationMs: Long? = null,
        requireLyrics: Boolean = false
    ): MetadataSongMatch? {
        return findBestSongDetails(
            searchQueries = listOf(MetadataSearchQuery(songName, songArtist, durationMs)),
            requireLyrics = requireLyrics
        )
    }

    suspend fun findBestSongDetails(
        searchQueries: List<MetadataSearchQuery>,
        requireLyrics: Boolean = false
    ): MetadataSongMatch? {
        return withAutomaticMetadataTimeout("details") {
            val candidates = findConfidentSearchCandidates(searchQueries)
            loadBestSongDetails(candidates, requireLyrics)
        }
    }

    suspend fun findFirstSongDetails(
        songName: String,
        songArtist: String,
        durationMs: Long? = null,
        requireLyrics: Boolean = false
    ): MetadataSongMatch? {
        return findFirstSongDetails(
            searchQueries = listOf(MetadataSearchQuery(songName, songArtist, durationMs)),
            requireLyrics = requireLyrics
        )
    }

    suspend fun findFirstSongDetails(
        searchQueries: List<MetadataSearchQuery>,
        requireLyrics: Boolean = false
    ): MetadataSongMatch? {
        return withAutomaticMetadataTimeout("first details") {
            val candidates = findConfidentSearchCandidates(searchQueries)
            loadBestSongDetails(candidates, requireLyrics)
        }
    }

    private suspend fun findConfidentSearchCandidates(
        songName: String,
        songArtist: String,
        durationMs: Long?
    ): List<SongSearchInfo> {
        return findConfidentSearchCandidates(MetadataSearchQuery(songName, songArtist, durationMs))
    }

    private suspend fun findConfidentSearchCandidates(
        searchQuery: MetadataSearchQuery
    ): List<SongSearchInfo> =
        findConfidentSearchCandidates(listOf(searchQuery))

    private suspend fun findConfidentSearchCandidates(
        searchQueries: List<MetadataSearchQuery>
    ): List<SongSearchInfo> {
        val normalizedQueries = searchQueries
            .mapNotNull { it.normalized() }
            .distinct()
        if (normalizedQueries.isEmpty()) {
            return emptyList()
        }

        val searchResults = coroutineScope {
            normalizedQueries
                .map { query ->
                    async {
                        searchMetadataCandidates(
                            songName = query.songName,
                            songArtist = query.songArtist,
                            durationMs = query.durationMs
                        )
                    }
                }
                .awaitAll()
                .flatten()
                .dedupeMetadataCandidates()
        }
        if (searchResults.isEmpty()) {
            return emptyList()
        }

        val confident = searchResults.confidentMetadataCandidates()
        if (confident.isNotEmpty()) {
            return confident
        }

        val ranked = searchResults.rankedByMatchScore()
        val bestScore = ranked.firstOrNull()?.matchScore ?: 0.0
        NPLogger.d(
            "SearchManager",
            "No confident match for ${normalizedQueries.joinToString { "${it.songName}/${it.songArtist}" }}, bestScore=$bestScore"
        )
        return emptyList()
    }

    private suspend fun loadBestSongDetails(
        candidates: List<SongSearchInfo>,
        requireLyrics: Boolean
    ): MetadataSongMatch? {
        if (candidates.isEmpty()) {
            return null
        }

        val limit = if (requireLyrics) {
            AUTOMATIC_DETAIL_CANDIDATE_LIMIT
        } else {
            AUTOMATIC_DETAIL_CANDIDATE_LIMIT_WITHOUT_LYRICS
        }
        val indexedMatches = coroutineScope {
            candidates
                .take(limit)
                .mapIndexed { index, candidate ->
                    async {
                        val details = loadSongDetails(candidate)
                        IndexedMetadataSongMatch(
                            index = index,
                            match = details?.let { MetadataSongMatch(candidate, it) }
                        )
                    }
                }
                .awaitAll()
        }

        return indexedMatches
            .asSequence()
            .filter { indexed ->
                val details = indexed.match?.details ?: return@filter false
                !requireLyrics || !details.lyric.isNullOrBlank()
            }
            .minByOrNull { it.index }
            ?.match
    }

    private suspend fun searchMetadataCandidates(
        songName: String,
        songArtist: String,
        durationMs: Long?
    ): List<SongSearchInfo> {
        NPLogger.d("SearchManager", "try to match $songName / $songArtist")

        return runCatching {
            metadataApi.search(
                songName = songName,
                songArtist = songArtist,
                durationMs = durationMs,
                enrichDetails = false
            )
        }.onFailure {
            it.throwIfCancellation()
            NPLogger.w(
                "SearchManager",
                "Failed to search metadata library for $songName / $songArtist: ${it.message}"
            )
        }.getOrDefault(emptyList())
    }

    private suspend fun loadSongDetails(candidate: SongSearchInfo): SongDetails? {
        return withTimeoutOrNull(AUTOMATIC_DETAIL_TIMEOUT_MS) {
            runCatching {
                getSongInfo(candidate)
            }.onFailure {
                it.throwIfCancellation()
                NPLogger.w(
                    "SearchManager",
                    "Failed to load metadata detail for ${candidate.providerId}:${candidate.id}: ${it.message}"
                )
            }.getOrNull()
        }
    }

    private suspend fun <T> withAutomaticMetadataTimeout(
        label: String,
        block: suspend () -> T?
    ): T? {
        val startedAt = System.currentTimeMillis()
        return withTimeoutOrNull(AUTOMATIC_METADATA_LOAD_TIMEOUT_MS) {
            block()
        }.also { result ->
            val elapsedMs = System.currentTimeMillis() - startedAt
            NPLogger.d(
                "SearchManager",
                "automatic metadata $label ${if (result == null) "finished without match" else "matched"} in ${elapsedMs}ms"
            )
        }
    }

    private fun Throwable.throwIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }
}

private data class IndexedMetadataSongMatch(
    val index: Int,
    val match: SearchManager.MetadataSongMatch?
)

internal fun List<SongSearchInfo>.confidentMetadataCandidates(
    minimumMatchScore: Double = AUTOMATIC_MATCH_SCORE
): List<SongSearchInfo> {
    val ranked = rankedByMatchScore()
    val bestScore = ranked.firstOrNull()?.matchScore ?: return emptyList()
    if (bestScore < minimumMatchScore) return emptyList()

    return ranked.filter { (it.matchScore ?: 0.0) >= minimumMatchScore }
}

private fun MetadataSearchQuery.normalized(): MetadataSearchQuery? {
    val normalizedName = songName.trim().takeIf { it.isNotBlank() } ?: return null
    return copy(
        songName = normalizedName,
        songArtist = songArtist.trim(),
        durationMs = durationMs?.takeIf { it > 0L }
    )
}

private fun List<SongSearchInfo>.dedupeMetadataCandidates(): List<SongSearchInfo> =
    groupBy { "${it.providerId ?: it.source.name}:${it.id}" }
        .values
        .mapNotNull { candidates ->
            candidates.maxWithOrNull(compareBy<SongSearchInfo> { it.matchScore ?: 0.0 })
        }
        .rankedByMatchScore()

private fun List<SongSearchInfo>.rankedByMatchScore(): List<SongSearchInfo> =
    sortedByDescending { it.matchScore ?: 0.0 }
