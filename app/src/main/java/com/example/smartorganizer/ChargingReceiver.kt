package com.example.smartorganizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_POWER_CONNECTED) return
        val prefs = context.getSharedPreferences("wallpaper_studio", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("charging_alert_enabled", false)) return
        if (prefs.getString("charging_video_uri", null).isNullOrBlank()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "charging_wallpaper"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "충전 애니메이션", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "충전 연결 시 영상 미리보기를 여는 알림" })
        }
        val open = Intent(context, ChargingPreviewActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 7202, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }.setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Smart Organizer 충전 애니메이션")
            .setContentText("눌러서 저장한 충전 영상을 재생하세요.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(7202, notification)
    }
}
