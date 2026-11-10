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
