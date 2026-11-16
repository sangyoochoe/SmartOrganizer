package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*

/** Sailor Moon setup hub: creates assets in-app and guides Samsung-only steps. */
class SailorMoonSetupActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("sailor_moon_setup", MODE_PRIVATE) }
    private val dark=Color.rgb(7,17,29)
    private val panel=Color.rgb(18,36,53)
    private val mint=Color.rgb(104,232,218)
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); render() }

    private fun render() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); setBackgroundColor(dark) }
        root.addView(TextView(this).apply { text="☾  세일러문 테마"; textSize=26f; setTextColor(Color.WHITE); setTypeface(null,1) })
        root.addView(TextView(this).apply { text="GALAXY Z FOLD · THEME SETUP"; textSize=11f; letterSpacing=.18f; setTextColor(mint); setPadding(0,dp(8),0,dp(16)) })
        status=TextView(this).apply { setTextColor(0xffa6bdcc.toInt()); textSize=13f; text="선택한 자료와 설정 단계를 이곳에서 관리합니다."; setPadding(0,0,0,dp(12)) }
        root.addView(status)
        val scroll=ScrollView(this); val list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        card(list,"01  충전 배경화면","충전 시 사용할 이미지·영상을 선택하고 모드 및 루틴 설정으로 이동") {
            startActivity(Intent(this,WallpaperStudioActivity::class.java))
            openSettings("android.settings.SETTINGS")
        }
        card(list,"02  전면 움직이는 배경","GIF·동영상 라이브 배경과 움직이는 요소 제작") {
            startActivity(Intent(this,WallpaperStudioActivity::class.java))
        }
        card(list,"03  내부 잠금화면","영상·GIF 배경을 고르고 잠금화면 적용 대상을 선택") {
            startActivity(Intent(this,WallpaperStudioActivity::class.java))
        }
        card(list,"04  퀵패널 커스텀","달·태양·비행기·손전등 등 퀵패널용 PNG 아이콘 제작") {
            startActivity(Intent(this,QuickPanelIconStudioActivity::class.java))
        }
        card(list,"05  반응형 내부 홈 화면","움직이는 배경과 Fold 접힘·펼침 앱 배치 관리") {
            startActivity(Intent(this,LauncherActivity::class.java))
        }
        card(list,"06  앱 아이콘 커스텀","앱별 이미지 아이콘은 커스텀 런처에서 적용") {
            startActivity(Intent(this,LauncherActivity::class.java))
        }
        card(list,"07  Good Lock · QuickStar · 테마파크","설치된 삼성 모듈을 바로 열거나 Galaxy Store에서 설치 페이지를 엽니다.") {
            showSamsungModules()
        }
        setContentView(root)
    }

    private fun card(parent:LinearLayout,title:String,desc:String,action:()->Unit) {
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(16)); setBackgroundColor(panel)
            setOnClickListener { action() }
        }
        box.addView(TextView(this).apply { text=title+"   →"; textSize=18f; setTextColor(Color.WHITE); setTypeface(null,1) })
        box.addView(TextView(this).apply { text=desc; textSize=12f; setTextColor(0xff9ab0c2.toInt()); setPadding(0,dp(7),0,0) })
        parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }

    private fun showSamsungModules() {
        val modules = arrayOf(
            "Good Lock" to "com.samsung.android.goodlock",
            "QuickStar" to "com.samsung.android.qstuner",
            "Theme Park" to "com.samsung.android.themecenter",
            "Wonderland" to "com.samsung.android.wonderland"
        )
        AlertDialog.Builder(this).setTitle("삼성 커스터마이징 모듈")
            .setItems(modules.map { it.first }.toTypedArray()) { _, which ->
                val (name, packageName) = modules[which]
                val launch = packageManager.getLaunchIntentForPackage(packageName)
                if (launch != null) {
                    try { startActivity(launch) } catch (_: Exception) { openStore(packageName) }
                } else {
                    AlertDialog.Builder(this).setMessage("$name 모듈이 설치되어 있지 않습니다. Galaxy Store 페이지를 열까요?")
                        .setPositiveButton("스토어 열기") { _, _ -> openStore(packageName) }
                        .setNegativeButton("취소", null).show()
                }
            }.setNegativeButton("닫기", null).show()
    }

    private fun openStore(packageName: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
        catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://galaxystore.samsung.com/detail/$packageName"))) }
            catch (_: Exception) { openSettings("android.settings.SETTINGS") }
        }
    }

    private fun openSettings(action:String) {
        try { startActivity(Intent(action)) } catch (_:Exception) {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_:Exception) {}
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
