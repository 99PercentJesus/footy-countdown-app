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

    fun getCachedMatchFlow(teamId: Int): Flow<CachedMatchEntity?> = footballDao.getCachedMatchFlow(teamId)

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

        // Rate limit: Check if fetched within 24 hours
        if (!force && cached != null && (now - cached.lastFetched) < CACHE_DURATION_MS) {
            Log.d(TAG, "Using cached match for team $teamId. Cached within 24h.")
            return Result.success(cached)
        }

        // Determine API key to use
        val active = footballDao.getActiveTeam()
        val apiKey = active?.customApiKey?.trim()?.ifEmpty { null }
            ?: BuildConfig.FOOTBALL_API_KEY.trim().ifEmpty { null }
            ?: ""

        if (apiKey.isEmpty() || apiKey == "YOUR_FOOTBALL_API_KEY_HERE" || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "No valid football API key configured. Using upcoming match schedule.")
            val fallback = cached ?: FallbackMatches.getForTeam(teamId)
            if (fallback != null) {
                footballDao.insertCachedMatch(fallback)
                return Result.success(fallback)
            }
            return Result.failure(Exception("No API key configured. Please enter a valid API key from football-data.org."))
        }

        return try {
            Log.d(TAG, "Fetching match for team $teamId from network...")
            val response = apiService.getTeamMatches(teamId = teamId, apiKey = apiKey, status = null)
            Log.d(TAG, "Fetched ${response.matches.size} matches for team $teamId. Statuses: ${response.matches.map { it.status }.distinct()}")
            val nextMatch = response.matches
                .filter { (it.status == "SCHEDULED" || it.status == "TIMED" || it.status == "LIVE" || it.status == "IN_PLAY" || it.status == "PAUSED") && it.utcDate != null }
                .minByOrNull { it.utcDate ?: "" } // Nearest match

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
                Result.success(cachedMatch)
            } else {
                // No scheduled matches found via network, check fallback schedule
                val fallback = FallbackMatches.getForTeam(teamId)
                if (fallback != null) {
                    footballDao.insertCachedMatch(fallback)
                    Result.success(fallback)
                } else {
                    footballDao.deleteCachedMatchForTeam(teamId)
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network fetch unavailable for team $teamId (${e.message}). Falling back to schedule.")
            if (cached != null) {
                // Gracefully fall back to cache even if stale
                Result.success(cached)
            } else {
                val fallback = FallbackMatches.getForTeam(teamId)
                if (fallback != null) {
                    footballDao.insertCachedMatch(fallback)
                    Result.success(fallback)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
}
