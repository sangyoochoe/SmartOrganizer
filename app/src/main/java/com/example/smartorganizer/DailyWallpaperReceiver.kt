package com.example.smartorganizer

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Calendar

/** Applies the latest due daily wallpaper slot using a power-friendly inexact alarm. */
class DailyWallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("wallpaper_studio", Context.MODE_PRIVATE)
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val tick = Intent(context, DailyWallpaperReceiver::class.java).setAction(ACTION_TICK)
            val pi = PendingIntent.getBroadcast(context, 7310, tick, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, 15 * 60_000L, pi)
            return
        }
        val rows = (prefs.getString("daily_wallpaper_schedule", "") ?: "").split("\n").filter { it.isNotBlank() }
        if (rows.isEmpty()) return
        val now = Calendar.getInstance()
        val today = String.format(java.util.Locale.US, "%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        val current = String.format(java.util.Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
        val due = rows.mapNotNull { row -> val p = row.split("|", limit = 2); if (p.size == 2 && p[0] <= current) p[0] to p[1] else null }.maxByOrNull { it.first } ?: return
        val marker = today + "|" + due.first
        if (prefs.getString("daily_wallpaper_last_applied", "") == marker) return
        try {
            context.contentResolver.openInputStream(Uri.parse(due.second))?.use { WallpaperManager.getInstance(context).setStream(it, null, true, WallpaperManager.FLAG_SYSTEM) } ?: return
            prefs.edit().putString("daily_wallpaper_last_applied", marker).apply()
        } catch (_: Exception) { }
    }
    companion object { const val ACTION_TICK = "com.example.smartorganizer.DAILY_WALLPAPER_TICK" }
}
