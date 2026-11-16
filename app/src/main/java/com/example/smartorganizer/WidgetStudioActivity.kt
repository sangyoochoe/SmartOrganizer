package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A configurable in-app widget canvas. Layouts are saved as portable JSON.
 * For launcher placement, use AndroidWidgetHostActivity to host real AppWidgets.
 */
class WidgetStudioActivity : Activity() {
    private data class Item(val id: Long, var type: String, var label: String, var value: String)
    private val items = mutableListOf<Item>()
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("widget_studio", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xff081321.toInt())
        }
        root.addView(TextView(this).apply {
            text = "PERSONAL WIDGET STUDIO"
            textSize = 22f
            setTextColor(0xff82eaff.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "시계·날짜·배터리·텍스트 카드를 구성하고 순서를 조정하세요. 이 화면의 편집 위젯은 앱 내부 캔버스이며, 홈 화면에는 별도의 Android 위젯 호스트를 이용합니다."
            textSize = 13f
            setTextColor(0xffb8c9d8.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "＋ 카드"; setOnClickListener { addItem() } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = "JSON 내보내기"; setOnClickListener { exportLayout() } }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply { text = "JSON 가져오기"; setOnClickListener { importLayout() } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)
        status = TextView(this).apply { setTextColor(0xffb8c9d8.toInt()); textSize = 12f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun addItem() {
        val types = arrayOf("시계", "날짜", "배터리", "텍스트")
        AlertDialog.Builder(this).setTitle("추가할 위젯 카드").setItems(types) { _, which ->
            val type = when (which) { 0 -> "clock"; 1 -> "date"; 2 -> "battery"; else -> "text" }
            val label = when (type) { "clock" -> "시계"; "date" -> "날짜"; "battery" -> "배터리"; else -> "메모" }
            val value = if (type == "text") "여기에 메모를 입력하세요" else ""
            items.add(Item(System.currentTimeMillis(), type, label, value))
            save(); render()
        }.setNegativeButton("취소", null).show()
    }

    private fun editItem(item: Item) {
        val input = EditText(this).apply {
            setText(if (item.type == "text") item.value else item.label)
            hint = if (item.type == "text") "표시할 문구" else "카드 제목"
        }
        AlertDialog.Builder(this).setTitle("카드 편집")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    if (item.type == "text") item.value = value else item.label = value
                    save(); render()
                }
            }
            .setNeutralButton("삭제") { _, _ -> items.remove(item); save(); render() }
            .setNegativeButton("취소", null).show()
    }

    private fun render() {
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "카드가 없습니다. ＋ 카드를 눌러 위젯을 구성하세요."
                setTextColor(-1); setPadding(0, dp(24), 0, dp(24))
            })
        }
        items.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(0xff172a3d.toInt())
            }
            val title = TextView(this).apply {
                text = item.label
                textSize = 13f
                setTextColor(0xff82eaff.toInt())
            }
            val value = TextView(this).apply {
                textSize = if (item.type == "clock") 32f else 20f
                setTextColor(-1)
                setPadding(0, dp(5), 0, dp(5))
                text = when (item.type) {
                    "clock" -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    "date" -> SimpleDateFormat("yyyy년 MM월 dd일 EEEE", Locale.getDefault()).format(Date())
                    "battery" -> "배터리 ${batteryLevel()}%"
                    else -> item.value
                }
            }
            card.addView(title); card.addView(value)
            card.setOnClickListener { editItem(item) }
            val controls = LinearLayout(this).apply { gravity = Gravity.END }
            controls.addView(Button(this@WidgetStudioActivity).apply {
                text = "↑"; isEnabled = index > 0
                setOnClickListener { items.removeAt(index); items.add(index - 1, item); save(); render() }
            })
            controls.addView(Button(this@WidgetStudioActivity).apply {
                text = "↓"; isEnabled = index < items.lastIndex
                setOnClickListener { items.removeAt(index); items.add(index + 1, item); save(); render() }
            })
            controls.addView(Button(this@WidgetStudioActivity).apply {
                text = "편집"; setOnClickListener { editItem(item) }
            })
            card.addView(controls)
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        status.text = "카드 ${items.size}개 · 자동 저장됨 · 카드를 눌러 편집"
    }

    private fun batteryLevel(): Int {
        val manager = getSystemService(BATTERY_SERVICE) as BatteryManager
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }

    private fun save() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("type", item.type)
                .put("label", item.label).put("value", item.value))
        }
        prefs.edit().putString("layout", array.toString()).apply()
    }

    private fun load() {
        items.clear()
        val raw = prefs.getString("layout", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length().coerceAtMost(50)) {
                val obj = array.getJSONObject(i)
                val type = obj.optString("type")
                if (type !in setOf("clock", "date", "battery", "text")) continue
                items.add(Item(obj.optLong("id", System.currentTimeMillis() + i), type,
                    obj.optString("label", type).take(80), obj.optString("value", "").take(500)))
            }
        } catch (_: Exception) { items.clear() }
    }

    private fun exportLayout() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "smart-organizer-widget-layout.json")
        }
        startActivityForResult(intent, 9301)
    }

    private fun importLayout() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
        }
        startActivityForResult(intent, 9302)
    }

    @Deprecated("Legacy activity result API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 9301) {
                contentResolver.openOutputStream(uri)?.use { it.write(prefs.getString("layout", "[]")!!.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "위젯 배치를 JSON으로 내보냈습니다.", Toast.LENGTH_LONG).show()
            } else if (requestCode == 9302) {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                require(text.toByteArray(Charsets.UTF_8).size <= 256 * 1024) { "파일이 너무 큽니다." }
                val parsed = JSONArray(text)
                require(parsed.length() <= 50) { "카드는 최대 50개까지 가져올 수 있습니다." }
                items.clear()
                for (i in 0 until parsed.length()) {
                    val obj = parsed.getJSONObject(i)
                    val type = obj.optString("type")
                    require(type in setOf("clock", "date", "battery", "text")) { "지원하지 않는 카드 유형입니다." }
                    items.add(Item(System.currentTimeMillis() + i, type,
                        obj.optString("label", type).take(80), obj.optString("value", "").take(500)))
                }
                save(); render()
                Toast.makeText(this, "위젯 배치를 가져왔습니다.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "위젯 파일 처리 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
