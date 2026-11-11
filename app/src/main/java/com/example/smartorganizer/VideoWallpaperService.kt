package com.example.smartorganizer

import android.media.MediaPlayer
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/** Plays the user's selected video as a muted Android live wallpaper. */
class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VideoEngine()

    inner class VideoEngine : Engine() {
        private var player: MediaPlayer? = null
        private var visible = false

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) startPlayback() else stopPlayback()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) startPlayback()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopPlayback()
            super.onSurfaceDestroyed(holder)
        }

        private fun startPlayback() {
            if (player != null || !surfaceHolder.surface.isValid) return
            val saved = getSharedPreferences("wallpaper_studio", MODE_PRIVATE)
                .getString("live_video_uri", null) ?: return
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setDataSource(this@VideoWallpaperService, Uri.parse(saved))
                mp.setSurface(surfaceHolder.surface)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setOnPreparedListener { if (visible) it.start() }
                mp.setOnErrorListener { _, _, _ -> stopPlayback(); true }
                mp.prepareAsync()
            } catch (_: Exception) {
                stopPlayback()
            }
        }

        private fun stopPlayback() {
            val old = player ?: return
            player = null
            try { old.setSurface(null) } catch (_: Exception) {}
            try { old.reset(); old.release() } catch (_: Exception) {}
        }

        override fun onDestroy() {
            stopPlayback()
            super.onDestroy()
        }
    }
}
