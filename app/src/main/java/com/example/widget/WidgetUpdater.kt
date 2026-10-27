package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import coil.imageLoader
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.R
import com.example.data.CachedMatchEntity
import com.example.data.FootballDatabase
import com.example.data.FootballRepository
import com.example.data.SelectedTeamEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val MATCH_WINDOW_MS = 150 * 60 * 1000L // 2.5 hours maximum in-play window
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    suspend fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val thisWidget = ComponentName(context, CountdownAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            if (appWidgetIds == null || appWidgetIds.isEmpty()) {
                return
            }

            // Fetch active team and cached match
            val db = FootballDatabase.getDatabase(context)
            val dao = db.footballDao()
            val selectedTeam = dao.getActiveTeam()
            var cachedMatch = selectedTeam?.let { dao.getCachedMatch(it.teamId) }

            // Auto-refresh stale matches: if match kickoff has passed or match is older than 2.5 hours past kickoff
            if (selectedTeam != null && cachedMatch != null) {
                val matchKickoff = parseUtcDate(cachedMatch.utcDate)?.time ?: 0L
                val timeSinceKickoff = System.currentTimeMillis() - matchKickoff
                if (timeSinceKickoff > 0) {
                    try {
                        val repository = FootballRepository(context.applicationContext, dao)
                        val fetchResult = repository.fetchNextMatch(selectedTeam.teamId, force = true)
                        val updatedMatch = fetchResult.getOrNull()
                        if (updatedMatch != null) {
                            cachedMatch = updatedMatch
                        } else if (timeSinceKickoff > MATCH_WINDOW_MS) {
                            dao.deleteCachedMatchForTeam(selectedTeam.teamId)
                            cachedMatch = null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed auto-refreshing match after kickoff: ${e.message}")
                    }
                }
            }

            // Pre-load bitmaps for the match
            var homeBitmap: Bitmap? = null
            var awayBitmap: Bitmap? = null
            var compBitmap: Bitmap? = null

            if (cachedMatch != null) {
                val homeCrest = cachedMatch.homeTeamCrest
                if (!homeCrest.isNullOrEmpty()) {
                    homeBitmap = bitmapCache[homeCrest] ?: withContext(Dispatchers.IO) {
                        loadBitmapFromUrl(context, homeCrest)
                    }?.also { bitmapCache[homeCrest] = it }
                }
                if (homeBitmap == null) {
                    homeBitmap = createMonogramBitmap(cachedMatch.homeTeamShort)
                }

                val awayCrest = cachedMatch.awayTeamCrest
                if (!awayCrest.isNullOrEmpty()) {
                    awayBitmap = bitmapCache[awayCrest] ?: withContext(Dispatchers.IO) {
                        loadBitmapFromUrl(context, awayCrest)
                    }?.also { bitmapCache[awayCrest] = it }
                }
                if (awayBitmap == null) {
                    awayBitmap = createMonogramBitmap(cachedMatch.awayTeamShort)
                }

                val compEmblemUrl = cachedMatch.competitionEmblem ?: ""
                if (compEmblemUrl.isNotEmpty()) {
                    compBitmap = bitmapCache[compEmblemUrl] ?: withContext(Dispatchers.IO) {
                        loadBitmapFromUrl(context, compEmblemUrl)
                    }?.also { bitmapCache[compEmblemUrl] = it }
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Update each widget based on its dimension/options
            for (appWidgetId in appWidgetIds) {
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) ?: 180
                val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110

                val views = createSizedRemoteViews(
                    context = context,
                    minWidth = minWidth,
                    minHeight = minHeight,
                    selectedTeam = selectedTeam,
                    cachedMatch = cachedMatch,
                    homeBitmap = homeBitmap,
                    awayBitmap = awayBitmap,
                    compBitmap = compBitmap,
                    pendingIntent = pendingIntent
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets", e)
        }
    }

    private fun createSizedRemoteViews(
        context: Context,
        minWidth: Int,
        minHeight: Int,
        selectedTeam: SelectedTeamEntity?,
        cachedMatch: CachedMatchEntity?,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?,
        compBitmap: Bitmap?,
        pendingIntent: PendingIntent
    ): RemoteViews {
        // Size Categories:
        // 2x2 Square: minWidth < 200 && minHeight < 170
        // 4x4 / 3x3 Large: minHeight >= 180 (regardless of width, tall/square showcase)
        // 3x2 / 4x2 Wide: minWidth >= 200 && minHeight < 180
        val isCompact2x2 = minWidth < 200 && minHeight < 180
        val isLarge4x4 = minHeight >= 180

        val layoutId = when {
            isCompact2x2 -> R.layout.widget_countdown_2x2
            isLarge4x4 -> R.layout.widget_countdown_4x4
            else -> R.layout.widget_countdown_4x2
        }

        val views = RemoteViews(context.packageName, layoutId)

        if (isCompact2x2) {
            populate2x2View(views, selectedTeam, cachedMatch, homeBitmap, awayBitmap, context)
            views.setOnClickPendingIntent(R.id.widget_container_2x2, pendingIntent)
        } else if (isLarge4x4) {
            populate4x4View(views, selectedTeam, cachedMatch, homeBitmap, awayBitmap, compBitmap, context)
            views.setOnClickPendingIntent(R.id.widget_container_4x4, pendingIntent)
        } else {
            populate4x2View(views, selectedTeam, cachedMatch, homeBitmap, awayBitmap, compBitmap, context)
            views.setOnClickPendingIntent(R.id.widget_container_4x2, pendingIntent)
        }

        return views
    }

    // -------------------------------------------------------------
    // 2x2 Compact Square: Crests + VS + Big Timer Countdown
    // -------------------------------------------------------------
    private fun populate2x2View(
        views: RemoteViews,
        selectedTeam: SelectedTeamEntity?,
        cachedMatch: CachedMatchEntity?,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?,
        context: Context
    ) {
        if (selectedTeam == null) {
            views.setViewVisibility(R.id.widget_2x2_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_2x2_state_no_fixture, View.GONE)
            views.setViewVisibility(R.id.widget_2x2_state_empty, View.VISIBLE)
            return
        }
        if (cachedMatch == null) {
            views.setViewVisibility(R.id.widget_2x2_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_2x2_state_no_fixture, View.VISIBLE)
            views.setViewVisibility(R.id.widget_2x2_state_empty, View.GONE)
            views.setTextViewText(R.id.widget_2x2_no_fixture_team, selectedTeam.shortName.uppercase(Locale.getDefault()))
            return
        }

        views.setViewVisibility(R.id.widget_2x2_state_match, View.VISIBLE)
        views.setViewVisibility(R.id.widget_2x2_state_no_fixture, View.GONE)
        views.setViewVisibility(R.id.widget_2x2_state_empty, View.GONE)

        val compName = cachedMatch.competitionName?.uppercase(Locale.getDefault()) ?: "MATCH"
        views.setTextViewText(R.id.widget_2x2_header_text, compName)

        if (homeBitmap != null) {
            views.setImageViewBitmap(R.id.widget_2x2_home_crest, homeBitmap)
        }
        if (awayBitmap != null) {
            views.setImageViewBitmap(R.id.widget_2x2_away_crest, awayBitmap)
        }

        val kickoffDate = parseUtcDate(cachedMatch.utcDate)
        if (kickoffDate != null) {
            val diffMs = kickoffDate.time - System.currentTimeMillis()
            if (diffMs <= 0) {
                val timeSinceKickoff = -diffMs
                if (timeSinceKickoff <= MATCH_WINDOW_MS) {
                    views.setViewVisibility(R.id.widget_2x2_timer_box, View.GONE)
                    views.setViewVisibility(R.id.widget_2x2_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_2x2_live_status, "LIVE IN PLAY")
                    scheduleRefreshAlarm(context, kickoffDate.time + MATCH_WINDOW_MS + 5000L)
                } else {
                    views.setViewVisibility(R.id.widget_2x2_timer_box, View.GONE)
                    views.setViewVisibility(R.id.widget_2x2_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_2x2_live_status, "FULL TIME")
                }
            } else {
                views.setViewVisibility(R.id.widget_2x2_timer_box, View.VISIBLE)
                views.setViewVisibility(R.id.widget_2x2_live_container, View.GONE)

                val days = diffMs / (24 * 60 * 60 * 1000)
                val baseTime = SystemClock.elapsedRealtime() + diffMs
                views.setChronometerCountDown(R.id.widget_2x2_chronometer, true)
                views.setChronometer(R.id.widget_2x2_chronometer, baseTime, null, true)

                if (days >= 1) {
                    views.setTextViewText(R.id.widget_2x2_timer_label, if (days == 1L) "1 DAY TO KICKOFF" else "$days DAYS TO KICKOFF")
                } else {
                    views.setTextViewText(R.id.widget_2x2_timer_label, "HOURS : MIN : SEC")
                    scheduleRefreshAlarm(context, kickoffDate.time)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 3x2 / 4x2 Wide: Header bar, Team names, Crests, Digital clock
    // -------------------------------------------------------------
    private fun populate4x2View(
        views: RemoteViews,
        selectedTeam: SelectedTeamEntity?,
        cachedMatch: CachedMatchEntity?,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?,
        compBitmap: Bitmap?,
        context: Context
    ) {
        if (selectedTeam == null) {
            views.setViewVisibility(R.id.widget_4x2_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_4x2_state_no_fixture, View.GONE)
            views.setViewVisibility(R.id.widget_4x2_state_empty, View.VISIBLE)
            return
        }
        if (cachedMatch == null) {
            views.setViewVisibility(R.id.widget_4x2_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_4x2_state_no_fixture, View.VISIBLE)
            views.setViewVisibility(R.id.widget_4x2_state_empty, View.GONE)
            views.setTextViewText(R.id.widget_4x2_no_fixture_team, selectedTeam.shortName.uppercase(Locale.getDefault()))
            return
        }

        views.setViewVisibility(R.id.widget_4x2_state_match, View.VISIBLE)
        views.setViewVisibility(R.id.widget_4x2_state_no_fixture, View.GONE)
        views.setViewVisibility(R.id.widget_4x2_state_empty, View.GONE)

        val compName = cachedMatch.competitionName?.uppercase(Locale.getDefault()) ?: "MATCH"
        views.setTextViewText(R.id.widget_4x2_competition_name, compName)

        if (compBitmap != null) {
            views.setImageViewBitmap(R.id.widget_4x2_comp_emblem, compBitmap)
            views.setViewVisibility(R.id.widget_4x2_comp_emblem, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_4x2_comp_emblem, View.GONE)
        }

        views.setTextViewText(R.id.widget_4x2_home_name, cachedMatch.homeTeamShort)
        views.setTextViewText(R.id.widget_4x2_away_name, cachedMatch.awayTeamShort)

        if (homeBitmap != null) {
            views.setImageViewBitmap(R.id.widget_4x2_home_crest, homeBitmap)
        }
        if (awayBitmap != null) {
            views.setImageViewBitmap(R.id.widget_4x2_away_crest, awayBitmap)
        }

        val kickoffDate = parseUtcDate(cachedMatch.utcDate)
        if (kickoffDate != null) {
            val kickoffFormat = SimpleDateFormat("EEE d MMM · HH:mm", Locale.US)
            views.setTextViewText(R.id.widget_4x2_kickoff_datetime, kickoffFormat.format(kickoffDate).uppercase(Locale.US))

            val diffMs = kickoffDate.time - System.currentTimeMillis()
            if (diffMs <= 0) {
                val timeSinceKickoff = -diffMs
                if (timeSinceKickoff <= MATCH_WINDOW_MS) {
                    views.setViewVisibility(R.id.widget_4x2_blocks_container, View.GONE)
                    views.setViewVisibility(R.id.widget_4x2_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_4x2_live_status, "IN PLAY · LIVE")
                    scheduleRefreshAlarm(context, kickoffDate.time + MATCH_WINDOW_MS + 5000L)
                } else {
                    views.setViewVisibility(R.id.widget_4x2_blocks_container, View.GONE)
                    views.setViewVisibility(R.id.widget_4x2_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_4x2_live_status, "FULL TIME · FINISHED")
                }
            } else {
                views.setViewVisibility(R.id.widget_4x2_blocks_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_4x2_live_container, View.GONE)

                val days = diffMs / (24 * 60 * 60 * 1000)
                val baseTime = SystemClock.elapsedRealtime() + diffMs
                views.setChronometerCountDown(R.id.widget_4x2_chronometer, true)
                views.setChronometer(R.id.widget_4x2_chronometer, baseTime, null, true)

                if (days >= 1) {
                    views.setTextViewText(R.id.widget_4x2_chronometer_units, if (days == 1L) "MATCH TOMORROW · 1 DAY" else "$days DAYS TO KICKOFF")
                } else {
                    views.setTextViewText(R.id.widget_4x2_chronometer_units, "TOTAL HOURS : MIN : SEC")
                    scheduleRefreshAlarm(context, kickoffDate.time)
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 4x4 Large Showcase: Big Crests, Stadium Clock, Detailed Tag
    // -------------------------------------------------------------
    private fun populate4x4View(
        views: RemoteViews,
        selectedTeam: SelectedTeamEntity?,
        cachedMatch: CachedMatchEntity?,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?,
        compBitmap: Bitmap?,
        context: Context
    ) {
        if (selectedTeam == null) {
            views.setViewVisibility(R.id.widget_4x4_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_4x4_state_no_fixture, View.GONE)
            views.setViewVisibility(R.id.widget_4x4_state_empty, View.VISIBLE)
            return
        }
        if (cachedMatch == null) {
            views.setViewVisibility(R.id.widget_4x4_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_4x4_state_no_fixture, View.VISIBLE)
            views.setViewVisibility(R.id.widget_4x4_state_empty, View.GONE)
            views.setTextViewText(R.id.widget_4x4_no_fixture_team, selectedTeam.shortName.uppercase(Locale.getDefault()))
            return
        }

        views.setViewVisibility(R.id.widget_4x4_state_match, View.VISIBLE)
        views.setViewVisibility(R.id.widget_4x4_state_no_fixture, View.GONE)
        views.setViewVisibility(R.id.widget_4x4_state_empty, View.GONE)

        val compName = cachedMatch.competitionName?.uppercase(Locale.getDefault()) ?: "MATCH"
        views.setTextViewText(R.id.widget_4x4_competition_name, compName)

        if (compBitmap != null) {
            views.setImageViewBitmap(R.id.widget_4x4_comp_emblem, compBitmap)
            views.setViewVisibility(R.id.widget_4x4_comp_emblem, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_4x4_comp_emblem, View.GONE)
        }

        views.setTextViewText(R.id.widget_4x4_home_name, cachedMatch.homeTeamShort)
        views.setTextViewText(R.id.widget_4x4_away_name, cachedMatch.awayTeamShort)

        if (homeBitmap != null) {
            views.setImageViewBitmap(R.id.widget_4x4_home_crest, homeBitmap)
        }
        if (awayBitmap != null) {
            views.setImageViewBitmap(R.id.widget_4x4_away_crest, awayBitmap)
        }

        val kickoffDate = parseUtcDate(cachedMatch.utcDate)
        if (kickoffDate != null) {
            val kickoffFormat = SimpleDateFormat("EEE d MMM · HH:mm", Locale.US)
            views.setTextViewText(R.id.widget_4x4_kickoff_datetime, kickoffFormat.format(kickoffDate).uppercase(Locale.US))

            val diffMs = kickoffDate.time - System.currentTimeMillis()
            if (diffMs <= 0) {
                val timeSinceKickoff = -diffMs
                if (timeSinceKickoff <= MATCH_WINDOW_MS) {
                    views.setViewVisibility(R.id.widget_4x4_blocks_container, View.GONE)
                    views.setViewVisibility(R.id.widget_4x4_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_4x4_live_status, "IN PLAY · SECOND HALF")
                    scheduleRefreshAlarm(context, kickoffDate.time + MATCH_WINDOW_MS + 5000L)
                } else {
                    views.setViewVisibility(R.id.widget_4x4_blocks_container, View.GONE)
                    views.setViewVisibility(R.id.widget_4x4_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_4x4_live_status, "FULL TIME · FINISHED")
                }
            } else {
                views.setViewVisibility(R.id.widget_4x4_blocks_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_4x4_live_container, View.GONE)

                val days = diffMs / (24 * 60 * 60 * 1000)
                val baseTime = SystemClock.elapsedRealtime() + diffMs
                views.setChronometerCountDown(R.id.widget_4x4_chronometer, true)
                views.setChronometer(R.id.widget_4x4_chronometer, baseTime, null, true)

                if (days >= 1) {
                    val tagText = if (days == 1L) "MATCH TOMORROW · 1 DAY" else "$days DAYS TO KICKOFF"
                    views.setTextViewText(R.id.widget_4x4_countdown_tag, tagText)
                    views.setTextViewText(R.id.widget_4x4_chronometer_units, "TOTAL HOURS : MIN : SEC")
                } else {
                    views.setTextViewText(R.id.widget_4x4_countdown_tag, "🔥 MATCHDAY · KICKOFF TODAY")
                    views.setTextViewText(R.id.widget_4x4_chronometer_units, "HOURS : MIN : SEC")
                    scheduleRefreshAlarm(context, kickoffDate.time)
                }
            }
        }
    }

    private suspend fun loadBitmapFromUrl(context: Context, url: String): Bitmap? {
        if (url.isEmpty()) return null
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()

        return try {
            val result = loader.execute(request)
            val drawable = result.drawable ?: return null
            drawableToBitmap(drawable)
        } catch (e: Exception) {
            Log.w(TAG, "Error loading bitmap from $url: ${e.message}")
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 120
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 120

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun parseUtcDate(utcStr: String): Date? {
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

    private fun createMonogramBitmap(teamShortName: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background circle (#161A22)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161A22")
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // Border stroke (#232836)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#232836")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(radius, radius, radius - 1.5f, borderPaint)

        // Monogram text in bright green #00E676
        val monogram = teamShortName.filter { it.isLetterOrDigit() }.take(2).uppercase().ifEmpty { "FT" }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            textSize = 34f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textBounds = Rect()
        textPaint.getTextBounds(monogram, 0, monogram.length, textBounds)
        val yOffset = radius - textBounds.exactCenterY()

        canvas.drawText(monogram, radius, yOffset, textPaint)
        return bitmap
    }

    private fun scheduleRefreshAlarm(context: Context, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, CountdownAppWidgetProvider::class.java).apply {
                action = CountdownAppWidgetProvider.ACTION_REFRESH_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule widget refresh alarm: ${e.message}")
        }
    }
}
