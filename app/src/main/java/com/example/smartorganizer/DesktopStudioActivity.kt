package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class DesktopStudioActivity : Activity() {
    private data class Tile(val id: Long, val type: String, var x: Float, var y: Float, var w: Int, var h: Int, var text: String = "", var pkg: String = "")
    private lateinit var canvas: FrameLayout
    private lateinit var heading: TextView
    private lateinit var monitor: FoldStateMonitor
    private var posture = FoldStateMonitor.Posture.COVER_OR_COMPACT
    private val tiles = mutableListOf<Tile>()
    private val prefs by lazy { getSharedPreferences("desktop_studio", MODE_PRIVATE) }
    private var wallpaper: String? = null
    private var widthPx = 0
    private var heightPx = 0
    private val key get() = when(posture) {
        FoldStateMonitor.Posture.COVER_OR_COMPACT -> "cover"
        FoldStateMonitor.Posture.BOOK -> "book"
        FoldStateMonitor.Posture.TABLETOP -> "tabletop"
        FoldStateMonitor.Posture.OPEN_OR_FLAT -> "inner"
    }
    private val label get() = when(posture) {
        FoldStateMonitor.Posture.COVER_OR_COMPACT -> "커버 화면"
        FoldStateMonitor.Posture.BOOK -> "북 모드"
        FoldStateMonitor.Posture.TABLETOP -> "플렉스 모드"
        FoldStateMonitor.Posture.OPEN_OR_FLAT -> "내부 화면"
    }
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshBattery() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = FoldStateMonitor(this) { state ->
            if (posture != state.posture) { save(); posture = state.posture; load(); render() }
        }
        build(); load(); render()
    }
    override fun onResume() {
        super.onResume()
        monitor.start()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    override fun onPause() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        save(); monitor.stop(); super.onPause()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xff0a1523.toInt()) }
        val bar = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(5),dp(4),dp(5),dp(4)); setBackgroundColor(0xff142438.toInt()) }
        heading = TextView(this).apply { textSize=12f; setTextColor(0xff83eaff.toInt()); typeface=Typeface.DEFAULT_BOLD }
        bar.addView(heading,LinearLayout.LayoutParams(0,dp(44),1f))
        bar.addView(button("＋ 위젯"){ addMenu() })
        bar.addView(button("배경"){ pickWallpaper() })
        bar.addView(button("저장"){ save(); toast("배치 저장 완료") })
        canvas = FrameLayout(this).apply { setBackgroundColor(0xff17649a.toInt()); clipChildren=true }
        canvas.addOnLayoutChangeListener { _,l,t,r,b,_,_,_,_ ->
            if(r-l>0 && b-t>0 && (widthPx!=r-l || heightPx!=b-t)){ widthPx=r-l; heightPx=b-t; renderTiles() }
        }
        val taskbar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),dp(3),dp(4),dp(3)); setBackgroundColor(0xee111b27.toInt()) }
        taskbar.addView(button("⊞ 시작"){ startMenu() })
        taskbar.addView(button("메모"){ add("memo") })
        taskbar.addView(button("시계"){ add("clock") })
        taskbar.addView(button("앱"){ pickApp() })
        taskbar.addView(TextView(this).apply { text="  $label"; textSize=10f; setTextColor(Color.LTGRAY) },LinearLayout.LayoutParams(0,dp(40),1f))
        root.addView(bar); root.addView(canvas,LinearLayout.LayoutParams(-1,0,1f)); root.addView(taskbar); setContentView(root)
    }
    private fun button(value:String, action:()->Unit)=Button(this).apply {
        text=value; textSize=10f; isAllCaps=false; minWidth=dp(42); setPadding(dp(3),0,dp(3),0); setOnClickListener{action()}
    }
    private fun addMenu(){
        AlertDialog.Builder(this).setTitle("바탕화면 요소 추가").setItems(arrayOf("시계","날짜","배터리","메모장","앱 바로가기")){_,i->
            when(i){0->add("clock");1->add("date");2->add("battery");3->add("memo");4->pickApp()}
        }.show()
    }
    private fun startMenu(){
        AlertDialog.Builder(this).setTitle("Smart Desktop").setItems(arrayOf("위젯 추가","앱 바로가기 추가","배경 변경","화면 초기화")){_,i->
            when(i){0->addMenu();1->pickApp();2->pickWallpaper();3->
                AlertDialog.Builder(this).setMessage("현재 화면의 요소를 모두 삭제할까요?").setNegativeButton("취소",null)
                    .setPositiveButton("삭제"){_,_->tiles.clear();save();renderTiles()}.show()
            }
        }.show()
    }
    private fun add(type:String,text:String="",pkg:String=""){
        val size=when(type){"clock"->190 to 76;"memo"->210 to 145;"date"->170 to 48;"battery"->150 to 52;else->150 to 75}
        tiles.add(Tile(System.currentTimeMillis(),type,.08f+(tiles.size%4)*.04f,.08f+(tiles.size%5)*.09f,size.first,size.second,text,pkg))
        save();renderTiles()
    }
    private fun pickApp(){
        val apps=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map{it.loadLabel(packageManager).toString() to it.activityInfo.packageName}.distinctBy{it.second}.sortedBy{it.first.lowercase()}
        AlertDialog.Builder(this).setTitle("앱 바로가기 선택").setItems(apps.map{it.first}.toTypedArray()){_,i->add("app",apps[i].first,apps[i].second)}.show()
    }
    private fun pickWallpaper(){
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},7301)
    }
    @Deprecated("Legacy result API retained")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==7301 && resultCode==RESULT_OK){
            val uri=data?.data?:return
            try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
            wallpaper=uri.toString();prefs.edit().putString("wall_$key",wallpaper).apply();render()
        }
    }
    private fun load(){
        wallpaper=prefs.getString("wall_$key",null);tiles.clear()
        (prefs.getString("tiles_$key","")?:"").lineSequence().forEach{line->
            val p=line.split("\t")
            if(p.size==8)try{tiles.add(Tile(p[0].toLong(),p[1],p[2].toFloat(),p[3].toFloat(),p[4].toInt(),p[5].toInt(),p[6].replace("~n","\n"),p[7]))}catch(_:Exception){}
        }
    }
    private fun save(){
        val raw=tiles.joinToString("\n"){listOf(it.id,it.type,it.x,it.y,it.w,it.h,it.text.replace("\n","~n"),it.pkg).joinToString("\t")}
        prefs.edit().putString("tiles_$key",raw).putString("wall_$key",wallpaper).apply()
    }
    private fun render(){
        heading.text="DESKTOP STUDIO  /  $label";canvas.removeAllViews()
        var applied=false
        wallpaper?.let{uri->try{contentResolver.openInputStream(Uri.parse(uri))?.use{stream->
            val bmp=BitmapFactory.decodeStream(stream);if(bmp!=null){canvas.background=android.graphics.drawable.BitmapDrawable(resources,bmp);applied=true}
        }}catch(_:Exception){}}
        if(!applied)canvas.setBackgroundColor(0xff17649a.toInt())
        renderTiles()
    }
    private fun renderTiles(){
        if(!::canvas.isInitialized || canvas.width==0 || canvas.height==0)return
        canvas.removeAllViews()
        tiles.forEach{tile->
            val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(6),dp(4),dp(6),dp(4))
                background=android.graphics.drawable.GradientDrawable().apply{setColor(0xd91a2b3d.toInt());setStroke(dp(1),0xff68dfff.toInt());cornerRadius=dp(8).toFloat()}}
            when(tile.type){
                "clock"->panel.addView(TextClock(this).apply{format12Hour="h:mm";format24Hour="HH:mm";textSize=32f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.MONOSPACE},LinearLayout.LayoutParams(-1,-1))
                "date"->panel.addView(TextView(this).apply{text=SimpleDateFormat("yyyy.MM.dd EEE",Locale.getDefault()).format(Date());textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(-1,-1))
                "battery"->panel.addView(TextView(this).apply{tag="battery";text=batteryText();textSize=16f;gravity=Gravity.CENTER;setTextColor(0xff8eeaff.toInt())},LinearLayout.LayoutParams(-1,-1))
                "memo"->panel.addView(EditText(this).apply{setText(tile.text);hint="메모를 입력하세요";textSize=13f;gravity=Gravity.TOP;setTextColor(Color.WHITE);setHintTextColor(0xffb0c3d1.toInt());setBackgroundColor(Color.TRANSPARENT)
                    addTextChangedListener(object:TextWatcher{
                        override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                        override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){tile.text=s.toString();save()}
                        override fun afterTextChanged(s:Editable?){}
                    })},LinearLayout.LayoutParams(-1,-1))
                "app"->panel.addView(TextView(this).apply{text="▣\n"+tile.text;textSize=14f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(-1,-1))
            }
            panel.setOnClickListener{if(tile.type=="app")packageManager.getLaunchIntentForPackage(tile.pkg)?.let{startActivity(it)}}
            panel.setOnLongClickListener{tileMenu(tile);true}
            val lp=FrameLayout.LayoutParams(dp(tile.w),dp(tile.h)).apply{leftMargin=(tile.x*canvas.width).toInt().coerceIn(0,max(0,canvas.width-dp(tile.w)));topMargin=(tile.y*canvas.height).toInt().coerceIn(0,max(0,canvas.height-dp(tile.h)))}
            canvas.addView(panel,lp)
            var sx=0f;var sy=0f;var left=0;var top=0
            panel.setOnTouchListener{v,e->
                if(tile.type=="memo" && v.findFocus() is EditText)return@setOnTouchListener false
                when(e.action){
                    MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;val p=v.layoutParams as FrameLayout.LayoutParams;left=p.leftMargin;top=p.topMargin;true}
                    MotionEvent.ACTION_MOVE->{val p=v.layoutParams as FrameLayout.LayoutParams;p.leftMargin=(left+e.rawX-sx).toInt().coerceIn(0,max(0,canvas.width-v.width));p.topMargin=(top+e.rawY-sy).toInt().coerceIn(0,max(0,canvas.height-v.height));v.layoutParams=p;tile.x=p.leftMargin.toFloat()/canvas.width;tile.y=p.topMargin.toFloat()/canvas.height;true}
                    MotionEvent.ACTION_UP->{save();true}
                    else->true
                }
            }
        }
    }
    private fun tileMenu(tile:Tile){
        AlertDialog.Builder(this).setTitle("요소 설정").setItems(arrayOf("크게","작게","내용 수정","삭제")){_,i->
            when(i){
                0->{tile.w=(tile.w+30).coerceAtMost(330);tile.h=(tile.h+20).coerceAtMost(280)}
                1->{tile.w=(tile.w-25).coerceAtLeast(90);tile.h=(tile.h-18).coerceAtLeast(42)}
                2->{val edit=EditText(this).apply{setText(tile.text)}
                    AlertDialog.Builder(this).setTitle("텍스트 편집").setView(edit).setNegativeButton("취소",null)
                        .setPositiveButton("저장"){_,_->tile.text=edit.text.toString();save();renderTiles()}.show();return@setItems}
                3->tiles.remove(tile)
            };save();renderTiles()
        }.show()
    }
    private fun batteryText():String{
        val i=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=i?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=i?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100
        return if(level>=0&&scale>0)"BATTERY  ${level*100/scale}%" else "BATTERY  --"
    }
    private fun refreshBattery(){
        if(!::canvas.isInitialized)return
        fun visit(v:View){if(v.tag=="battery"&&v is TextView)v.text=batteryText();if(v is android.view.ViewGroup)for(i in 0 until v.childCount)visit(v.getChildAt(i))}
        visit(canvas)
    }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
