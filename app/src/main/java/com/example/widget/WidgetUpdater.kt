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
import com.example.data.FootballDatabase
import com.example.data.FootballRepository
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
                // If match kicked off in the past, attempt to fetch the latest/next match
                if (timeSinceKickoff > 0) {
                    try {
                        val repository = FootballRepository(context.applicationContext, dao)
                        val fetchResult = repository.fetchNextMatch(selectedTeam.teamId, force = true)
                        val updatedMatch = fetchResult.getOrNull()
                        if (updatedMatch != null) {
                            cachedMatch = updatedMatch
                        } else if (timeSinceKickoff > MATCH_WINDOW_MS) {
                            // If it's well past full time and no next match came back yet, discard stale match
                            dao.deleteCachedMatchForTeam(selectedTeam.teamId)
                            cachedMatch = null
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed auto-refreshing match after kickoff: ${e.message}")
                    }
                }
            }

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_countdown)

                if (selectedTeam == null) {
                    // State 3: No team picked yet
                    views.setViewVisibility(R.id.widget_state_match, View.GONE)
                    views.setViewVisibility(R.id.widget_state_no_fixture, View.GONE)
                    views.setViewVisibility(R.id.widget_state_empty, View.VISIBLE)
                } else if (cachedMatch == null) {
                    // State 2: No fixture scheduled
                    views.setViewVisibility(R.id.widget_state_match, View.GONE)
                    views.setViewVisibility(R.id.widget_state_no_fixture, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_state_empty, View.GONE)
                    views.setTextViewText(R.id.widget_no_fixture_team, selectedTeam.shortName.uppercase(Locale.getDefault()))
                } else {
                    // State 1: Active Match Countdown
                    views.setViewVisibility(R.id.widget_state_match, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_state_no_fixture, View.GONE)
                    views.setViewVisibility(R.id.widget_state_empty, View.GONE)

                    val compName = cachedMatch.competitionName?.uppercase(Locale.getDefault()) ?: "MATCH"
                    views.setTextViewText(R.id.widget_competition_name, compName)

                    views.setTextViewText(R.id.widget_home_name, cachedMatch.homeTeamShort)
                    views.setTextViewText(R.id.widget_away_name, cachedMatch.awayTeamShort)

                    // Try to load Competition Emblem
                    val compEmblemUrl = cachedMatch.competitionEmblem ?: ""
                    if (compEmblemUrl.isNotEmpty()) {
                        val compBitmap = bitmapCache[compEmblemUrl] ?: withContext(Dispatchers.IO) {
                            loadBitmapFromUrl(context, compEmblemUrl)
                        }?.also { bitmapCache[compEmblemUrl] = it }

                        if (compBitmap != null) {
                            views.setImageViewBitmap(R.id.widget_comp_emblem, compBitmap)
                            views.setViewVisibility(R.id.widget_comp_emblem, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_comp_emblem, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.widget_comp_emblem, View.GONE)
                    }

                    // Try to load Home Crest (with Monogram fallback)
                    val homeBitmap = if (!cachedMatch.homeTeamCrest.isNullOrEmpty()) {
                        bitmapCache[cachedMatch.homeTeamCrest] ?: withContext(Dispatchers.IO) {
                            loadBitmapFromUrl(context, cachedMatch.homeTeamCrest)
                        }?.also { bitmapCache[cachedMatch.homeTeamCrest] = it }
                    } else null

                    val finalHomeBitmap = homeBitmap ?: createMonogramBitmap(cachedMatch.homeTeamShort)
                    views.setImageViewBitmap(R.id.widget_home_crest, finalHomeBitmap)
                    views.setViewVisibility(R.id.widget_home_crest, View.VISIBLE)

                    // Try to load Away Crest (with Monogram fallback)
                    val awayBitmap = if (!cachedMatch.awayTeamCrest.isNullOrEmpty()) {
                        bitmapCache[cachedMatch.awayTeamCrest] ?: withContext(Dispatchers.IO) {
                            loadBitmapFromUrl(context, cachedMatch.awayTeamCrest)
                        }?.also { bitmapCache[cachedMatch.awayTeamCrest] = it }
                    } else null

                    val finalAwayBitmap = awayBitmap ?: createMonogramBitmap(cachedMatch.awayTeamShort)
                    views.setImageViewBitmap(R.id.widget_away_crest, finalAwayBitmap)
                    views.setViewVisibility(R.id.widget_away_crest, View.VISIBLE)

                    val kickoffDate = parseUtcDate(cachedMatch.utcDate)
                    if (kickoffDate != null) {
                        val kickoffFormat = SimpleDateFormat("EEE d MMM · HH:mm", Locale.US)
                        views.setTextViewText(R.id.widget_kickoff_datetime, kickoffFormat.format(kickoffDate).uppercase(Locale.US))

                        val diffMs = kickoffDate.time - System.currentTimeMillis()

                        if (diffMs <= 0) {
                            val timeSinceKickoff = -diffMs
                            if (timeSinceKickoff <= MATCH_WINDOW_MS) {
                                // In play state: Match currently active (under 2.5 hours since kickoff)
                                views.setViewVisibility(R.id.widget_blocks_container, View.GONE)
                                views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
                                views.setTextViewText(R.id.widget_live_status, "IN PLAY · LIVE")

                                // Schedule an alarm at the end of the match window to transition out of in-play
                                scheduleRefreshAlarm(context, kickoffDate.time + MATCH_WINDOW_MS + 5000L)
                            } else {
                                // Match concluded (>2.5 hours since kickoff)
                                views.setViewVisibility(R.id.widget_blocks_container, View.GONE)
                                views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
                                views.setTextViewText(R.id.widget_live_status, "FULL TIME · FINISHED")
                            }
                        } else {
                            views.setViewVisibility(R.id.widget_blocks_container, View.VISIBLE)
                            views.setViewVisibility(R.id.widget_live_container, View.GONE)

                            val days = diffMs / (24 * 60 * 60 * 1000)
                            val totalHours = diffMs / (60 * 60 * 1000)

                            val baseTime = SystemClock.elapsedRealtime() + diffMs
                            views.setChronometerCountDown(R.id.widget_chronometer, true)
                            views.setChronometer(R.id.widget_chronometer, baseTime, null, true)

                            if (days >= 1) {
                                val tagText = if (days == 1L) "MATCH TOMORROW · 1 DAY" else "$days DAYS TO KICKOFF"
                                views.setTextViewText(R.id.widget_countdown_tag, tagText)
                                views.setTextViewText(R.id.widget_chronometer_units, "TOTAL HOURS : MIN : SEC")

                                val remainderOfDayMs = diffMs % (24 * 60 * 60 * 1000)
                                val safeRemainderMs = if (remainderOfDayMs < 1000L) (24 * 60 * 60 * 1000) else remainderOfDayMs
                                scheduleRefreshAlarm(context, System.currentTimeMillis() + safeRemainderMs)
                            } else {
                                // Under 24 hours (Matchday!)
                                val tagText = if (totalHours >= 2) "🔥 MATCHDAY · KICKOFF TODAY" else "⚡ FINAL COUNTDOWN"
                                views.setTextViewText(R.id.widget_countdown_tag, tagText)
                                views.setTextViewText(R.id.widget_chronometer_units, if (totalHours > 0) "HOURS : MIN : SEC" else "MINUTES : SECONDS")

                                scheduleRefreshAlarm(context, kickoffDate.time)
                            }
                        }
                    } else {
                        views.setTextViewText(R.id.widget_kickoff_datetime, "DATE TBD")
                        views.setViewVisibility(R.id.widget_blocks_container, View.GONE)
                        views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
                        views.setTextViewText(R.id.widget_live_status, "FIXTURE SCHEDULE PENDING")
                    }
                }

                // Dynamic click intent to open Main Application
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets", e)
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
