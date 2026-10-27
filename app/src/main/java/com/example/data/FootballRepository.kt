package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class FootballRepository(
    private val context: Context,
    private val footballDao: FootballDao,
    private val apiService: FootballApiService = FootballRetrofitInstance.apiService
) {
    companion object {
        private const val TAG = "FootballRepository"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    val selectedTeam: Flow<SelectedTeamEntity?> = footballDao.getActiveTeamFlow()
    val selectedTeams: Flow<List<SelectedTeamEntity>> = footballDao.getSelectedTeamsFlow()

    fun getCachedMatchFlow(teamId: Int): Flow<CachedMatchEntity?> = kotlinx.coroutines.flow.flow {
        // Emit current cached if not expired
        val initial = footballDao.getCachedMatch(teamId)
        val now = System.currentTimeMillis()
        if (initial != null) {
            val kickoff = parseUtcMillis(initial.utcDate)
            if (kickoff > 0 && (now - kickoff) > (150 * 60 * 1000L)) {
                // Discard expired match from DB
                footballDao.deleteCachedMatchForTeam(teamId)
                emit(null)
            } else {
                emit(initial)
            }
        } else {
            emit(null)
        }
        // Then collect changes
        footballDao.getCachedMatchFlow(teamId).collect { match ->
            if (match != null) {
                val kickoff = parseUtcMillis(match.utcDate)
                val currNow = System.currentTimeMillis()
                if (kickoff > 0 && (currNow - kickoff) > (150 * 60 * 1000L)) {
                    footballDao.deleteCachedMatchForTeam(teamId)
                    emit(null)
                } else {
                    emit(match)
                }
            } else {
                emit(null)
            }
        }
    }

    /**
     * Add a favorite team. Sets it as active if there is no current active team.
     */
    suspend fun saveSelectedTeam(team: PopularTeam, customApiKey: String? = null) {
        val currentActive = footballDao.getActiveTeam()
        val keyToUse = customApiKey ?: currentActive?.customApiKey
        val makeActive = currentActive == null

        val entity = SelectedTeamEntity(
            teamId = team.id,
            teamName = team.name,
            shortName = team.shortName,
            tla = team.tla,
            crestUrl = team.crestUrl,
            isNational = team.isNational,
            primaryColorHex = team.primaryColorHex,
            isActive = makeActive,
            customApiKey = keyToUse
        )
        footballDao.insertSelectedTeam(entity)

        // If it was marked active, fetch the match
        if (makeActive) {
            fetchNextMatch(team.id, force = true)
        } else {
            // Also fetch to warm up cached match for this team
            fetchNextMatch(team.id, force = false)
        }
    }

    /**
     * Set a team as the active team.
     */
    suspend fun selectActiveTeam(teamId: Int) {
        footballDao.clearActiveStatus()
        footballDao.setActiveStatus(teamId)
        fetchNextMatch(teamId, force = true)
    }

    /**
     * Remove a team from selection.
     */
    suspend fun removeSelectedTeam(teamId: Int) {
        val currentTeams = footballDao.getSelectedTeams()
        val removedTeam = currentTeams.find { it.teamId == teamId }
        footballDao.deleteSelectedTeam(teamId)
        footballDao.deleteCachedMatchForTeam(teamId)

        if (removedTeam?.isActive == true) {
            val remaining = currentTeams.filter { it.teamId != teamId }
            if (remaining.isNotEmpty()) {
                footballDao.setActiveStatus(remaining.first().teamId)
                fetchNextMatch(remaining.first().teamId, force = true)
            }
        }
    }

    /**
     * Save custom API key across all stored selections.
     */
    suspend fun saveCustomApiKey(key: String?) {
        val currentTeams = footballDao.getSelectedTeams()
        for (team in currentTeams) {
            val updated = team.copy(customApiKey = key)
            footballDao.insertSelectedTeam(updated)
        }
        val active = footballDao.getActiveTeam()
        if (active != null) {
            fetchNextMatch(active.teamId, force = true)
        }
    }

    /**
     * Fetch the next match from network, subject to the once-per-day rate limit
     * unless [force] is true.
     */
    suspend fun fetchNextMatch(teamId: Int, force: Boolean = false): Result<CachedMatchEntity?> {
        val cached = footballDao.getCachedMatch(teamId)
        val now = System.currentTimeMillis()

        // Helper to check if cached match is still valid/in future
        val isExpired = cached?.let {
            val kickoff = parseUtcMillis(it.utcDate)
            kickoff > 0 && (now - kickoff) > (150 * 60 * 1000L) // > 2.5 hours past kickoff
        } ?: false

        // Rate limit: Check if fetched within 24 hours, provided it isn't expired
        if (!force && cached != null && !isExpired && (now - cached.lastFetched) < CACHE_DURATION_MS) {
            Log.d(TAG, "Using cached match for team $teamId. Cached within 24h.")
            return Result.success(cached)
        }

        // Determine API key to use
        val active = footballDao.getActiveTeam()
        val apiKey = active?.customApiKey?.trim()?.ifEmpty { null }
            ?: BuildConfig.FOOTBALL_API_KEY.trim().ifEmpty { null }
            ?: ""

        // 1. If valid football-data.org API key, attempt primary API
        if (apiKey.isNotEmpty() && apiKey != "YOUR_FOOTBALL_API_KEY_HERE" && apiKey != "MY_GEMINI_API_KEY") {
            try {
                Log.d(TAG, "Fetching match for team $teamId from network...")
                val response = apiService.getTeamMatches(teamId = teamId, apiKey = apiKey, status = null)
                val nextMatch = response.matches
                    .filter { (it.status == "SCHEDULED" || it.status == "TIMED" || it.status == "LIVE" || it.status == "IN_PLAY" || it.status == "PAUSED") && it.utcDate != null }
                    .minByOrNull { it.utcDate ?: "" }

                if (nextMatch != null) {
                    val cachedMatch = CachedMatchEntity(
                        id = nextMatch.id ?: (1000000 + teamId),
                        teamId = teamId,
                        utcDate = nextMatch.utcDate ?: "",
                        homeTeamName = nextMatch.homeTeam?.name ?: "TBD",
                        homeTeamShort = nextMatch.homeTeam?.shortName ?: nextMatch.homeTeam?.name ?: "TBD",
                        homeTeamCrest = nextMatch.homeTeam?.crest,
                        awayTeamName = nextMatch.awayTeam?.name ?: "TBD",
                        awayTeamShort = nextMatch.awayTeam?.shortName ?: nextMatch.awayTeam?.name ?: "TBD",
                        awayTeamCrest = nextMatch.awayTeam?.crest,
                        competitionName = nextMatch.competition?.name,
                        competitionEmblem = nextMatch.competition?.emblem,
                        lastFetched = now
                    )
                    footballDao.insertCachedMatch(cachedMatch)
                    return Result.success(cachedMatch)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Primary API fetch failed for team $teamId: ${e.message}")
            }
        }

        // 2. Fetch from Public Football Schedule (real-time, no key required)
        try {
            val publicMatch = PublicFootballScheduleFetcher.fetchUpcomingMatch(teamId)
            if (publicMatch != null) {
                Log.d(TAG, "Successfully fetched live schedule from public feed: ${publicMatch.homeTeamShort} vs ${publicMatch.awayTeamShort} at ${publicMatch.utcDate}")
                footballDao.insertCachedMatch(publicMatch)
                return Result.success(publicMatch)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public schedule fetch failed for team $teamId: ${e.message}")
        }

        // 3. Fallback: If cached match exists and isn't expired, retain it
        if (cached != null && !isExpired) {
            return Result.success(cached)
        }

        // 4. Generate dynamic upcoming fallback match
        val fallback = FallbackMatches.getForTeam(teamId)
        if (fallback != null) {
            footballDao.insertCachedMatch(fallback)
            return Result.success(fallback)
        }

        return Result.failure(Exception("No upcoming match schedule available."))
    }

    private fun parseUtcMillis(utcStr: String): Long {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(utcStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val sdf2 = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdf2.parse(utcStr)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }
}
