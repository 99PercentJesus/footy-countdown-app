package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.ui.FootballViewModel
import com.example.ui.UpdateDialog
import com.example.data.CachedMatchEntity
import com.example.data.PopularTeam
import com.example.data.SelectedTeamEntity
import com.example.ui.theme.*
import com.example.update.AppUpdateManager
import com.example.update.UpdateUiState
import com.example.widget.CountdownAppWidgetProvider
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FootyPitchBlack
                ) {
                    FootyApp()
                }
            }
        }
    }
}

enum class FootyTab {
    FAVOURITES,
    BROWSE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootyApp(viewModel: FootballViewModel = viewModel()) {
    val context = LocalContext.current
    val selectedTeam by viewModel.selectedTeam.collectAsState()
    val selectedTeams by viewModel.selectedTeams.collectAsState()
    val cachedMatch by viewModel.cachedMatch.collectAsState()
    val filteredTeams by viewModel.filteredTeams.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var activeTab by remember { mutableStateOf(FootyTab.FAVOURITES) }
    var selectedBrowseCategory by remember { mutableStateOf("All") } // "All", "Clubs", "National"
    var showApiSettings by remember { mutableStateOf(false) }
    var apiKeyValue by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }
    val updateState by updateManager.updateState.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateUrlValue by remember { mutableStateOf(updateManager.getUpdateUrl()) }

    LaunchedEffect(selectedTeam) {
        apiKeyValue = selectedTeam?.customApiKey ?: ""
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Automatically check for OTA updates on app launch
    LaunchedEffect(Unit) {
        val targetUrl = updateManager.getUpdateUrl()
        updateManager.checkForUpdates(targetUrl)
    }

    // Show update dialog whenever an update is discovered
    LaunchedEffect(updateState) {
        if (updateState is UpdateUiState.UpdateAvailable) {
            showUpdateDialog = true
        }
    }

    // Trigger widget refresh when config changes
    LaunchedEffect(selectedTeam, cachedMatch, selectedTeams) {
        WidgetUpdater.updateAllWidgets(context)
    }

    Scaffold(
        containerColor = FootyPitchBlack,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Footy",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = FootyTextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(FootyAccentGreen, CircleShape)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshMatch(force = true) },
                        modifier = Modifier.testTag("refresh_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (isRefreshing) FootyAccentGreen else FootyTextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            showUpdateDialog = true
                            coroutineScope.launch {
                                updateManager.checkForUpdates(updateUrlValue.ifBlank { null })
                            }
                        },
                        modifier = Modifier.testTag("ota_update_action")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Check for updates",
                            tint = FootyAccentGreen
                        )
                    }
                    IconButton(
                        onClick = { showApiSettings = !showApiSettings },
                        modifier = Modifier.testTag("api_settings_toggle")
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (showApiSettings) FootyAccentGreenDark else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(1.dp, FootySurfaceBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "API KEY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showApiSettings) FootyAccentGreen else FootyTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FootyPitchBlack,
                    titleContentColor = FootyTextPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Token Settings Modal/Panel
            item {
                AnimatedVisibility(
                    visible = showApiSettings,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = FootySurfaceDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FootySurfaceBorder, RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "API Key Configuration",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = FootyTextPrimary
                                )
                                Text(
                                    text = "football-data.org",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = FootyAccentGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Enter your custom token from football-data.org to avoid shared API rate limits.",
                                fontSize = 12.sp,
                                color = FootyTextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val keyboardController = LocalSoftwareKeyboardController.current
                            OutlinedTextField(
                                value = apiKeyValue,
                                onValueChange = { apiKeyValue = it },
                                label = { Text("API X-Auth-Token") },
                                placeholder = { Text("Enter token") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input"),
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle key visibility",
                                            tint = FootyTextSecondary
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboardController?.hide()
                                    viewModel.saveCustomApiKey(apiKeyValue)
                                    Toast.makeText(context, "API Key Updated!", Toast.LENGTH_SHORT).show()
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FootyAccentGreen,
                                    unfocusedBorderColor = FootySurfaceBorder,
                                    focusedLabelColor = FootyAccentGreen,
                                    cursorColor = FootyAccentGreen
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.saveCustomApiKey(apiKeyValue)
                                    Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FootyAccentGreenDark,
                                    contentColor = FootyAccentGreen
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Token", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = FootySurfaceBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))

                            // Over-The-Air (OTA) Updates section
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "In-App Updates (OTA)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = FootyTextPrimary
                                )
                                Text(
                                    text = "v${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = FootyAccentGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Update over the air without Google Play Store. Supports custom JSON manifests or GitHub release URLs.",
                                fontSize = 12.sp,
                                color = FootyTextSecondary,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = updateUrlValue,
                                onValueChange = { updateUrlValue = it },
                                label = { Text("Update URL or GitHub Releases API") },
                                placeholder = { Text("https://example.com/update_manifest.json") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("update_url_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FootyAccentGreen,
                                    unfocusedBorderColor = FootySurfaceBorder,
                                    focusedLabelColor = FootyAccentGreen,
                                    cursorColor = FootyAccentGreen
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        updateManager.setUpdateUrl(updateUrlValue)
                                        Toast.makeText(context, "Update URL saved!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, FootySurfaceBorder)
                                ) {
                                    Text("Save URL", color = FootyTextPrimary)
                                }

                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        updateManager.setUpdateUrl(updateUrlValue)
                                        showUpdateDialog = true
                                        coroutineScope.launch {
                                            updateManager.checkForUpdates(updateUrlValue.ifBlank { null })
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = FootyAccentGreenDark,
                                        contentColor = FootyAccentGreen
                                    ),
                                    modifier = Modifier.weight(1.3f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // TOP HERO SECTION (Match Hero from Mockup 1i or Collapsed Strip from 1j)
            item {
                if (activeTab == FootyTab.FAVOURITES) {
                    MatchHeroCard(
                        selectedTeam = selectedTeam,
                        cachedMatch = cachedMatch,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshMatch(force = true) }
                    )
                } else {
                    // In Browse mode: Collapses to a sleek single-line strip so catalogue gets the room (Mockup 1j)
                    MatchHeroCollapsedStrip(
                        selectedTeam = selectedTeam,
                        cachedMatch = cachedMatch,
                        onExpand = { activeTab = FootyTab.FAVOURITES }
                    )
                }
            }

            // SEGMENTED TABS: "Favourites" vs "Browse" (Mockup 1i / 1j)
            item {
                SegmentedTabs(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it }
                )
            }

            // CONTENT BASED ON ACTIVE TAB
            if (activeTab == FootyTab.FAVOURITES) {
                // FAVORITES TAB (Mockup 1i)
                item {
                    FavoritesTabContent(
                        selectedTeams = selectedTeams,
                        activeTeamId = selectedTeam?.teamId,
                        onSelectActive = { teamId ->
                            viewModel.setActiveTeam(teamId)
                            Toast.makeText(context, "Active team set for widget!", Toast.LENGTH_SHORT).show()
                        },
                        onRemove = { teamId ->
                            viewModel.removeTeam(teamId)
                            Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        },
                        onGoToBrowse = { activeTab = FootyTab.BROWSE }
                    )
                }
            } else {
                // BROWSE TAB (Mockup 1j)
                item {
                    BrowseTabHeader(
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { viewModel.updateSearchQuery(it) },
                        selectedCategory = selectedBrowseCategory,
                        onCategorySelected = { selectedBrowseCategory = it }
                    )
                }

                item {
                    val filteredByCategory = when (selectedBrowseCategory) {
                        "Clubs" -> filteredTeams.filter { !it.isNational }
                        "National" -> filteredTeams.filter { it.isNational }
                        else -> filteredTeams
                    }

                    BrowseTabList(
                        teams = filteredByCategory,
                        favoriteTeamIds = selectedTeams.map { it.teamId },
                        activeTeamId = selectedTeam?.teamId,
                        onToggleFavorite = { team ->
                            val isAlreadyFav = selectedTeams.any { it.teamId == team.id }
                            if (isAlreadyFav) {
                                viewModel.removeTeam(team.id)
                                Toast.makeText(context, "Removed ${team.shortName} from favorites", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.selectTeam(team)
                                Toast.makeText(context, "Added ${team.shortName} to favorites!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        if (showUpdateDialog) {
            UpdateDialog(
                updateManager = updateManager,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}

// -----------------------------------------------------------------------------------------
// MATCH HERO CARD (Mockup 1i & 1a)
// -----------------------------------------------------------------------------------------
@Composable
fun MatchHeroCard(
    selectedTeam: SelectedTeamEntity?,
    cachedMatch: CachedMatchEntity?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    if (selectedTeam == null) {
        // Empty State card (Mockup 1h bottom)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FootySurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FootySurfaceBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Footy",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = FootyAccentGreen,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "+ Pick a team to follow",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FootyTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap on Browse below to choose your club or national team.",
                    fontSize = 12.sp,
                    color = FootyTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (cachedMatch == null) {
        // No Fixture Scheduled (Mockup 1h center)
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = FootySurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FootySurfaceBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedTeam.shortName.uppercase(Locale.getDefault()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FootyAccentGreen,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = FootyTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No fixture scheduled",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FootyTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nothing on the calendar for the next 30 days. Footy will update the moment fixtures are published.",
                    fontSize = 12.sp,
                    color = FootyTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Full Hero Fixture Card (Mockup 1i & 1a)
        var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                currentTime = System.currentTimeMillis()
            }
        }

        val kickoffDate = remember(cachedMatch.utcDate) { parseUtcDate(cachedMatch.utcDate) }
        val diffMs = remember(kickoffDate, currentTime) {
            (kickoffDate?.time ?: 0L) - currentTime
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = FootySurfaceDark),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FootySurfaceBorder, RoundedCornerShape(28.dp))
                .testTag("match_hero_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Row: Competition & Matchday / Active Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        cachedMatch.competitionEmblem?.let { emblemUrl ->
                            if (emblemUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = emblemUrl,
                                    contentDescription = "Competition Emblem",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(end = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = cachedMatch.competitionName?.uppercase(Locale.getDefault()) ?: "UPCOMING MATCH",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = FootyTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(FootyAccentGreenDark, RoundedCornerShape(100.dp))
                            .border(1.dp, FootyAccentGreenBorder, RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = FootyAccentGreen,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Teams Face-Off: Home vs Away
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home Team
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        TeamCrestImage(
                            crestUrl = cachedMatch.homeTeamCrest ?: "",
                            shortName = cachedMatch.homeTeamShort,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cachedMatch.homeTeamShort,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = FootyTextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "HOME",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = FootyTextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // VS Divider
                    Text(
                        text = "VS",
                        color = FootyTextSubtle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Away Team
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        TeamCrestImage(
                            crestUrl = cachedMatch.awayTeamCrest ?: "",
                            shortName = cachedMatch.awayTeamShort,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cachedMatch.awayTeamShort,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = FootyTextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "AWAY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = FootyTextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4-Block Countdown Timer (Mockup 1a & 1i)
                if (diffMs <= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FootyStatusRedBg, RoundedCornerShape(14.dp))
                            .border(1.dp, FootyStatusRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "IN PLAY · SECOND HALF",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = FootyStatusRed,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    val days = diffMs / (24 * 60 * 60 * 1000)
                    val hours = (diffMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                    val minutes = (diffMs % (60 * 60 * 1000)) / (60 * 1000)
                    val seconds = (diffMs % (60 * 1000)) / 1000

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DigitBlock(value = String.format(Locale.US, "%02d", days), label = "DAYS", modifier = Modifier.weight(1f))
                        DigitBlock(value = String.format(Locale.US, "%02d", hours), label = "HRS", modifier = Modifier.weight(1f))
                        DigitBlock(value = String.format(Locale.US, "%02d", minutes), label = "MIN", modifier = Modifier.weight(1f))
                        DigitBlock(value = String.format(Locale.US, "%02d", seconds), label = "SEC", modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Kickoff Date & Time Bar
                kickoffDate?.let {
                    val kickoffFormat = SimpleDateFormat("EEE d MMM · HH:mm", Locale.US)
                    Text(
                        text = kickoffFormat.format(it).uppercase(Locale.US),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FootyTextSecondary,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// Collapsed single-line match strip for Browse mode (Mockup 1j)
@Composable
fun MatchHeroCollapsedStrip(
    selectedTeam: SelectedTeamEntity?,
    cachedMatch: CachedMatchEntity?,
    onExpand: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FootySurfaceDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FootySurfaceBorder, RoundedCornerShape(16.dp))
            .clickable { onExpand() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (selectedTeam != null && cachedMatch != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamCrestImage(
                        crestUrl = selectedTeam.crestUrl,
                        shortName = selectedTeam.shortName,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${cachedMatch.homeTeamShort} v ${cachedMatch.awayTeamShort}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = FootyTextPrimary
                        )
                        val kickoff = parseUtcDate(cachedMatch.utcDate)?.let {
                            SimpleDateFormat("EEE d MMM · HH:mm", Locale.US).format(it)
                        } ?: ""
                        Text(
                            text = "${cachedMatch.competitionName?.take(8) ?: "MATCH"} · $kickoff",
                            fontSize = 10.sp,
                            color = FootyTextSecondary
                        )
                    }
                }

                val kickoffDate = parseUtcDate(cachedMatch.utcDate)
                val diffMs = (kickoffDate?.time ?: 0L) - System.currentTimeMillis()
                if (diffMs > 0) {
                    val days = diffMs / (24 * 60 * 60 * 1000)
                    val hours = (diffMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
                    val minutes = (diffMs % (60 * 60 * 1000)) / (60 * 1000)
                    val seconds = (diffMs % (60 * 1000)) / 1000

                    Text(
                        text = String.format(Locale.US, "%02d:%02d:%02d:%02d", days, hours, minutes, seconds),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = FootyAccentGreen
                    )
                }
            } else {
                Text(
                    text = "No team selected yet",
                    fontSize = 12.sp,
                    color = FootyTextSecondary
                )
                Text(
                    text = "Select Team",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FootyAccentGreen
                )
            }
        }
    }
}

@Composable
fun DigitBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(FootySurfaceSubtle, RoundedCornerShape(12.dp))
            .border(1.dp, FootySurfaceBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = FootyAccentGreen,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = FootyTextMuted,
            letterSpacing = 0.5.sp
        )
    }
}

// -----------------------------------------------------------------------------------------
// SEGMENTED TABS (Mockup 1i & 1j)
// -----------------------------------------------------------------------------------------
@Composable
fun SegmentedTabs(
    activeTab: FootyTab,
    onTabSelected: (FootyTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FootySurfaceDark, RoundedCornerShape(100.dp))
            .border(1.dp, FootySurfaceBorder, RoundedCornerShape(100.dp))
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Favourites Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (activeTab == FootyTab.FAVOURITES) FootyAccentGreenDark else Color.Transparent)
                    .clickable { onTabSelected(FootyTab.FAVOURITES) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Favourites",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (activeTab == FootyTab.FAVOURITES) FootyAccentGreen else FootyTextSecondary
                )
            }

            // Browse Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (activeTab == FootyTab.BROWSE) FootySurfaceBorder else Color.Transparent)
                    .clickable { onTabSelected(FootyTab.BROWSE) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Browse",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (activeTab == FootyTab.BROWSE) FootyTextPrimary else FootyTextSecondary
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// FAVORITES TAB CONTENT (Mockup 1i)
// -----------------------------------------------------------------------------------------
@Composable
fun FavoritesTabContent(
    selectedTeams: List<SelectedTeamEntity>,
    activeTeamId: Int?,
    onSelectActive: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onGoToBrowse: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YOUR TEAMS · ${selectedTeams.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = FootyTextSecondary
            )
            Text(
                text = "+ Add more",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FootyAccentGreen,
                modifier = Modifier.clickable { onGoToBrowse() }
            )
        }

        if (selectedTeams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No favourite teams added yet.",
                        color = FootyTextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onGoToBrowse,
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FootyAccentGreenDark,
                            contentColor = FootyAccentGreen
                        )
                    ) {
                        Text("Browse Catalog")
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                selectedTeams.forEach { team ->
                    val isActive = team.teamId == activeTeamId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FootySurfaceDark)
                            .border(
                                width = 1.dp,
                                color = if (isActive) FootyAccentGreenBorder else FootySurfaceBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TeamCrestImage(
                            crestUrl = team.crestUrl,
                            shortName = team.shortName,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = team.shortName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = FootyTextPrimary
                            )
                            Text(
                                text = team.tla,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = FootyTextSecondary
                            )
                        }

                        // Active badge vs "Set active" button (Mockup 1i)
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .background(FootyAccentGreenDark, RoundedCornerShape(100.dp))
                                    .border(1.dp, FootyAccentGreenBorder, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = FootyAccentGreen,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSelectActive(team.teamId) },
                                shape = RoundedCornerShape(100.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = FootyTextSecondary
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(FootySurfaceBorder)
                                ),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = "Set active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Remove icon
                        IconButton(
                            onClick = { onRemove(team.teamId) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Team",
                                tint = FootyTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// BROWSE TAB HEADER & LIST (Mockup 1j)
// -----------------------------------------------------------------------------------------
@Composable
fun BrowseTabHeader(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // M3 Search Field (Mockup 1j)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search club or national team...", color = FootyTextMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = FootyTextSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = FootyTextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FootySurfaceDark,
                unfocusedContainerColor = FootySurfaceDark,
                focusedBorderColor = FootyAccentGreen,
                unfocusedBorderColor = FootySurfaceBorder,
                cursorColor = FootyAccentGreen,
                focusedTextColor = FootyTextPrimary,
                unfocusedTextColor = FootyTextPrimary
            ),
            shape = RoundedCornerShape(100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("browse_search_field")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Pills (All / Clubs / National) (Mockup 1j)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("All", "Clubs", "National").forEach { category ->
                val isSelected = selectedCategory == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (isSelected) FootyAccentGreenDark else FootySurfaceDark)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) FootyAccentGreenBorder else FootySurfaceBorder,
                            shape = RoundedCornerShape(100.dp)
                        )
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = category,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) FootyAccentGreen else FootyTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun BrowseTabList(
    teams: List<PopularTeam>,
    favoriteTeamIds: List<Int>,
    activeTeamId: Int?,
    onToggleFavorite: (PopularTeam) -> Unit
) {
    if (teams.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No teams matching your search.",
                color = FootyTextSecondary,
                fontSize = 13.sp
            )
        }
    } else {
        val clubTeams = teams.filter { !it.isNational }
        val nationalTeams = teams.filter { it.isNational }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (clubTeams.isNotEmpty()) {
                TeamCategorySection(
                    title = "CLUB TEAMS · ${clubTeams.size}",
                    teams = clubTeams,
                    favoriteTeamIds = favoriteTeamIds,
                    onToggleFavorite = onToggleFavorite
                )
            }

            if (nationalTeams.isNotEmpty()) {
                TeamCategorySection(
                    title = "NATIONAL TEAMS · ${nationalTeams.size}",
                    teams = nationalTeams,
                    favoriteTeamIds = favoriteTeamIds,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
fun TeamCategorySection(
    title: String,
    teams: List<PopularTeam>,
    favoriteTeamIds: List<Int>,
    onToggleFavorite: (PopularTeam) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = FootyTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            teams.forEach { team ->
                val isFav = favoriteTeamIds.contains(team.id)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(FootySurfaceDark)
                        .border(1.dp, FootySurfaceBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TeamCrestImage(
                        crestUrl = team.crestUrl,
                        shortName = team.shortName,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = team.shortName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = FootyTextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = team.tla,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = FootyTextSecondary
                            )
                            if (team.isNational) {
                                Text(
                                    text = " · National",
                                    fontSize = 11.sp,
                                    color = FootyTextMuted
                                )
                            }
                        }
                    }

                    // Saved pill vs Plus button (Mockup 1j)
                    if (isFav) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(FootyAccentGreenDark)
                                .border(1.dp, FootyAccentGreenBorder, RoundedCornerShape(100.dp))
                                .clickable { onToggleFavorite(team) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = FootyAccentGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Saved",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FootyAccentGreen
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { onToggleFavorite(team) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(FootySurfaceSubtle, CircleShape)
                                .border(1.dp, FootySurfaceBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add to favourites",
                                tint = FootyTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// REUSABLE CREST IMAGE COMPONENT
// -----------------------------------------------------------------------------------------
@Composable
fun TeamCrestImage(crestUrl: String, shortName: String, modifier: Modifier = Modifier) {
    val cleanShort = shortName.filter { it.isLetterOrDigit() }
    val monogram = cleanShort.take(2).uppercase().ifEmpty { "FT" }

    Box(
        modifier = modifier
            .background(FootySurfaceSubtle, CircleShape)
            .border(1.dp, FootySurfaceBorder, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (crestUrl.isNotBlank()) {
            val painter = rememberAsyncImagePainter(model = crestUrl)
            val state = painter.state

            Image(
                painter = painter,
                contentDescription = "$shortName crest",
                modifier = Modifier.fillMaxSize()
            )

            // If image is still loading or failed to load, display the high-contrast club monogram
            if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Error) {
                Text(
                    text = monogram,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = FootyAccentGreen,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Explicit empty crest: show monogram
            Text(
                text = monogram,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = FootyAccentGreen,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun parseUtcDate(utcStr: String): Date? {
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
