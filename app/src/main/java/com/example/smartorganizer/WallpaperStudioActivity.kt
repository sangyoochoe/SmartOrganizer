package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.content.pm.PackageManager
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
    private var selectedBuiltinResId: Int = 0
    private var videoView: VideoView? = null
    private val media = mutableListOf<Pair<Uri, Boolean>>()
    private var target = WallpaperManager.FLAG_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadMedia()
        buildUi()
        loadAllDeviceMedia()
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
        buttons.addView(Button(this).apply { text = "갤러리 전체 불러오기"; setOnClickListener { loadAllDeviceMedia() } })
        buttons.addView(Button(this).apply { text = "사진 추가"; setOnClickListener { pick("image/*", 7101) } })
        buttons.addView(Button(this).apply { text = "동영상 추가"; setOnClickListener { pick("video/*", 7102) } })
        buttons.addView(Button(this).apply { text = "GIF 추가"; setOnClickListener { pick("image/gif", 7103) } })
        buttons.addView(Button(this).apply { text = "동영상/GIF 라이브 배경"; setOnClickListener { setLiveVideoWallpaper() } })
        buttons.addView(Button(this).apply { text = "홈 배경"; setOnClickListener { target = WallpaperManager.FLAG_SYSTEM; Toast.makeText(this@WallpaperStudioActivity, "홈 배경 적용 대상으로 선택됨", Toast.LENGTH_SHORT).show() } })
        buttons.addView(Button(this).apply { text = "잠금 화면"; setOnClickListener { target = WallpaperManager.FLAG_LOCK; Toast.makeText(this@WallpaperStudioActivity, "잠금 화면 적용 대상으로 선택됨", Toast.LENGTH_SHORT).show() } })
        buttons.addView(Button(this).apply { text = "외부 화면용 저장"; setOnClickListener { saveFoldWallpaper("folded_wallpaper_uri") } })
        buttons.addView(Button(this).apply { text = "내부 화면용 저장"; setOnClickListener { saveFoldWallpaper("expanded_wallpaper_uri") } })
        outer.addView(buttons)
        outer.addView(Button(this).apply {
            text = "✨ AI 배경화면 제작 스튜디오"
            setOnClickListener { startActivity(Intent(this@WallpaperStudioActivity, ImageCreatorActivity::class.java)) }
        })
        outer.addView(TextView(this).apply {
            text = "Fold7 전용 기본 배경 · 충전 아트"
            textSize = 18f
            setPadding(0, dp(12), 0, dp(6))
        })
        val builtins = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        builtins.addView(Button(this).apply {
            text = "기본 배경"
            setOnClickListener { selectBuiltin(R.drawable.fold7_wallpaper_preview) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        builtins.addView(Button(this).apply {
            text = "Solar Flare"
            setOnClickListener { selectBuiltin(R.drawable.fold7_charge_solar) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        builtins.addView(Button(this).apply {
            text = "Super Saiyan"
            setOnClickListener { selectBuiltin(R.drawable.fold7_charge_super) }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        outer.addView(builtins)
        preview = FrameLayout(this).apply {
            setBackgroundColor(0xff20242c.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(12) }
        }
        outer.addView(preview)
        outer.addView(Button(this).apply {
            text = "선택한 사진을 배경화면으로 적용"
            setOnClickListener { applyImage() }
        })
        outer.addView(Button(this).apply { text = "⏰ 선택 사진을 매일 자동 변경에 추가"; setOnClickListener { addDailySchedule() } })
        outer.addView(Button(this).apply { text = "자동 변경 일정 보기 / 모두 삭제"; setOnClickListener { showSchedules() } })
        outer.addView(Button(this).apply {
            text = "충전 시 알림 사용"; setOnClickListener { enableChargingAlert() }
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


    private fun loadAllDeviceMedia() {
        val needed = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
        } else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 7250)
            return
        }
        importDeviceMedia()
    }

    private fun importDeviceMedia() {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
        val selection = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?)"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        var added = 0
        try {
            contentResolver.query(collection, projection, selection, args, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val isVideo = cursor.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val base = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    val uri = Uri.withAppendedPath(base, id.toString())
                    if (media.none { it.first == uri }) { media.add(uri to isVideo); added++ }
                }
            }
            persist()
            renderList()
            Toast.makeText(this, "기기 미디어 " + media.size + "개를 불러왔습니다. 새로 추가 " + added + "개", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "미디어를 읽지 못했습니다: " + (e.localizedMessage ?: "권한을 확인하세요."), Toast.LENGTH_LONG).show()
        }
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
        if (resultCode != RESULT_OK || requestCode !in 7101..7103) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        val isVideo = requestCode == 7102
        val isGif = requestCode == 7103
        if (media.none { it.first == uri }) media.add(uri to (isVideo || isGif))
        selectedUri = uri
        selectedIsVideo = isVideo || isGif
        prefs.edit().putBoolean("selected_is_gif", isGif).apply()
        persist()
        showPreview()
        renderList()
    }

    private fun selectBuiltin(resId: Int) {
        selectedBuiltinResId = resId
        selectedUri = null
        selectedIsVideo = false
        showPreview()
    }

    private fun showPreview() {
        videoView?.stopPlayback()
        videoView = null
        preview.removeAllViews()
        if (selectedBuiltinResId != 0) {
            preview.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(selectedBuiltinResId)
            }, FrameLayout.LayoutParams(-1, -1))
            return
        }
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
        if (media.isEmpty()) {
            list.addView(TextView(this).apply { text = "기기 갤러리에서 사진·동영상을 불러오면 여기에 표시됩니다." })
            return
        }
        val grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = true }
        media.forEach { (uri, video) ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setOnClickListener {
                    selectedUri = uri
                    selectedIsVideo = video
                    selectedBuiltinResId = 0
                    showPreview()
                }
            }
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(92))
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (video) setImageResource(android.R.drawable.ic_media_play) else setImageURI(uri)
            }
            tile.addView(thumb)
            tile.addView(TextView(this).apply { text = if (video) "▶ 동영상" else "사진"; textSize = 11f; gravity = Gravity.CENTER; maxLines = 1 })
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        list.addView(grid)
    }

    private fun addDailySchedule() {
        val uri = selectedUri
        if (uri == null || selectedIsVideo) { Toast.makeText(this, "자동 변경에는 사진을 선택하세요.", Toast.LENGTH_SHORT).show(); return }
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val time = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
            val rows = (prefs.getString("daily_wallpaper_schedule", "") ?: "").split("\n").filter { it.isNotBlank() && !it.endsWith("|" + uri.toString()) }.toMutableList()
            rows.add(time + "|" + uri.toString())
            prefs.edit().putString("daily_wallpaper_schedule", rows.sorted().joinToString("\n")).apply()
            scheduleWallpaperAlarm()
            Toast.makeText(this, "매일 " + time + "에 배경을 변경하도록 저장했습니다.", Toast.LENGTH_LONG).show()
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
    }

    private fun showSchedules() {
        val rows = (prefs.getString("daily_wallpaper_schedule", "") ?: "").split("\n").filter { it.isNotBlank() }
        val message = if (rows.isEmpty()) "등록된 일정이 없습니다." else rows.joinToString("\n") { it.substringBefore("|") + " · " + it.substringAfter("|").substringAfterLast("/") }
        AlertDialog.Builder(this).setTitle("매일 배경 자동 변경").setMessage(message)
            .setNeutralButton("모두 삭제") { _, _ -> prefs.edit().remove("daily_wallpaper_schedule").remove("daily_wallpaper_last_applied").apply(); Toast.makeText(this, "일정을 삭제했습니다.", Toast.LENGTH_SHORT).show() }
            .setPositiveButton("확인", null).show()
    }

    private fun scheduleWallpaperAlarm() {
        val alarm = getSystemService(ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DailyWallpaperReceiver::class.java).setAction(DailyWallpaperReceiver.ACTION_TICK)
        val pending = PendingIntent.getBroadcast(this, 7310, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000L, 15 * 60_000L, pending)
    }
    private fun saveFoldWallpaper(key: String) {
        val uri = selectedUri
        if (uri == null || selectedIsVideo) {
            Toast.makeText(this, "외부·내부 화면용으로 저장할 사진을 선택하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString(key, uri.toString()).apply()
        val label = if (key == "folded_wallpaper_uri") "커버(외부) 화면" else "내부 화면"
        Toast.makeText(this, "$label 전용 배경으로 보관했습니다. 실제 적용은 삼성 배경화면 설정에서 해당 화면을 선택해야 합니다.", Toast.LENGTH_LONG).show()
    }

    private fun enableChargingAlert() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7201)
            return
        }
        getSharedPreferences("wallpaper_studio", MODE_PRIVATE).edit().putBoolean("charging_alert_enabled", true).apply()
        Toast.makeText(this, "충전 연결 시 영상 알림을 표시합니다. 알림을 눌러 미리보기를 여세요.", Toast.LENGTH_LONG).show()
    }


    @Deprecated("Legacy permission callback retained for compatibility")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7250) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) importDeviceMedia()
            else Toast.makeText(this, "전체 미디어를 보려면 사진·동영상 접근을 허용하세요.", Toast.LENGTH_LONG).show()
            return
        }
        if (requestCode == 7201 && grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("charging_alert_enabled", true).apply()
            Toast.makeText(this, "충전 연결 시 알림을 표시합니다.", Toast.LENGTH_LONG).show()
        } else if (requestCode == 7201) {
            Toast.makeText(this, "충전 알림을 사용하려면 알림 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setLiveVideoWallpaper() {
        val uri = selectedUri
        if (uri == null || !selectedIsVideo) {
            Toast.makeText(this, "먼저 동영상 또는 GIF를 선택하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val isGif = contentResolver.getType(uri)?.contains("gif", ignoreCase = true) == true || prefs.getBoolean("selected_is_gif", false)
        prefs.edit().putString("live_video_uri", uri.toString()).putString("live_media_type", if (isGif) "gif" else "video").apply()
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    android.content.ComponentName(this@WallpaperStudioActivity, VideoWallpaperService::class.java))
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "라이브 배경 설정 화면을 열 수 없습니다: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveChargingVideo() {
        val uri = selectedUri
        if (uri == null || !selectedIsVideo) {
            Toast.makeText(this, "먼저 충전 효과로 사용할 동영상을 선택하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("charging_video_uri", uri.toString()).apply()
        Toast.makeText(this, "충전 애니메이션 영상으로 저장했습니다.", Toast.LENGTH_LONG).show()
    }

    private fun previewChargingVideo() {
        val uri = prefs.getString("charging_video_uri", null)?.let { Uri.parse(it) }
        if (uri == null) {
            Toast.makeText(this, "먼저 동영상을 선택하고 충전 애니메이션으로 저장하세요.", Toast.LENGTH_LONG).show()
            return
        }
        val player = VideoView(this)
        AlertDialog.Builder(this).setTitle("충전 애니메이션 미리보기")
            .setView(player).setPositiveButton("닫기", null).show()
        player.setVideoURI(uri)
        player.setOnPreparedListener { it.isLooping = true; player.start() }
    }

    private fun applyImage() {
        val uri = selectedUri
        if (selectedBuiltinResId == 0 && (uri == null || selectedIsVideo)) {
            Toast.makeText(this, "사진 또는 기본 배경을 선택하세요.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (selectedBuiltinResId != 0) {
                resources.openRawResource(selectedBuiltinResId).use { input ->
                    WallpaperManager.getInstance(this).setStream(input, null, true, target)
                }
            } else {
                contentResolver.openInputStream(uri!!).use { input ->
                    if (input == null) throw IllegalStateException("이미지를 열 수 없습니다.")
                    WallpaperManager.getInstance(this).setStream(input, null, true, target)
                }
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
