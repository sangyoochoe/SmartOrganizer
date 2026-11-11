package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.media.MediaPlayer
import android.widget.VideoView

/**
 * Wallpaper studio for user-owned image/video media.
 * Images can be applied with Android WallpaperManager. Videos are previewed in-app;
 * Android does not expose a universal video-wallpaper setter, so we never claim they
 * have been installed as system wallpaper.
 */
class WallpaperStudioActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("wallpaper_studio", MODE_PRIVATE) }
    private lateinit var list: LinearLayout
    private lateinit var preview: FrameLayout
    private var selectedUri: Uri? = null
    private var selectedIsVideo = false
    private var videoView: VideoView? = null
    private val media = mutableListOf<Pair<Uri, Boolean>>()
    private var target = WallpaperManager.FLAG_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadMedia()
        buildUi()
    }

    private fun loadMedia() {
        val saved = prefs.getString("library", "") ?: ""
        saved.split("\n").filter { it.isNotBlank() }.forEach { row ->
            val parts = row.split("|", limit = 2)
            if (parts.size == 2) try { media.add(Uri.parse(parts[1]) to (parts[0] == "video")) } catch (_: Exception) {}
        }
        selectedUri = media.firstOrNull()?.first
        selectedIsVideo = media.firstOrNull()?.second ?: false
    }

    private fun persist() {
        prefs.edit().putString("library", media.distinctBy { it.first.toString() }
            .joinToString("\n") { (uri, video) -> (if (video) "video" else "image") + "|" + uri }).apply()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(12))
        }
        outer.addView(TextView(this).apply {
            text = "월페이퍼 스튜디오"
            textSize = 25f
        })
        outer.addView(TextView(this).apply {
            text = "사진·동영상을 모아보고 Fold 화면별 배경을 관리합니다."
            textSize = 13f
        })
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply { text = "사진 추가"; setOnClickListener { pick("image/*", 7101) } })
        buttons.addView(Button(this).apply { text = "동영상 추가"; setOnClickListener { pick("video/*", 7102) } })
        buttons.addView(Button(this).apply { text = "접힌 화면"; setOnClickListener { target = WallpaperManager.FLAG_SYSTEM; Toast.makeText(this@WallpaperStudioActivity, "홈 배경 적용 대상으로 선택됨", Toast.LENGTH_SHORT).show() } })
        buttons.addView(Button(this).apply { text = "잠금 화면"; setOnClickListener { target = WallpaperManager.FLAG_LOCK; Toast.makeText(this@WallpaperStudioActivity, "잠금 화면 적용 대상으로 선택됨", Toast.LENGTH_SHORT).show() } })
        outer.addView(buttons)
        preview = FrameLayout(this).apply {
            setBackgroundColor(0xff20242c.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(12) }
        }
        outer.addView(preview)
        outer.addView(Button(this).apply {
            text = "선택한 사진을 배경화면으로 적용"
            setOnClickListener { applyImage() }
        })
        outer.addView(Button(this).apply {
            text = "선택 항목 삭제"
            setOnClickListener {
                val uri = selectedUri ?: return@setOnClickListener
                media.removeAll { it.first == uri }; persist()
                selectedUri = media.firstOrNull()?.first
                selectedIsVideo = media.firstOrNull()?.second ?: false
                showPreview(); renderList()
            }
        })
        outer.addView(TextView(this).apply { text = "내 월페이퍼 보관함"; textSize = 18f; setPadding(0, dp(12), 0, dp(6)) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        outer.addView(scroll, LinearLayout.LayoutParams(-1, dp(180)))
        setContentView(outer)
        showPreview()
        renderList()
    }

    private fun pick(type: String, request: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            this.type = type
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, request)
    }

    @Deprecated("Legacy result API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in 7101..7102) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val isVideo = requestCode == 7102
        if (media.none { it.first == uri }) media.add(uri to isVideo)
        selectedUri = uri
        selectedIsVideo = isVideo
        persist()
        showPreview()
        renderList()
    }

    private fun showPreview() {
        videoView?.stopPlayback()
        videoView = null
        preview.removeAllViews()
        val uri = selectedUri ?: run {
            preview.addView(TextView(this).apply { text = "사진 또는 동영상을 추가하세요."; setTextColor(-1); gravity = Gravity.CENTER })
            return
        }
        if (selectedIsVideo) {
            val player = VideoView(this)
            videoView = player
            preview.addView(player, FrameLayout.LayoutParams(-1, -1))
            player.setVideoURI(uri)
            player.setOnPreparedListener { mp -> mp.isLooping = true; player.start() }
            player.setOnErrorListener { _, _, _ -> Toast.makeText(this, "이 동영상 형식은 미리보기할 수 없습니다.", Toast.LENGTH_SHORT).show(); true }
        } else {
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageURI(uri) }
            preview.addView(image, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun renderList() {
        list.removeAllViews()
        if (media.isEmpty()) list.addView(TextView(this).apply { text = "보관함이 비어 있습니다." })
        media.forEach { (uri, video) ->
            val row = Button(this).apply {
                text = (if (video) "▶ 동영상 · " else "▧ 사진 · ") + (uri.lastPathSegment ?: "미디어")
                setOnClickListener { selectedUri = uri; selectedIsVideo = video; showPreview() }
            }
            list.addView(row)
        }
    }

    private fun applyImage() {
        val uri = selectedUri
        if (uri == null || selectedIsVideo) {
            Toast.makeText(this, "시스템 배경화면 적용은 사진을 선택했을 때 사용할 수 있습니다. 동영상은 앱 안에서 미리보기할 수 있습니다.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IllegalStateException("이미지를 열 수 없습니다.")
                WallpaperManager.getInstance(this).setStream(input, null, true, target)
            }
            Toast.makeText(this, "배경화면 적용을 요청했습니다.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "적용 실패: " + (e.localizedMessage ?: "권한 또는 이미지 형식을 확인하세요."), Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        videoView?.stopPlayback()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
