package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FootballViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FootballDatabase.getDatabase(application)
    private val repository = FootballRepository(application, database.footballDao())

    val selectedTeam: StateFlow<SelectedTeamEntity?> = repository.selectedTeam
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedTeams: StateFlow<List<SelectedTeamEntity>> = repository.selectedTeams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Automatically observe the cached match for whatever team is currently selected
    val cachedMatch: StateFlow<CachedMatchEntity?> = selectedTeam
        .flatMapLatest { team ->
            if (team != null) {
                repository.getCachedMatchFlow(team.teamId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTeams: StateFlow<List<PopularTeam>> = _searchQuery
        .map { query ->
            if (query.isBlank()) {
                PredefinedTeams.list
            } else {
                PredefinedTeams.list.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.tla.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PredefinedTeams.list)

    init {
        // If first launch and no teams selected, pick Arsenal (or Liverpool) as default favorite
        viewModelScope.launch {
            val teams = repository.selectedTeams.first()
            if (teams.isEmpty()) {
                val defaultTeam = PredefinedTeams.list.firstOrNull() ?: PredefinedTeams.list[0]
                repository.saveSelectedTeam(defaultTeam)
            }
        }

        // Trigger fresh fetch on start to keep match data completely live and up-to-date
        viewModelScope.launch {
            selectedTeam.filterNotNull().collectLatest { team ->
                refreshMatch(force = true)
            }
        }

        // Periodic background poll in app to automatically transition out of past/in-play matches
        viewModelScope.launch {
            while (true) {
                delay(60_000L) // check every minute while ViewModel is active
                val currentMatch = cachedMatch.value
                val team = selectedTeam.value
                if (currentMatch != null && team != null) {
                    val kickoffTime = parseDate(currentMatch.utcDate)?.time ?: 0L
                    if (kickoffTime > 0) {
                        val timeSinceKickoff = System.currentTimeMillis() - kickoffTime
                        if (timeSinceKickoff > 0) {
                            refreshMatch(force = true)
                        }
                    }
                }
            }
        }
    }

    private fun parseDate(utcStr: String): java.util.Date? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(utcStr)
        } catch (e: Exception) {
            try {
                val formatMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatMs.parse(utcStr)
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun selectTeam(team: PopularTeam) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            repository.saveSelectedTeam(team)
            _isRefreshing.value = false
        }
    }

    fun setActiveTeam(teamId: Int) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            repository.selectActiveTeam(teamId)
            _isRefreshing.value = false
        }
    }

    fun removeTeam(teamId: Int) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            repository.removeSelectedTeam(teamId)
            _isRefreshing.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshMatch(force: Boolean = true) {
        val team = selectedTeam.value ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            val result = repository.fetchNextMatch(team.teamId, force = force)
            result.onFailure {
                _errorMessage.value = it.message ?: "Failed to refresh match countdown data."
            }
            _isRefreshing.value = false
        }
    }

    fun saveCustomApiKey(key: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            repository.saveCustomApiKey(key.trim().ifEmpty { null })
            _isRefreshing.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
