package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object PublicFootballScheduleFetcher {
    private const val TAG = "PublicScheduleFetcher"

    // Maps Football-Data team ID -> Pair(leagueSlug, espnTeamId)
    private val espnTeamMappings = mapOf(
        // English Premier League (eng.1)
        64 to Pair("eng.1", 364), // Liverpool
        57 to Pair("eng.1", 359), // Arsenal
        65 to Pair("eng.1", 382), // Man City
        66 to Pair("eng.1", 360), // Man United
        61 to Pair("eng.1", 363), // Chelsea
        73 to Pair("eng.1", 367), // Tottenham
        58 to Pair("eng.1", 362), // Aston Villa
        67 to Pair("eng.1", 361), // Newcastle

        // Spanish La Liga (esp.1)
        86 to Pair("esp.1", 86),   // Real Madrid
        81 to Pair("esp.1", 83),   // Barcelona
        78 to Pair("esp.1", 1068), // Atlético Madrid
        559 to Pair("esp.1", 243), // Sevilla

        // German Bundesliga (ger.1)
        5 to Pair("ger.1", 132),   // Bayern
        4 to Pair("ger.1", 124),   // Dortmund
        3 to Pair("ger.1", 131),   // Leverkusen

        // Italian Serie A (ita.1)
        109 to Pair("ita.1", 111), // Juventus
        98 to Pair("ita.1", 103),  // AC Milan
        108 to Pair("ita.1", 110), // Inter
        100 to Pair("ita.1", 104), // AS Roma
        113 to Pair("ita.1", 114), // Napoli

        // French Ligue 1 (fra.1)
        524 to Pair("fra.1", 160), // PSG

        // Dutch Eredivisie & Portugal
        678 to Pair("ned.1", 139), // Ajax
        503 to Pair("por.1", 437), // FC Porto
        1908 to Pair("por.1", 299), // Benfica
        498 to Pair("por.1", 503)  // Sporting CP
    )

    suspend fun fetchUpcomingMatch(teamId: Int): CachedMatchEntity? = withContext(Dispatchers.IO) {
        val mapping = espnTeamMappings[teamId] ?: return@withContext null
        val league = mapping.first
        val espnId = mapping.second
        val apiUrl = "https://site.api.espn.com/apis/site/v2/sports/soccer/$league/teams/$espnId"

        var connection: HttpURLConnection? = null
        try {
            val url = URL(apiUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode != 200) {
                Log.w(TAG, "ESPN API returned response code: ${connection.responseCode}")
                return@withContext null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val jsonText = reader.readText()
            reader.close()

            val root = JSONObject(jsonText)
            val teamObj = root.optJSONObject("team") ?: return@withContext null
            val nextEvents = teamObj.optJSONArray("nextEvent")
            if (nextEvents == null || nextEvents.length() == 0) {
                return@withContext null
            }

            val event = nextEvents.getJSONObject(0)
            val dateStr = event.optString("date", "")
            if (dateStr.isEmpty()) return@withContext null

            val competitions = event.optJSONArray("competitions")
            val compObj = if (competitions != null && competitions.length() > 0) competitions.getJSONObject(0) else JSONObject()
            val competitors = compObj.optJSONArray("competitors") ?: return@withContext null

            var homeName = "Home"
            var homeShort = "Home"
            var homeCrest: String? = null
            var awayName = "Away"
            var awayShort = "Away"
            var awayCrest: String? = null

            for (i in 0 until competitors.length()) {
                val c = competitors.getJSONObject(i)
                val homeAway = c.optString("homeAway", "")
                val t = c.optJSONObject("team") ?: continue
                val displayName = t.optString("displayName", t.optString("name", "Team"))
                val shortName = t.optString("shortDisplayName", t.optString("nickname", displayName))
                
                var crest: String? = null
                val logos = t.optJSONArray("logos")
                if (logos != null && logos.length() > 0) {
                    crest = logos.getJSONObject(0).optString("href", null)
                }

                if (homeAway.equals("home", ignoreCase = true)) {
                    homeName = displayName
                    homeShort = shortName
                    homeCrest = crest
                } else {
                    awayName = displayName
                    awayShort = shortName
                    awayCrest = crest
                }
            }

            val leagueObj = event.optJSONObject("league")
            val compName = leagueObj?.optString("name", "League Match") ?: "League Match"
            val compEmblem = null // Can default or be null

            CachedMatchEntity(
                id = event.optString("id", "${teamId}001").hashCode().let { if (it < 0) -it else it },
                teamId = teamId,
                utcDate = dateStr,
                homeTeamName = homeName,
                homeTeamShort = homeShort,
                homeTeamCrest = homeCrest,
                awayTeamName = awayName,
                awayTeamShort = awayShort,
                awayTeamCrest = awayCrest,
                competitionName = compName,
                competitionEmblem = compEmblem,
                lastFetched = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching public soccer schedule for team $teamId: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
