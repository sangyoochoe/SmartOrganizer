package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CyberDashboardActivity : Activity() {
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var clock:TextView
    private lateinit var date:TextView
    private lateinit var battery:TextView
    private lateinit var mode:TextView
    private lateinit var panel:CyberPanel
    private var boost=false
    private val tick=object:Runnable {
        override fun run() {
            val now=Date()
            clock.text=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(now)
            date.text=SimpleDateFormat("yyyy.MM.dd  EEEE",Locale.getDefault()).format(now)
            val b=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level=b?.getIntExtra("level",-1) ?: -1
            val scale=b?.getIntExtra("scale",100) ?: 100
            battery.text="BATTERY  "+if(level>=0) "${level*100/scale}%" else "--"
            handler.postDelayed(this,1000)
        }
    }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(18),dp(22),dp(18),dp(22)); setBackgroundColor(Color.rgb(3,9,17)) }
        root.addView(TextView(this).apply { text="ASURADA  /  CYBER FORMULA"; textSize=14f; letterSpacing=.2f; setTextColor(0xff69dfff.toInt()); gravity=Gravity.CENTER })
        clock=TextView(this).apply { textSize=48f; typeface=android.graphics.Typeface.MONOSPACE; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(0,dp(22),0,0) }
        date=TextView(this).apply { textSize=14f; setTextColor(0xff8ba8bd.toInt()); gravity=Gravity.CENTER }
        battery=TextView(this).apply { textSize=14f; setTextColor(0xff69dfff.toInt()); gravity=Gravity.CENTER; setPadding(0,dp(8),0,dp(14)) }
        root.addView(clock); root.addView(date); root.addView(battery)
        panel=CyberPanel()
        root.addView(panel,LinearLayout.LayoutParams(-1,0,1f))
        mode=TextView(this).apply { text="NORMAL DRIVE"; textSize=18f; gravity=Gravity.CENTER; setTextColor(0xff69dfff.toInt()); setPadding(0,dp(12),0,dp(8)) }
        root.addView(mode)
        val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply { text="◀ NAV"; setOnClickListener { runOrChooseApp("left") } },LinearLayout.LayoutParams(0,dp(56),1f))
        controls.addView(Button(this).apply { text="ZERO BOOST"; setOnClickListener { boost=!boost; updateMode() } },LinearLayout.LayoutParams(0,dp(56),1f))
        controls.addView(Button(this).apply { text="APP ▶"; setOnClickListener { runOrChooseApp("right") } },LinearLayout.LayoutParams(0,dp(56),1f))
        root.addView(controls)
        root.addView(TextView(this).apply { text="화면 터치: 부스터 모드 전환 · 좌/우 버튼: 실행할 앱 지정"; textSize=11f; gravity=Gravity.CENTER; setTextColor(0xff7893a7.toInt()); setPadding(0,dp(10),0,0) })
        panel.setOnClickListener { boost=!boost; updateMode() }
        setContentView(root)
    }

    private fun updateMode() {
        getSharedPreferences("cyber_dashboard", MODE_PRIVATE).edit().putBoolean("widget_boost", boost).apply()
        mode.text=if(boost) "ZERO NOISE  /  BOOSTER ACTIVE" else "NORMAL DRIVE"
        mode.setTextColor(if(boost) 0xffffb45c.toInt() else 0xff69dfff.toInt())
        panel.boost=boost; panel.invalidate()
        CyberDashboardWidgetProvider.updateAll(this)
    }

    private fun runOrChooseApp(slot:String) {
        val pkg=getSharedPreferences("cyber_dashboard",MODE_PRIVATE).getString(slot,null)
        if(pkg.isNullOrBlank()) { chooseApp(slot); return }
        val launch=packageManager.getLaunchIntentForPackage(pkg)
        if(launch!=null) startActivity(launch) else {
            getSharedPreferences("cyber_dashboard",MODE_PRIVATE).edit().remove(slot).apply()
            chooseApp(slot)
        }
    }

    private fun chooseApp(slot:String) {
        val apps=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }.distinctBy { it.second }.sortedBy { it.first }
        val labels=apps.map { it.first }.toTypedArray()
        AlertDialog.Builder(this).setTitle(if(slot=="left") "왼쪽 버튼 앱 지정" else "오른쪽 버튼 앱 지정")
            .setItems(labels) { _,which ->
                val pkg=apps[which].second
                getSharedPreferences("cyber_dashboard",MODE_PRIVATE).edit().putString(slot,pkg).apply()
                CyberDashboardWidgetProvider.updateAll(this)
                Toast.makeText(this,"버튼에 앱을 지정했습니다. 홈 위젯에도 반영했습니다.",Toast.LENGTH_SHORT).show()
            }.setNegativeButton("취소",null).show()
    }

    private inner class CyberPanel:View(this) {
        var boost=false
        private val p=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas) {
            super.onDraw(c)
            val w=width.toFloat(); val h=height.toFloat()
            p.color=if(boost) 0xff102a45.toInt() else 0xff0b1b2b.toInt()
            c.drawRoundRect(0f,0f,w,h,dp(22).toFloat(),dp(22).toFloat(),p)
            p.color=if(boost) 0xffffa84d.toInt() else 0xff43c9ed.toInt()
            p.style=Paint.Style.STROKE; p.strokeWidth=dp(2).toFloat()
            c.drawRoundRect(dp(8).toFloat(),dp(8).toFloat(),w-dp(8),h-dp(8),dp(18).toFloat(),dp(18).toFloat(),p)
            p.style=Paint.Style.FILL
            val cx=w/2; val cy=h/2
            p.color=0xff172f46.toInt(); c.drawCircle(cx,cy,dp(104).toFloat(),p)
            p.color=if(boost) 0xffffa84d.toInt() else 0xff65ddff.toInt()
            p.style=Paint.Style.STROKE; p.strokeWidth=dp(3).toFloat()
            c.drawArc(cx-dp(88),cy-dp(88),cx+dp(88),cy+dp(88),210f,300f,false,p)
            c.drawArc(cx-dp(72),cy-dp(72),cx+dp(72),cy+dp(72),25f,125f,false,p)
            p.style=Paint.Style.FILL; p.textAlign=Paint.Align.CENTER; p.typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD); p.textSize=dp(18).toFloat()
            c.drawText(if(boost) "BOOST" else "SYSTEM",cx,cy+dp(6),p)
            p.textSize=dp(10).toFloat(); p.color=0xff91afc4.toInt()
            c.drawText("TOUCH TO SWITCH MODE",cx,cy+dp(130),p)
        }
    }
    override fun onResume(){ super.onResume(); handler.post(tick) }
    override fun onPause(){ handler.removeCallbacks(tick); super.onPause() }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
