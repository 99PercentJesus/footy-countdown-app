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
import android.util.SizeF
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
import com.example.update.AppUpdateManager
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

            // Silent OTA update check on widget refresh
            try {
                val updateManager = AppUpdateManager(context)
                updateManager.checkForUpdates()
            } catch (e: Exception) {
                // Ignore silent update errors
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

            // Update each widget
            for (appWidgetId in appWidgetIds) {
                val remoteViews = buildResponsiveRemoteViews(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = appWidgetId,
                    selectedTeam = selectedTeam,
                    cachedMatch = cachedMatch,
                    homeBitmap = homeBitmap,
                    awayBitmap = awayBitmap,
                    compBitmap = compBitmap,
                    pendingIntent = pendingIntent
                )
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widgets", e)
        }
    }

    /**
     * Builds RemoteViews using native Android 12+ multi-size mapping for instant reactive resizing,
     * and robust fallback for earlier Android versions.
     */
    private fun buildResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        selectedTeam: SelectedTeamEntity?,
        cachedMatch: CachedMatchEntity?,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?,
        compBitmap: Bitmap?,
        pendingIntent: PendingIntent
    ): RemoteViews {
        val viewsPill = RemoteViews(context.packageName, R.layout.widget_countdown_pill)
        val views2x2 = RemoteViews(context.packageName, R.layout.widget_countdown_2x2)
        val views4x2 = RemoteViews(context.packageName, R.layout.widget_countdown_4x2)
        val views4x4 = RemoteViews(context.packageName, R.layout.widget_countdown_4x4)

        // Populate each layout
        populateViews(viewsPill, isPill = true, isCompact2x2 = false, isLarge4x4 = false, selectedTeam, cachedMatch, homeBitmap, awayBitmap, compBitmap, context)
        populateViews(views2x2, isPill = false, isCompact2x2 = true, isLarge4x4 = false, selectedTeam, cachedMatch, homeBitmap, awayBitmap, compBitmap, context)
        populateViews(views4x2, isPill = false, isCompact2x2 = false, isLarge4x4 = false, selectedTeam, cachedMatch, homeBitmap, awayBitmap, compBitmap, context)
        populateViews(views4x4, isPill = false, isCompact2x2 = false, isLarge4x4 = true, selectedTeam, cachedMatch, homeBitmap, awayBitmap, compBitmap, context)

        viewsPill.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        views2x2.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        views4x2.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        views4x4.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ native responsive widget layout mapping:
            // The Android launcher dynamically switches the layout in real time as the user drags resize handles:
            // - Pill: 4x1 (wide, short height < 90dp)
            // - Compact Square: 2x2 (~110-180dp square)
            // - Wide: 3x2, 4x2 (height ~100-170dp, width >= 180dp)
            // - Large Showcase: 3x3, 4x3, 4x4 (height >= 180dp)
            val viewMapping = mapOf(
                SizeF(180f, 45f) to viewsPill,
                SizeF(100f, 95f) to views2x2,
                SizeF(180f, 95f) to views4x2,
                SizeF(180f, 180f) to views4x4
            )
            return RemoteViews(viewMapping)
        } else {
            // Fallback for Android 11 and below: inspect bundle options
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180) ?: 180
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110

            return when {
                minHeight < 80 -> viewsPill
                minHeight >= 180 -> views4x4
                minWidth >= 180 -> views4x2
                else -> views2x2
            }
        }
    }

    private fun populateViews(
        views: RemoteViews,
        isPill: Boolean,
        isCompact2x2: Boolean,
        isLarge4x4: Boolean,
        selectedTeam: SelectedTeamEntity?,
        cachedMatch: CachedMatchEntity?,
        homeBitmap: Bitmap?,
        awayBitmap: Bitmap?,
        compBitmap: Bitmap?,
        context: Context
    ) {
        // 1. Update Badge Check
        val hasPendingUpdate = AppUpdateManager.hasPendingUpdate(context)
        val pendingVersion = AppUpdateManager.getPendingUpdateVersion(context)
        if (hasPendingUpdate) {
            views.setViewVisibility(R.id.widget_update_badge, View.VISIBLE)
            val badgeText = when {
                isCompact2x2 -> "⚡ UPDATE READY"
                pendingVersion != null -> "⚡ UPDATE v$pendingVersion READY · TAP TO INSTALL"
                else -> "⚡ NEW UPDATE READY · TAP TO INSTALL"
            }
            views.setTextViewText(R.id.widget_update_badge, badgeText)
        } else {
            views.setViewVisibility(R.id.widget_update_badge, View.GONE)
        }

        // 2. Empty States
        if (selectedTeam == null) {
            views.setViewVisibility(R.id.widget_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_state_no_fixture, View.GONE)
            views.setViewVisibility(R.id.widget_state_empty, View.VISIBLE)
            return
        }

        if (cachedMatch == null) {
            views.setViewVisibility(R.id.widget_state_match, View.GONE)
            views.setViewVisibility(R.id.widget_state_no_fixture, View.VISIBLE)
            views.setViewVisibility(R.id.widget_state_empty, View.GONE)
            views.setTextViewText(R.id.widget_no_fixture_team, selectedTeam.shortName.uppercase(Locale.getDefault()))
            return
        }

        // 3. Active Match State
        views.setViewVisibility(R.id.widget_state_match, View.VISIBLE)
        views.setViewVisibility(R.id.widget_state_no_fixture, View.GONE)
        views.setViewVisibility(R.id.widget_state_empty, View.GONE)

        val compName = cachedMatch.competitionName?.uppercase(Locale.getDefault()) ?: "MATCH"
        views.setTextViewText(R.id.widget_competition_name, compName)

        // Crests
        if (homeBitmap != null) {
            views.setImageViewBitmap(R.id.widget_home_crest, homeBitmap)
            views.setViewVisibility(R.id.widget_home_crest, View.VISIBLE)
        }
        if (awayBitmap != null) {
            views.setImageViewBitmap(R.id.widget_away_crest, awayBitmap)
            views.setViewVisibility(R.id.widget_away_crest, View.VISIBLE)
        }

        // Names
        views.setTextViewText(R.id.widget_home_name, cachedMatch.homeTeamShort)
        views.setTextViewText(R.id.widget_away_name, cachedMatch.awayTeamShort)

        // Emblem (for 4x2 and 4x4)
        if (compBitmap != null) {
            views.setImageViewBitmap(R.id.widget_comp_emblem, compBitmap)
            views.setViewVisibility(R.id.widget_comp_emblem, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_comp_emblem, View.GONE)
        }

        val kickoffDate = parseUtcDate(cachedMatch.utcDate)
        if (kickoffDate != null) {
            val kickoffFormat = SimpleDateFormat("EEE d MMM · HH:mm", Locale.US)
            if (isCompact2x2) {
                // In 2x2, show competition and date compactly in 1 line
                views.setTextViewText(R.id.widget_kickoff_datetime, "$compName · " + SimpleDateFormat("d MMM", Locale.US).format(kickoffDate).uppercase(Locale.US))
            } else {
                views.setTextViewText(R.id.widget_kickoff_datetime, kickoffFormat.format(kickoffDate).uppercase(Locale.US))
            }

            val diffMs = kickoffDate.time - System.currentTimeMillis()
            if (diffMs <= 0) {
                val timeSinceKickoff = -diffMs
                if (timeSinceKickoff <= MATCH_WINDOW_MS) {
                    views.setViewVisibility(R.id.widget_blocks_container, View.GONE)
                    views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_live_status, if (isCompact2x2) "LIVE" else "IN PLAY · LIVE")
                    scheduleRefreshAlarm(context, kickoffDate.time + MATCH_WINDOW_MS + 5000L)
                } else {
                    views.setViewVisibility(R.id.widget_blocks_container, View.GONE)
                    views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_live_status, if (isCompact2x2) "FT" else "FULL TIME · FINISHED")
                }
            } else {
                views.setViewVisibility(R.id.widget_blocks_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_live_container, View.GONE)

                val days = diffMs / (24 * 60 * 60 * 1000)
                val totalHours = diffMs / (60 * 60 * 1000)
                val baseTime = SystemClock.elapsedRealtime() + diffMs

                views.setChronometerCountDown(R.id.widget_chronometer, true)
                views.setChronometer(R.id.widget_chronometer, baseTime, null, true)

                if (isCompact2x2) {
                    // Clean, single-line unit label for 2x2
                    if (days >= 1) {
                        views.setTextViewText(R.id.widget_chronometer_units, if (days == 1L) "1 DAY TO KICKOFF" else "$days DAYS TO KICKOFF")
                    } else {
                        views.setTextViewText(R.id.widget_chronometer_units, "HOURS : MIN : SEC")
                        scheduleRefreshAlarm(context, kickoffDate.time)
                    }
                } else {
                    if (days >= 1) {
                        val tagText = if (days == 1L) "MATCH TOMORROW · 1 DAY" else "$days DAYS TO KICKOFF"
                        views.setTextViewText(R.id.widget_countdown_tag, tagText)
                        views.setTextViewText(R.id.widget_chronometer_units, "TOTAL HOURS : MIN : SEC")
                    } else {
                        val tagText = if (totalHours >= 2) "🔥 MATCHDAY · KICKOFF TODAY" else "⚡ FINAL COUNTDOWN"
                        views.setTextViewText(R.id.widget_countdown_tag, tagText)
                        views.setTextViewText(R.id.widget_chronometer_units, "HOURS : MIN : SEC")
                        scheduleRefreshAlarm(context, kickoffDate.time)
                    }
                }
            }
        } else {
            views.setTextViewText(R.id.widget_kickoff_datetime, "DATE TBD")
            views.setViewVisibility(R.id.widget_blocks_container, View.GONE)
            views.setViewVisibility(R.id.widget_live_container, View.VISIBLE)
            views.setTextViewText(R.id.widget_live_status, "FIXTURE SCHEDULE PENDING")
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

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#161A22")
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#232836")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(radius, radius, radius - 1.5f, borderPaint)

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
