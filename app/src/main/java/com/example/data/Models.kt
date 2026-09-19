package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Predefined Team Info for selection
 */
data class PopularTeam(
    val id: Int,
    val name: String,
    val shortName: String,
    val tla: String,
    val crestUrl: String,
    val isNational: Boolean,
    val primaryColorHex: String // Used for premium styling
)

/**
 * Room Entity to store the user's selected team config.
 */
@Entity(tableName = "selected_team")
data class SelectedTeamEntity(
    @PrimaryKey val teamId: Int,
    val teamName: String,
    val shortName: String,
    val tla: String,
    val crestUrl: String,
    val isNational: Boolean,
    val primaryColorHex: String,
    val isActive: Boolean = false,
    val customApiKey: String? = null // Allows overriding API key in UI
)

/**
 * Room Entity to store the cached upcoming match.
 */
@Entity(tableName = "cached_match")
data class CachedMatchEntity(
    @PrimaryKey val id: Int, // Match ID
    val teamId: Int, // The favorite team this match is for
    val utcDate: String, // ISO UTC date string (e.g. "2026-06-27T14:00:00Z")
    val homeTeamName: String,
    val homeTeamShort: String,
    val homeTeamCrest: String?,
    val awayTeamName: String,
    val awayTeamShort: String,
    val awayTeamCrest: String?,
    val competitionName: String?,
    val competitionEmblem: String?,
    val lastFetched: Long
)

object PredefinedTeams {
    val list = listOf(
        // English Clubs
        PopularTeam(64, "Liverpool FC", "Liverpool", "LIV", "https://crests.football-data.org/64.png", false, "#C8102E"),
        PopularTeam(57, "Arsenal FC", "Arsenal", "ARS", "https://crests.football-data.org/57.png", false, "#EF0107"),
        PopularTeam(65, "Manchester City FC", "Man City", "MCI", "https://crests.football-data.org/65.png", false, "#6CABDD"),
        PopularTeam(66, "Manchester United FC", "Man United", "MUN", "https://crests.football-data.org/66.png", false, "#DA291C"),
        PopularTeam(61, "Chelsea FC", "Chelsea", "CHE", "https://crests.football-data.org/61.png", false, "#034694"),
        PopularTeam(73, "Tottenham Hotspur FC", "Tottenham", "TOT", "https://crests.football-data.org/73.png", false, "#132257"),
        PopularTeam(58, "Aston Villa FC", "Aston Villa", "AVL", "https://crests.football-data.org/58.png", false, "#95BFE5"),
        PopularTeam(67, "Newcastle United FC", "Newcastle", "NEW", "https://crests.football-data.org/67.png", false, "#241F20"),

        // Spanish Clubs
        PopularTeam(86, "Real Madrid CF", "Real Madrid", "RMA", "https://crests.football-data.org/86.png", false, "#00529F"),
        PopularTeam(81, "FC Barcelona", "Barcelona", "FCB", "https://crests.football-data.org/81.png", false, "#004D98"),
        PopularTeam(78, "Atlético de Madrid", "Atlético", "ATM", "https://crests.football-data.org/78.png", false, "#CB3524"),
        PopularTeam(559, "Sevilla FC", "Sevilla", "SEV", "https://crests.football-data.org/559.png", false, "#F43333"),

        // German Clubs
        PopularTeam(5, "FC Bayern München", "Bayern", "FCB", "https://crests.football-data.org/5.png", false, "#DC052D"),
        PopularTeam(4, "Borussia Dortmund", "Dortmund", "BVB", "https://crests.football-data.org/4.png", false, "#FDE100"),
        PopularTeam(3, "Bayer 04 Leverkusen", "Leverkusen", "B04", "https://crests.football-data.org/3.png", false, "#E32221"),

        // Italian Clubs
        PopularTeam(109, "Juventus FC", "Juventus", "JUV", "https://crests.football-data.org/109.png", false, "#000000"),
        PopularTeam(98, "AC Milan", "Milan", "MIL", "https://crests.football-data.org/98.png", false, "#E31B23"),
        PopularTeam(108, "FC Internazionale Milano", "Inter", "INT", "https://crests.football-data.org/108.png", false, "#0066B2"),
        PopularTeam(100, "AS Roma", "Roma", "ROM", "https://crests.football-data.org/100.png", false, "#8E1E34"),
        PopularTeam(113, "SSC Napoli", "Napoli", "NAP", "https://crests.football-data.org/113.png", false, "#12A0D7"),

        // French Clubs
        PopularTeam(524, "Paris Saint-Germain FC", "PSG", "PSG", "https://crests.football-data.org/524.png", false, "#002F6C"),

        // Dutch & Portuguese
        PopularTeam(678, "AFC Ajax", "Ajax", "AJX", "https://crests.football-data.org/678.png", false, "#D2122E"),
        PopularTeam(503, "FC Porto", "Porto", "POR", "https://crests.football-data.org/503.png", false, "#005CA9"),
        PopularTeam(1908, "SL Benfica", "Benfica", "BEN", "https://crests.football-data.org/1908.png", false, "#E30613"),
        PopularTeam(498, "Sporting CP", "Sporting", "SCP", "https://crests.football-data.org/498.png", false, "#3F9C35"),

        // National Teams
        PopularTeam(762, "Argentina", "Argentina", "ARG", "https://crests.football-data.org/762.png", true, "#43A1D5"),
        PopularTeam(764, "Brazil", "Brazil", "BRA", "https://crests.football-data.org/764.png", true, "#FEDD00"),
        PopularTeam(770, "England", "England", "ENG", "https://crests.football-data.org/770.png", true, "#CF142B"),
        PopularTeam(773, "France", "France", "FRA", "https://crests.football-data.org/773.png", true, "#002395"),
        PopularTeam(759, "Germany", "Germany", "GER", "https://crests.football-data.org/759.png", true, "#000000"),
        PopularTeam(784, "Italy", "Italy", "ITA", "https://crests.football-data.org/784.png", true, "#0066BC"),
        PopularTeam(760, "Spain", "Spain", "ESP", "https://crests.football-data.org/760.png", true, "#C60B1E"),
        PopularTeam(765, "Portugal", "Portugal", "POR", "https://crests.football-data.org/765.png", true, "#E42518"),
        PopularTeam(758, "Netherlands", "Netherlands", "NED", "https://crests.football-data.org/758.png", true, "#F36C21")
    )
}
