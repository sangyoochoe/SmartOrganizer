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
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home-screen companion for layouts created in Widget Studio. */
class OrganizerCustomWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY,
            Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> updateAll(context)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, OrganizerCustomWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.organizer_custom_widget)
            val raw = context.getSharedPreferences("widget_studio", Context.MODE_PRIVATE)
                .getString("layout", "[]") ?: "[]"
            val cards = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
            val count = cards.length().coerceAtMost(3)
            val labels = arrayOf(R.id.custom_widget_label_1, R.id.custom_widget_label_2, R.id.custom_widget_label_3)
            val values = arrayOf(R.id.custom_widget_value_1, R.id.custom_widget_value_2, R.id.custom_widget_value_3)
            val now = System.currentTimeMillis()
            val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
            for (i in 0..2) {
                val visible = i < count
                views.setViewVisibility(labels[i], if (visible) android.view.View.VISIBLE else android.view.View.GONE)
                views.setViewVisibility(values[i], if (visible) android.view.View.VISIBLE else android.view.View.GONE)
                if (!visible) continue
                val item = cards.optJSONObject(i)
                val type = item?.optString("type", "text") ?: "text"
                val label = item?.optString("label", type)?.take(60) ?: "카드"
                val value = when (type) {
                    "clock" -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
                    "date" -> SimpleDateFormat("yyyy.MM.dd  EEE", Locale.getDefault()).format(Date(now))
                    "battery" -> if (percent >= 0) "배터리 $percent%" else "배터리 --"
                    else -> item?.optString("value", "")?.take(120) ?: ""
                }
                views.setTextViewText(labels[i], label)
                views.setTextViewText(values[i], value)
                if (type == "clock") {
                    val dayStart = now - now % 86_400_000L
                    views.setChronometer(values[i], SystemClock.elapsedRealtime() - (now - dayStart), "%s", true)
                } else {
                    views.setTextViewText(values[i], value)
                }
            }
            val open = PendingIntent.getActivity(context, id, Intent(context, WidgetStudioActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.custom_widget_root, open)
            manager.updateAppWidget(id, views)
        }
    }
}
