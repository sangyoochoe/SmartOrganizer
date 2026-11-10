package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.max
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

class LauncherActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("photo_launcher", MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var grid: GridLayout
    private lateinit var title: TextView
    private var editing = false
    private val expanded get() = resources.configuration.screenWidthDp >= 600
    private val layoutKey get() = if (expanded) "apps_expanded" else "apps_folded"
    private val layoutName get() = if (expanded) "펼친 화면" else "접힌 화면"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun buildHome() {
        root = FrameLayout(this)
        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(35, 38, 48))
            prefs.getString("wallpaper", null)?.let { saved ->
                try { contentResolver.openInputStream(Uri.parse(saved))?.use { setImageBitmap(BitmapFactory.decodeStream(it)) } } catch (_: Exception) {}
            }
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { setBackgroundColor(0x55000000) }, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(18))
        }
        title = TextView(this).apply {
            text = "나의 공간 · $layoutName"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(actionButton("배경") { chooseWallpaper() })
            addView(actionButton("AI 배치") { analyzePhotoAndSuggest() })
            addView(actionButton("앱 +") { chooseApps() })
            addView(actionButton("편집") { editing = !editing; title.text = if (editing) "아이콘을 길게 눌러 배치 편집" else "나의 공간 · $layoutName" })
        }
        grid = GridLayout(this)
        grid.columnCount = if (expanded) 6 else 4
        grid.setPadding(0, dp(12), 0, dp(12))
        grid.setOnDragListener { _, event ->
            if (event.action == DragEvent.ACTION_DROP) {
                val from = event.clipData.getItemAt(0).text?.toString()
                val to = event.localState as? String
                if (from != null && to != null && from != to) reorder(from, to)
            }
            true
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        content.addView(toolbar)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(TextView(this).apply {
            text = "사진 속 여백을 고려해 앱을 배치하세요. 접힌 화면과 펼친 화면의 배치는 따로 저장됩니다."
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        renderApps()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        setOnClickListener { action() }
    }

    private fun currentApps(): MutableList<String> =
        prefs.getString(layoutKey, "")!!.split("\n").filter { it.isNotBlank() }.toMutableList()

    private fun renderApps() {
        grid.removeAllViews()
        val names = currentApps()
        names.forEach { pkg ->
            val app = try { packageManager.getApplicationInfo(pkg, 0) } catch (_: Exception) { null } ?: return@forEach
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(3), dp(10), dp(3), dp(10))
                addView(ImageView(context).apply {
                    setImageDrawable(packageManager.getApplicationIcon(app))
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                })
                addView(TextView(context).apply {
                    text = packageManager.getApplicationLabel(app)
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxLines = 2
                })
                setOnClickListener {
                    if (editing) showItemActions(pkg) else packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                }
                setOnLongClickListener {
                    if (editing) {
                        val clip = ClipData.newPlainText("app-package", pkg)
                        startDragAndDrop(clip, View.DragShadowBuilder(this), pkg, 0)
                        Toast.makeText(context, "다른 아이콘 위에 놓아 순서를 바꾸세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        editing = true
                        Toast.makeText(context, "편집 모드가 켜졌습니다. 다시 길게 눌러 이동하세요.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                setOnDragListener { _, event ->
                    if (event.action == DragEvent.ACTION_DROP) {
                        val from = event.localState as? String
                        if (from != null && from != pkg) reorder(from, pkg)
                    }
                    true
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(92)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            grid.addView(cell, params)
        }
    }

    private fun reorder(from: String, to: String) {
        val items = currentApps()
        val a = items.indexOf(from)
        val b = items.indexOf(to)
        if (a >= 0 && b >= 0) {
            items.removeAt(a)
            items.add(b.coerceAtMost(items.size), from)
            saveApps(items)
            renderApps()
        }
    }

    private fun showItemActions(pkg: String) {
        val items = currentApps()
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        AlertDialog.Builder(this).setTitle(label)
            .setItems(arrayOf("앞으로 이동", "뒤로 이동", "홈에서 제거")) { _, which ->
                val index = items.indexOf(pkg)
                when (which) {
                    0 -> if (index > 0) { items.removeAt(index); items.add(index - 1, pkg) }
                    1 -> if (index in 0 until items.lastIndex) { items.removeAt(index); items.add(index + 1, pkg) }
                    2 -> items.remove(pkg)
                }
                saveApps(items)
                renderApps()
            }.show()
    }

    private fun saveApps(items: List<String>) {
        prefs.edit().putString(layoutKey, items.distinct().joinToString("\n")).apply()
    }

    private fun chooseApps() {
        val available = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }.sortedBy { it.first.lowercase() }
        val current = currentApps()
        val choices = available.filter { it.second !in current }
        if (choices.isEmpty()) { Toast.makeText(this, "추가할 앱이 없습니다.", Toast.LENGTH_SHORT).show(); return }
        val selected = BooleanArray(choices.size)
        AlertDialog.Builder(this).setTitle("$layoutName 홈 화면에 추가")
            .setMultiChoiceItems(choices.map { it.first }.toTypedArray(), selected) { _, which, checked -> selected[which] = checked }
            .setNegativeButton("취소", null)
            .setPositiveButton("추가") { _, _ ->
                choices.forEachIndexed { i, app -> if (selected[i]) current.add(app.second) }
                saveApps(current)
                renderApps()
            }.show()
    }

    private fun analyzePhotoAndSuggest() {
        val saved = prefs.getString("wallpaper", null)
        if (saved == null) { Toast.makeText(this, "먼저 배경 사진을 선택하세요.", Toast.LENGTH_SHORT).show(); return }
        val uri = Uri.parse(saved)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } } catch (_: Exception) {}
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) { Toast.makeText(this, "사진을 읽을 수 없습니다.", Toast.LENGTH_LONG).show(); return }
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 1024)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } } catch (_: Exception) { null }
        if (bitmap == null) { Toast.makeText(this, "사진을 불러오지 못했습니다.", Toast.LENGTH_LONG).show(); return }
        Toast.makeText(this, "사진 장면을 기기에서 분석 중입니다…", Toast.LENGTH_SHORT).show()
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        labeler.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener { labels ->
            val ranked = labels.sortedByDescending { it.confidence }.take(8)
            val description = ranked.joinToString(", ") { it.text + " " + (it.confidence * 100).toInt() + "%" }
            val installed = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }.distinctBy { it.first }
            val existing = currentApps()
            val candidates = installed.filter { it.first !in existing }
            fun score(app: Pair<String, String>): Int {
                val text = (app.first + " " + app.second).lowercase()
                return ranked.sumOf { label ->
                    val words = when {
                        label.text.contains("food", true) || label.text.contains("restaurant", true) -> listOf("food", "배달", "음식", "restaurant")
                        label.text.contains("travel", true) || label.text.contains("building", true) -> listOf("map", "지도", "travel", "여행")
                        label.text.contains("sport", true) || label.text.contains("fitness", true) -> listOf("fitness", "운동", "건강", "sport")
                        label.text.contains("music", true) -> listOf("music", "음악", "audio")
                        label.text.contains("plant", true) || label.text.contains("flower", true) || label.text.contains("nature", true) -> listOf("photo", "사진", "weather", "날씨")
                        else -> listOf(label.text.lowercase())
                    }
                    if (words.any { text.contains(it) }) (label.confidence * 10).toInt() else 0
                }
            }
            val suggestions = candidates.sortedByDescending { score(it) }.take(12)
            AlertDialog.Builder(this).setTitle("사진 분석 · 홈 화면 제안")
                .setMessage("인식한 장면: " + if (description.isBlank()) "인식된 라벨 없음" else description +
                    "\n\n설치 앱 이름과 사진 라벨을 비교해 관련 가능성이 있는 앱을 앞쪽에 배치합니다. 앱 추천은 참고용이며 직접 수정할 수 있습니다.\n\n추천 앱: " + suggestions.take(6).joinToString { it.second })
                .setNegativeButton("취소", null).setPositiveButton("제안 적용") { _, _ ->
                    saveApps(existing + suggestions.map { it.first })
                    renderApps()
                    Toast.makeText(this, "사진 분석 기반 앱 배치를 적용했습니다.", Toast.LENGTH_LONG).show()
                }.show()
            bitmap.recycle()
            labeler.close()
        }.addOnFailureListener { error ->
            bitmap.recycle()
            labeler.close()
            Toast.makeText(this, "사진 분석 실패: " + error.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }
    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 4201)
    }

    @Deprecated("Legacy result API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4201 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.edit().putString("wallpaper", uri.toString()).apply()
            buildHome()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
