package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.widget.*

/** Hosts real third-party Android widgets inside the Smart Organizer desktop workspace. */
class AndroidWidgetHostActivity : Activity() {
    private val hostId = 0x534f
    private lateinit var host: AppWidgetHost
    private lateinit var manager: AppWidgetManager
    private lateinit var list: LinearLayout
    private val prefs by lazy { getSharedPreferences("android_widget_host", MODE_PRIVATE) }
    private var pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingProvider: AppWidgetProviderInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, hostId)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(0xff0a1523.toInt())
        }
        root.addView(TextView(this).apply {
            text = "ANDROID WIDGET HOST"
            textSize = 22f
            setTextColor(0xff83eaff.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "설치된 앱의 실제 Android 위젯을 추가합니다. 위젯 제공 앱과 Android 버전에 따라 크기·기능이 다릅니다."
            textSize = 13f
            setTextColor(0xffb5c6d3.toInt())
            setPadding(0, dp(8), 0, dp(12))
        })
        root.addView(Button(this).apply {
            text = "＋ 설치된 앱 위젯 추가"
            setOnClickListener { showProviders() }
        })
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderHosted()
    }

    override fun onStart() {
        super.onStart()
        try { host.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        try { host.stopListening() } catch (_: Exception) {}
        super.onStop()
    }

    private fun showProviders() {
        val providers = manager.installedProviders.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (providers.isEmpty()) {
            Toast.makeText(this, "사용 가능한 앱 위젯이 없습니다.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = providers.map { "${it.loadLabel(packageManager)} · ${it.provider.packageName}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("추가할 Android 위젯")
            .setItems(labels) { _, index -> beginAdd(providers[index]) }
            .setNegativeButton("취소", null).show()
    }

    private fun beginAdd(provider: AppWidgetProviderInfo) {
        pendingId = host.allocateAppWidgetId()
        pendingProvider = provider
        val allowed = try { manager.bindAppWidgetIdIfAllowed(pendingId, provider.provider) } catch (_: Exception) { false }
        if (allowed) finishBinding(provider) else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            startActivityForResult(bind, 8201)
        }
    }

    private fun finishBinding(provider: AppWidgetProviderInfo) {
        val configure = provider.configure
        if (configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId)
            }
            startActivityForResult(intent, 8202)
        } else persistPending()
    }

    private fun persistPending() {
        if (pendingId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val ids = prefs.getString("ids", "").orEmpty().split(",").filter { it.isNotBlank() }.toMutableSet()
        ids.add(pendingId.toString())
        prefs.edit().putString("ids", ids.joinToString(",")).apply()
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
        renderHosted()
    }

    @Deprecated("Legacy activity result API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 8201) {
            if (resultCode == RESULT_OK) pendingProvider?.let { finishBinding(it) } ?: persistPending()
            else discardPending()
        } else if (requestCode == 8202) {
            if (resultCode == RESULT_OK) persistPending() else discardPending()
        }
    }

    private fun discardPending() {
        if (pendingId != AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingId)
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingProvider = null
    }

    private fun renderHosted() {
        list.removeAllViews()
        val ids = prefs.getString("ids", "").orEmpty().split(",").mapNotNull { it.toIntOrNull() }
        if (ids.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "아직 추가한 위젯이 없습니다."
                setTextColor(0xffb5c6d3.toInt())
                setPadding(0, dp(20), 0, dp(20))
            })
            return
        }
        ids.forEach { id ->
            val info = manager.getAppWidgetInfo(id)
            if (info == null) {
                removeId(id)
                return@forEach
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundColor(0xff17283a.toInt())
            }
            card.addView(TextView(this).apply {
                text = info.loadLabel(packageManager)
                textSize = 14f
                setTextColor(-1)
            })
            val widget = host.createView(this, id, info)
            widget.setAppWidget(id, info)
            widget.updateAppWidgetSize(null, info.minWidth, info.minHeight, info.minWidth, info.minHeight)
            card.addView(widget, LinearLayout.LayoutParams(-1, dp(info.minHeight.coerceIn(100, 360))))
            card.addView(Button(this).apply {
                text = "이 위젯 삭제"
                setOnClickListener {
                    host.deleteAppWidgetId(id)
                    removeId(id)
                    renderHosted()
                }
            })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
    }

    private fun removeId(id: Int) {
        val ids = prefs.getString("ids", "").orEmpty().split(",").filter { it.isNotBlank() && it != id.toString() }
        prefs.edit().putString("ids", ids.joinToString(",")).apply()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
