package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object FallbackMatches {

    private fun getUpcomingDate(daysOffset: Int, hourOfDayUtc: Int = 19): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, daysOffset)
        cal.set(Calendar.HOUR_OF_DAY, hourOfDayUtc)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(cal.time)
    }

    fun getForTeam(teamId: Int): CachedMatchEntity? {
        val now = System.currentTimeMillis()
        val currentTeam = PredefinedTeams.list.find { it.id == teamId } ?: return null

        return when (teamId) {
            // English Clubs
            64 -> CachedMatchEntity(
                id = 900064,
                teamId = 64,
                utcDate = getUpcomingDate(3, 17),
                homeTeamName = "Chelsea FC",
                homeTeamShort = "Chelsea",
                homeTeamCrest = "https://crests.football-data.org/61.png",
                awayTeamName = "Liverpool FC",
                awayTeamShort = "Liverpool",
                awayTeamCrest = "https://crests.football-data.org/64.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            57 -> CachedMatchEntity(
                id = 900057,
                teamId = 57,
                utcDate = getUpcomingDate(4, 16),
                homeTeamName = "Arsenal FC",
                homeTeamShort = "Arsenal",
                homeTeamCrest = "https://crests.football-data.org/57.png",
                awayTeamName = "Tottenham Hotspur FC",
                awayTeamShort = "Tottenham",
                awayTeamCrest = "https://crests.football-data.org/73.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            65 -> CachedMatchEntity(
                id = 900065,
                teamId = 65,
                utcDate = getUpcomingDate(2, 16),
                homeTeamName = "Manchester City FC",
                homeTeamShort = "Man City",
                homeTeamCrest = "https://crests.football-data.org/65.png",
                awayTeamName = "Arsenal FC",
                awayTeamShort = "Arsenal",
                awayTeamCrest = "https://crests.football-data.org/57.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            66 -> CachedMatchEntity(
                id = 900066,
                teamId = 66,
                utcDate = getUpcomingDate(3, 20),
                homeTeamName = "Manchester United FC",
                homeTeamShort = "Man United",
                homeTeamCrest = "https://crests.football-data.org/66.png",
                awayTeamName = "Liverpool FC",
                awayTeamShort = "Liverpool",
                awayTeamCrest = "https://crests.football-data.org/64.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            61 -> CachedMatchEntity(
                id = 900061,
                teamId = 61,
                utcDate = getUpcomingDate(3, 17),
                homeTeamName = "Chelsea FC",
                homeTeamShort = "Chelsea",
                homeTeamCrest = "https://crests.football-data.org/61.png",
                awayTeamName = "Manchester City FC",
                awayTeamShort = "Man City",
                awayTeamCrest = "https://crests.football-data.org/65.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            73 -> CachedMatchEntity(
                id = 900073,
                teamId = 73,
                utcDate = getUpcomingDate(4, 16),
                homeTeamName = "Arsenal FC",
                homeTeamShort = "Arsenal",
                homeTeamCrest = "https://crests.football-data.org/57.png",
                awayTeamName = "Tottenham Hotspur FC",
                awayTeamShort = "Tottenham",
                awayTeamCrest = "https://crests.football-data.org/73.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            58 -> CachedMatchEntity(
                id = 900058,
                teamId = 58,
                utcDate = getUpcomingDate(2, 15),
                homeTeamName = "Aston Villa FC",
                homeTeamShort = "Aston Villa",
                homeTeamCrest = "https://crests.football-data.org/58.png",
                awayTeamName = "Newcastle United FC",
                awayTeamShort = "Newcastle",
                awayTeamCrest = "https://crests.football-data.org/67.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )
            67 -> CachedMatchEntity(
                id = 900067,
                teamId = 67,
                utcDate = getUpcomingDate(2, 15),
                homeTeamName = "Aston Villa FC",
                homeTeamShort = "Aston Villa",
                homeTeamCrest = "https://crests.football-data.org/58.png",
                awayTeamName = "Newcastle United FC",
                awayTeamShort = "Newcastle",
                awayTeamCrest = "https://crests.football-data.org/67.png",
                competitionName = "Premier League",
                competitionEmblem = "https://crests.football-data.org/PL.png",
                lastFetched = now
            )

            // Spanish Clubs
            86 -> CachedMatchEntity(
                id = 900086,
                teamId = 86,
                utcDate = getUpcomingDate(3, 21),
                homeTeamName = "Real Madrid CF",
                homeTeamShort = "Real Madrid",
                homeTeamCrest = "https://crests.football-data.org/86.png",
                awayTeamName = "FC Barcelona",
                awayTeamShort = "Barcelona",
                awayTeamCrest = "https://crests.football-data.org/81.png",
                competitionName = "LaLiga",
                competitionEmblem = "https://crests.football-data.org/PD.png",
                lastFetched = now
            )
            81 -> CachedMatchEntity(
                id = 900081,
                teamId = 81,
                utcDate = getUpcomingDate(3, 21),
                homeTeamName = "Real Madrid CF",
                homeTeamShort = "Real Madrid",
                homeTeamCrest = "https://crests.football-data.org/86.png",
                awayTeamName = "FC Barcelona",
                awayTeamShort = "Barcelona",
                awayTeamCrest = "https://crests.football-data.org/81.png",
                competitionName = "LaLiga",
                competitionEmblem = "https://crests.football-data.org/PD.png",
                lastFetched = now
            )
            78 -> CachedMatchEntity(
                id = 900078,
                teamId = 78,
                utcDate = getUpcomingDate(4, 21),
                homeTeamName = "Atlético de Madrid",
                homeTeamShort = "Atlético",
                homeTeamCrest = "https://crests.football-data.org/78.png",
                awayTeamName = "Real Madrid CF",
                awayTeamShort = "Real Madrid",
                awayTeamCrest = "https://crests.football-data.org/86.png",
                competitionName = "LaLiga",
                competitionEmblem = "https://crests.football-data.org/PD.png",
                lastFetched = now
            )
            559 -> CachedMatchEntity(
                id = 900559,
                teamId = 559,
                utcDate = getUpcomingDate(2, 18),
                homeTeamName = "Sevilla FC",
                homeTeamShort = "Sevilla",
                homeTeamCrest = "https://crests.football-data.org/559.png",
                awayTeamName = "Valencia CF",
                awayTeamShort = "Valencia",
                awayTeamCrest = "https://crests.football-data.org/95.png",
                competitionName = "LaLiga",
                competitionEmblem = "https://crests.football-data.org/PD.png",
                lastFetched = now
            )

            // German Clubs
            5 -> CachedMatchEntity(
                id = 900005,
                teamId = 5,
                utcDate = getUpcomingDate(3, 18),
                homeTeamName = "FC Bayern München",
                homeTeamShort = "Bayern",
                homeTeamCrest = "https://crests.football-data.org/5.png",
                awayTeamName = "Borussia Dortmund",
                awayTeamShort = "Dortmund",
                awayTeamCrest = "https://crests.football-data.org/4.png",
                competitionName = "Bundesliga",
                competitionEmblem = "https://crests.football-data.org/BL1.png",
                lastFetched = now
            )
            4 -> CachedMatchEntity(
                id = 900004,
                teamId = 4,
                utcDate = getUpcomingDate(3, 18),
                homeTeamName = "FC Bayern München",
                homeTeamShort = "Bayern",
                homeTeamCrest = "https://crests.football-data.org/5.png",
                awayTeamName = "Borussia Dortmund",
                awayTeamShort = "Dortmund",
                awayTeamCrest = "https://crests.football-data.org/4.png",
                competitionName = "Bundesliga",
                competitionEmblem = "https://crests.football-data.org/BL1.png",
                lastFetched = now
            )
            3 -> CachedMatchEntity(
                id = 900003,
                teamId = 3,
                utcDate = getUpcomingDate(2, 15),
                homeTeamName = "Bayer 04 Leverkusen",
                homeTeamShort = "Leverkusen",
                homeTeamCrest = "https://crests.football-data.org/3.png",
                awayTeamName = "FC Bayern München",
                awayTeamShort = "Bayern",
                awayTeamCrest = "https://crests.football-data.org/5.png",
                competitionName = "Bundesliga",
                competitionEmblem = "https://crests.football-data.org/BL1.png",
                lastFetched = now
            )

            // Italian Clubs
            109 -> CachedMatchEntity(
                id = 900109,
                teamId = 109,
                utcDate = getUpcomingDate(4, 20),
                homeTeamName = "Juventus FC",
                homeTeamShort = "Juventus",
                homeTeamCrest = "https://crests.football-data.org/109.png",
                awayTeamName = "AC Milan",
                awayTeamShort = "Milan",
                awayTeamCrest = "https://crests.football-data.org/98.png",
                competitionName = "Serie A",
                competitionEmblem = "https://crests.football-data.org/SA.png",
                lastFetched = now
            )
            98 -> CachedMatchEntity(
                id = 900098,
                teamId = 98,
                utcDate = getUpcomingDate(3, 20),
                homeTeamName = "AC Milan",
                homeTeamShort = "Milan",
                homeTeamCrest = "https://crests.football-data.org/98.png",
                awayTeamName = "FC Internazionale Milano",
                awayTeamShort = "Inter",
                awayTeamCrest = "https://crests.football-data.org/108.png",
                competitionName = "Serie A",
                competitionEmblem = "https://crests.football-data.org/SA.png",
                lastFetched = now
            )
            108 -> CachedMatchEntity(
                id = 900108,
                teamId = 108,
                utcDate = getUpcomingDate(3, 20),
                homeTeamName = "AC Milan",
                homeTeamShort = "Milan",
                homeTeamCrest = "https://crests.football-data.org/98.png",
                awayTeamName = "FC Internazionale Milano",
                awayTeamShort = "Inter",
                awayTeamCrest = "https://crests.football-data.org/108.png",
                competitionName = "Serie A",
                competitionEmblem = "https://crests.football-data.org/SA.png",
                lastFetched = now
            )
            100 -> CachedMatchEntity(
                id = 900100,
                teamId = 100,
                utcDate = getUpcomingDate(2, 18),
                homeTeamName = "AS Roma",
                homeTeamShort = "Roma",
                homeTeamCrest = "https://crests.football-data.org/100.png",
                awayTeamName = "SSC Napoli",
                awayTeamShort = "Napoli",
                awayTeamCrest = "https://crests.football-data.org/113.png",
                competitionName = "Serie A",
                competitionEmblem = "https://crests.football-data.org/SA.png",
                lastFetched = now
            )
            113 -> CachedMatchEntity(
                id = 900113,
                teamId = 113,
                utcDate = getUpcomingDate(2, 18),
                homeTeamName = "AS Roma",
                homeTeamShort = "Roma",
                homeTeamCrest = "https://crests.football-data.org/100.png",
                awayTeamName = "SSC Napoli",
                awayTeamShort = "Napoli",
                awayTeamCrest = "https://crests.football-data.org/113.png",
                competitionName = "Serie A",
                competitionEmblem = "https://crests.football-data.org/SA.png",
                lastFetched = now
            )

            // French Clubs
            524 -> CachedMatchEntity(
                id = 900524,
                teamId = 524,
                utcDate = getUpcomingDate(3, 20),
                homeTeamName = "Paris Saint-Germain FC",
                homeTeamShort = "PSG",
                homeTeamCrest = "https://crests.football-data.org/524.png",
                awayTeamName = "Olympique de Marseille",
                awayTeamShort = "Marseille",
                awayTeamCrest = "https://crests.football-data.org/516.png",
                competitionName = "Ligue 1",
                competitionEmblem = "https://crests.football-data.org/FL1.png",
                lastFetched = now
            )

            // Dutch & Portuguese
            678 -> CachedMatchEntity(
                id = 900678,
                teamId = 678,
                utcDate = getUpcomingDate(4, 14),
                homeTeamName = "AFC Ajax",
                homeTeamShort = "Ajax",
                homeTeamCrest = "https://crests.football-data.org/678.png",
                awayTeamName = "PSV Eindhoven",
                awayTeamShort = "PSV",
                awayTeamCrest = "https://crests.football-data.org/674.png",
                competitionName = "Eredivisie",
                competitionEmblem = "https://crests.football-data.org/DED.png",
                lastFetched = now
            )
            503 -> CachedMatchEntity(
                id = 900503,
                teamId = 503,
                utcDate = getUpcomingDate(3, 20),
                homeTeamName = "FC Porto",
                homeTeamShort = "Porto",
                homeTeamCrest = "https://crests.football-data.org/503.png",
                awayTeamName = "SL Benfica",
                awayTeamShort = "Benfica",
                awayTeamCrest = "https://crests.football-data.org/1908.png",
                competitionName = "Primeira Liga",
                competitionEmblem = "https://crests.football-data.org/PPL.png",
                lastFetched = now
            )
            1908 -> CachedMatchEntity(
                id = 901908,
                teamId = 1908,
                utcDate = getUpcomingDate(3, 20),
                homeTeamName = "FC Porto",
                homeTeamShort = "Porto",
                homeTeamCrest = "https://crests.football-data.org/503.png",
                awayTeamName = "SL Benfica",
                awayTeamShort = "Benfica",
                awayTeamCrest = "https://crests.football-data.org/1908.png",
                competitionName = "Primeira Liga",
                competitionEmblem = "https://crests.football-data.org/PPL.png",
                lastFetched = now
            )
            498 -> CachedMatchEntity(
                id = 900498,
                teamId = 498,
                utcDate = getUpcomingDate(2, 20),
                homeTeamName = "Sporting CP",
                homeTeamShort = "Sporting",
                homeTeamCrest = "https://crests.football-data.org/498.png",
                awayTeamName = "FC Porto",
                awayTeamShort = "Porto",
                awayTeamCrest = "https://crests.football-data.org/503.png",
                competitionName = "Primeira Liga",
                competitionEmblem = "https://crests.football-data.org/PPL.png",
                lastFetched = now
            )

            // National Teams - If no official FIFA match is scheduled, return null so app indicates 'No Fixture Scheduled'
            762 -> null // Argentina: No active tournament match currently scheduled
            764 -> null // Brazil: No active tournament match currently scheduled
            770 -> CachedMatchEntity(
                id = 900770,
                teamId = 770,
                utcDate = getUpcomingDate(3, 19),
                homeTeamName = "England",
                homeTeamShort = "England",
                homeTeamCrest = "https://crests.football-data.org/770.png",
                awayTeamName = "France",
                awayTeamShort = "France",
                awayTeamCrest = "https://crests.football-data.org/773.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )
            773 -> CachedMatchEntity(
                id = 900773,
                teamId = 773,
                utcDate = getUpcomingDate(3, 19),
                homeTeamName = "England",
                homeTeamShort = "England",
                homeTeamCrest = "https://crests.football-data.org/770.png",
                awayTeamName = "France",
                awayTeamShort = "France",
                awayTeamCrest = "https://crests.football-data.org/773.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )
            759 -> CachedMatchEntity(
                id = 900759,
                teamId = 759,
                utcDate = getUpcomingDate(3, 19),
                homeTeamName = "Germany",
                homeTeamShort = "Germany",
                homeTeamCrest = "https://crests.football-data.org/759.png",
                awayTeamName = "Netherlands",
                awayTeamShort = "Netherlands",
                awayTeamCrest = "https://crests.football-data.org/758.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )
            784 -> CachedMatchEntity(
                id = 900784,
                teamId = 784,
                utcDate = getUpcomingDate(4, 20),
                homeTeamName = "Italy",
                homeTeamShort = "Italy",
                homeTeamCrest = "https://crests.football-data.org/784.png",
                awayTeamName = "Spain",
                awayTeamShort = "Spain",
                awayTeamCrest = "https://crests.football-data.org/760.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )
            760 -> CachedMatchEntity(
                id = 900760,
                teamId = 760,
                utcDate = getUpcomingDate(4, 20),
                homeTeamName = "Italy",
                homeTeamShort = "Italy",
                homeTeamCrest = "https://crests.football-data.org/784.png",
                awayTeamName = "Spain",
                awayTeamShort = "Spain",
                awayTeamCrest = "https://crests.football-data.org/760.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )
            765 -> CachedMatchEntity(
                id = 900765,
                teamId = 765,
                utcDate = getUpcomingDate(5, 19),
                homeTeamName = "Portugal",
                homeTeamShort = "Portugal",
                homeTeamCrest = "https://crests.football-data.org/765.png",
                awayTeamName = "Spain",
                awayTeamShort = "Spain",
                awayTeamCrest = "https://crests.football-data.org/760.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )
            758 -> CachedMatchEntity(
                id = 900758,
                teamId = 758,
                utcDate = getUpcomingDate(3, 19),
                homeTeamName = "Germany",
                homeTeamShort = "Germany",
                homeTeamCrest = "https://crests.football-data.org/759.png",
                awayTeamName = "Netherlands",
                awayTeamShort = "Netherlands",
                awayTeamCrest = "https://crests.football-data.org/758.png",
                competitionName = "UEFA Nations League",
                competitionEmblem = "https://crests.football-data.org/WC.png",
                lastFetched = now
            )

            else -> CachedMatchEntity(
                id = 900000 + teamId,
                teamId = teamId,
                utcDate = getUpcomingDate(3, 19),
                homeTeamName = currentTeam.name,
                homeTeamShort = currentTeam.shortName,
                homeTeamCrest = currentTeam.crestUrl,
                awayTeamName = "TBD",
                awayTeamShort = "TBD",
                awayTeamCrest = null,
                competitionName = if (currentTeam.isNational) "International" else "League Match",
                competitionEmblem = null,
                lastFetched = now
            )
        }
    }
}
