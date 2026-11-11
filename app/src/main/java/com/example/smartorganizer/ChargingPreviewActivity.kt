package com.example.smartorganizer

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.VideoView
import android.widget.Toast

/** Opened only after the user taps the charging notification. */
class ChargingPreviewActivity : Activity() {
    private var chargingVideo: VideoView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uri = getSharedPreferences("wallpaper_studio", MODE_PRIVATE)
            .getString("charging_video_uri", null)?.let { Uri.parse(it) }
        if (uri == null) {
            Toast.makeText(this, "저장된 충전 영상이 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val video = VideoView(this)
        chargingVideo = video
        setContentView(video)
        video.setVideoURI(uri)
        video.setOnPreparedListener { player ->
            player.isLooping = true
            video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "충전 영상을 재생할 수 없습니다.", Toast.LENGTH_LONG).show()
            finish()
            true
        }
    }

    override fun onDestroy() {
        chargingVideo?.stopPlayback()
        chargingVideo = null
        super.onDestroy()
    }
}
