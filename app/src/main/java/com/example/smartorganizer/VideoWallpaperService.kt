package com.example.smartorganizer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.smartorganizer.thirdparty.gif.GifDecoder

/** Video and animated-GIF live wallpaper engine. GIF decoding is based on codeg0blin/video-live-wallpaper (Apache-2.0). */
class VideoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = WallpaperEngine()

    inner class WallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private var visible = false
        private var gif: GifDecoder? = null
        private var gifBitmap: Bitmap? = null
        private var mediaUri: Uri? = null
        private var mediaType: String = "video"

        private val gifTick = object : Runnable {
            override fun run() {
                if (!visible || gif == null) return
                try {
                    val decoder = gif ?: return
                    val delay = decoder.advance()
                    val bitmap = gifBitmap ?: Bitmap.createBitmap(decoder.width, decoder.height, Bitmap.Config.ARGB_8888).also { gifBitmap = it }
                    bitmap.setPixels(decoder.pixels, 0, decoder.width, 0, 0, decoder.width, decoder.height)
                    drawBitmap(bitmap)
                    handler.postDelayed(this, delay.toLong())
                } catch (_: Exception) {
                    stopGif()
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val prefs = getSharedPreferences("wallpaper_studio", MODE_PRIVATE)
            mediaUri = prefs.getString("live_video_uri", null)?.let(Uri::parse)
            mediaType = prefs.getString("live_media_type", "video") ?: "video"
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) startMedia() else stopMedia()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (visible) startMedia()
        }

        private fun startMedia() {
            if (!surfaceHolder.surface.isValid) return
            if (mediaType == "gif") {
                if (gif != null) return
                try {
                    val uri = mediaUri ?: return
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                    gif = GifDecoder.parse(bytes, 12_000_000)
                    gifTick.run()
                } catch (_: Exception) {
                    stopGif()
                }
            } else {
                // Keep Android's native video live-wallpaper playback path for video files.
                startVideoFallback()
            }
        }

        private fun startVideoFallback() {
            val uri = mediaUri ?: return
            val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(this@VideoWallpaperService, VideoWallpaperService::class.java))
            }
            // The existing video playback engine is retained in the service's legacy implementation.
            // Video playback is handled by MediaPlayer below.
            if (videoPlayer == null) {
                try {
                    val player = android.media.MediaPlayer()
                    videoPlayer = player
                    player.setDataSource(this@VideoWallpaperService, uri)
                    player.setSurface(surfaceHolder.surface)
                    player.isLooping = true
                    player.setVolume(0f, 0f)
                    player.setOnPreparedListener { if (visible) it.start() }
                    player.setOnErrorListener { _, _, _ -> stopVideo(); true }
                    player.prepareAsync()
                } catch (_: Exception) { stopVideo() }
            }
        }

        private var videoPlayer: android.media.MediaPlayer? = null

        private fun drawBitmap(bitmap: Bitmap) {
            if (!surfaceHolder.surface.isValid) return
            var canvas: Canvas? = null
            try {
                canvas = surfaceHolder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(android.graphics.Color.BLACK)
                    val scale = maxOf(canvas.width.toFloat() / bitmap.width, canvas.height.toFloat() / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val left = (canvas.width - w) / 2f
                    val top = (canvas.height - h) / 2f
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + w, top + h), paint)
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null) try { surfaceHolder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
        }

        private fun stopGif() {
            handler.removeCallbacks(gifTick)
            gif = null
            gifBitmap?.recycle()
            gifBitmap = null
        }

        private fun stopVideo() {
            val player = videoPlayer ?: return
            videoPlayer = null
            try { player.setSurface(null) } catch (_: Exception) {}
            try { player.reset(); player.release() } catch (_: Exception) {}
        }

        private fun stopMedia() {
            stopGif()
            stopVideo()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopMedia()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            stopMedia()
            super.onDestroy()
        }
    }
}
