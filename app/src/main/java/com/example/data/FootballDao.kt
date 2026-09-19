package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FootballDao {
    @Query("SELECT * FROM selected_team")
    fun getSelectedTeamsFlow(): Flow<List<SelectedTeamEntity>>

    @Query("SELECT * FROM selected_team")
    suspend fun getSelectedTeams(): List<SelectedTeamEntity>

    @Query("SELECT * FROM selected_team WHERE isActive = 1 LIMIT 1")
    fun getActiveTeamFlow(): Flow<SelectedTeamEntity?>

    @Query("SELECT * FROM selected_team WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTeam(): SelectedTeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelectedTeam(team: SelectedTeamEntity)

    @Query("DELETE FROM selected_team WHERE teamId = :teamId")
    suspend fun deleteSelectedTeam(teamId: Int)

    @Query("UPDATE selected_team SET isActive = 0")
    suspend fun clearActiveStatus()

    @Query("UPDATE selected_team SET isActive = 1 WHERE teamId = :teamId")
    suspend fun setActiveStatus(teamId: Int)

    @Query("SELECT * FROM cached_match WHERE teamId = :teamId LIMIT 1")
    fun getCachedMatchFlow(teamId: Int): Flow<CachedMatchEntity?>

    @Query("SELECT * FROM cached_match WHERE teamId = :teamId LIMIT 1")
    suspend fun getCachedMatch(teamId: Int): CachedMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMatch(match: CachedMatchEntity)

    @Query("DELETE FROM cached_match WHERE teamId = :teamId")
    suspend fun deleteCachedMatchForTeam(teamId: Int)

    @Query("DELETE FROM cached_match")
    suspend fun clearCachedMatches()
}
