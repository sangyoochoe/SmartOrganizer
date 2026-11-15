package com.example.smartorganizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.SystemClock
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CyberDashboardWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_BOOST) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_BOOST, !prefs.getBoolean(KEY_BOOST, false)).apply()
            updateAll(context)
        } else if (intent.action == Intent.ACTION_DATE_CHANGED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_BATTERY_LOW ||
            intent.action == Intent.ACTION_BATTERY_OKAY ||
            intent.action == Intent.ACTION_POWER_CONNECTED ||
            intent.action == Intent.ACTION_POWER_DISCONNECTED) {
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_BOOST = "com.example.smartorganizer.CYBER_WIDGET_TOGGLE"
        private const val PREFS = "cyber_dashboard"
        private const val KEY_BOOST = "widget_boost"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CyberDashboardWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val boost = prefs.getBoolean(KEY_BOOST, false)
            val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1

            val views = RemoteViews(context.packageName, R.layout.widget_cyber_dashboard)
            val options = manager.getAppWidgetOptions(id)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
            val compactWidth = minWidth < 230
            val compactHeight = minHeight < 145
            views.setViewVisibility(R.id.widget_header, if (compactHeight) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_date, if (compactHeight) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_left, if (compactWidth) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_right, if (compactWidth) android.view.View.GONE else android.view.View.VISIBLE)
            val now = System.currentTimeMillis()
            val dayStart = now - (now % 86_400_000L)
            val chronometerBase = SystemClock.elapsedRealtime() - (now - dayStart)
            views.setChronometer(R.id.widget_clock, chronometerBase, "%s", true)
            views.setTextViewText(R.id.widget_date, SimpleDateFormat("yyyy.MM.dd  EEE", Locale.getDefault()).format(Date(now)))
            views.setTextViewText(R.id.widget_battery, if (percent >= 0) "BATTERY  $percent%" else "BATTERY  --")
            views.setTextViewText(R.id.widget_mode, if (boost) "ZERO BOOST  /  ACTIVE" else "NORMAL DRIVE")
            views.setTextColor(R.id.widget_mode, if (boost) 0xffffb45c.toInt() else 0xff69dfff.toInt())

            val dashboard = Intent(context, CyberDashboardActivity::class.java)
            val dashboardPending = PendingIntent.getActivity(context, id * 10, dashboard,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_center, dashboardPending)

            val toggle = Intent(context, CyberDashboardWidgetProvider::class.java).setAction(ACTION_TOGGLE_BOOST)
            val togglePending = PendingIntent.getBroadcast(context, id * 10 + 1, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_mode, togglePending)

            setAppShortcut(context, views, prefs.getString("left", null), R.id.widget_left, id * 10 + 2)
            setAppShortcut(context, views, prefs.getString("right", null), R.id.widget_right, id * 10 + 3)
            manager.updateAppWidget(id, views)
        }

        private fun setAppShortcut(context: Context, views: RemoteViews, pkg: String?, viewId: Int, request: Int) {
            val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
                ?: Intent(context, CyberDashboardActivity::class.java)
            val pending = PendingIntent.getActivity(context, request, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(viewId, pending)
        }
    }
}
