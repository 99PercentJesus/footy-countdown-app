package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CountdownAppWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "CountdownAppWidget"
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets.")
        triggerAsyncUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG, "onAppWidgetOptionsChanged for widget $appWidgetId")
        triggerAsyncUpdate(context)
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "onEnabled called. First widget added.")
        triggerAsyncUpdate(context)
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "onDisabled called. All widgets removed.")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        Log.d(TAG, "onReceive intent: $action")
        when (action) {
            ACTION_REFRESH_WIDGET,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                triggerAsyncUpdate(context)
            }
        }
    }

    private fun triggerAsyncUpdate(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.updateAllWidgets(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed updating widgets async", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
