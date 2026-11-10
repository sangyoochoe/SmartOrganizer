package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.content.pm.PackageManager

class LauncherActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("photo_launcher", MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var grid: GridLayout
    private lateinit var title: TextView
    private var editing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
    }

    private fun buildHome() {
        root = FrameLayout(this)
        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val saved = prefs.getString("wallpaper", null)
            if (saved != null) try {
                contentResolver.openInputStream(Uri.parse(saved))?.use { setImageBitmap(BitmapFactory.decodeStream(it)) }
            } catch (_: Exception) {}
            setBackgroundColor(Color.rgb(35, 38, 48))
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))
        val shade = View(this).apply { setBackgroundColor(0x55000000) }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(18))
        }
        title = TextView(this).apply {
            text = "나의 공간"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f))
            addView(actionButton("사진") { chooseWallpaper() })
            addView(actionButton("앱 추가") { chooseApps() })
            addView(actionButton("편집") { editing = !editing; title.text = if (editing) "아이콘을 길게 눌러 순서 변경" else "나의 공간" })
        }
        grid = GridLayout(this).apply { alignmentMode = GridLayout.ALIGN_BOUNDS }
        val scroll = ScrollView(this).apply { addView(grid) }
        content.addView(toolbar)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val hint = TextView(this).apply {
            text = "사진을 배경으로 설정하고 앱을 원하는 위치에 배치하세요."
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        content.addView(hint)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        renderApps()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 11f
        setOnClickListener { action() }
    }

    private fun renderApps() {
        grid.removeAllViews()
        val names = prefs.getString("apps", "")!!.split("\n").filter { it.isNotBlank() }.toMutableList()
        val columns = if (resources.configuration.screenWidthDp >= 600) 6 else 4
        grid.columnCount = columns
        names.forEachIndexed { index, pkg ->
            val app = try { packageManager.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
            if (app == null) return@forEachIndexed
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                val icon = ImageView(context).apply {
                    setImageDrawable(packageManager.getApplicationIcon(app))
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                }
                val label = TextView(context).apply {
                    text = packageManager.getApplicationLabel(app)
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    gravity = Gravity.CENTER
                    maxLines = 2
                }
                addView(icon); addView(label)
                setOnClickListener {
                    if (editing) {
                        val choices = arrayOf("앞으로 이동", "뒤로 이동", "홈에서 제거")
                        AlertDialog.Builder(this@LauncherActivity).setItems(choices) { _, which ->
                            when (which) {
                                0 -> if (index > 0) { val v = names.removeAt(index); names.add(index - 1, v) }
                                1 -> if (index < names.lastIndex) { val v = names.removeAt(index); names.add(index + 1, v) }
                                2 -> names.remove(pkg)
                            }
                            saveApps(names); renderApps()
                        }.show()
                    } else {
                        val launch = packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) startActivity(launch) else Toast.makeText(context, "실행할 수 없는 앱입니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                setOnLongClickListener {
                    editing = true
                    Toast.makeText(context, "편집 모드: 아이콘을 눌러 이동하거나 제거하세요.", Toast.LENGTH_LONG).show()
                    true
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(94)
                columnSpec = GridLayout.spec(index % columns, 1f)
                rowSpec = GridLayout.spec(index / columns)
            }
            grid.addView(cell, params)
        }
    }

    private fun saveApps(items: List<String>) {
        prefs.edit().putString("apps", items.distinct().joinToString("\n")).apply()
    }

    private fun chooseApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }.sortedBy { it.first.lowercase() }
        val existing = prefs.getString("apps", "")!!.split("\n").toSet()
        val available = apps.filter { it.second !in existing }
        val labels = available.map { it.first }.toTypedArray()
        AlertDialog.Builder(this).setTitle("홈 화면에 추가할 앱")
            .setItems(labels) { _, which ->
                val current = prefs.getString("apps", "")!!.split("\n").filter { it.isNotBlank() }
                saveApps(current + available[which].second)
                renderApps()
            }.setNegativeButton("닫기", null).show()
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 4201)
    }

    @Deprecated("Legacy result API used for broad Android compatibility")
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
