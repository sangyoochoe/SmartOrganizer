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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

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
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }
    override fun onPause() {
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        save(); monitor.stop(); super.onPause()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun clampTile(tile:Tile){
        val maxX=(canvas.width-dp(tile.w)).coerceAtLeast(0)
        val maxY=(canvas.height-dp(tile.h)).coerceAtLeast(0)
        val px=(tile.x*canvas.width).toInt().coerceIn(0,maxX)
        val py=(tile.y*canvas.height).toInt().coerceIn(0,maxY)
        tile.x=if(canvas.width>0)px.toFloat()/canvas.width else 0f
        tile.y=if(canvas.height>0)py.toFloat()/canvas.height else 0f
    }

    private fun build() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(0xff0a1523.toInt()) }
        val bar = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(5),dp(4),dp(5),dp(4)); setBackgroundColor(0xff142438.toInt()) }
        heading = TextView(this).apply { textSize=12f; setTextColor(0xff83eaff.toInt()); typeface=Typeface.DEFAULT_BOLD }
        bar.addView(heading,LinearLayout.LayoutParams(0,dp(44),1f))
        bar.addView(button("＋ 위젯"){ addMenu() })
        bar.addView(button("앱 위젯"){ startActivity(Intent(this, AndroidWidgetHostActivity::class.java)) })
        bar.addView(button("배경"){ pickWallpaper() })
        bar.addView(button("AI 분석"){ pickAndAnalyze() })
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
        AlertDialog.Builder(this).setTitle("Smart Desktop").setItems(arrayOf("위젯 추가","앱 바로가기 추가","배경 변경","프리셋 적용","레이아웃 내보내기","레이아웃 가져오기","화면 초기화")){_,i->
            when(i){0->addMenu();1->pickApp();2->pickWallpaper();3->presetMenu();4->exportLayout();5->importLayout();6->
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
    private fun presetMenu(){
        val names=arrayOf("Windows 데스크톱","Cyber Formula HUD","미니멀 시계")
        AlertDialog.Builder(this).setTitle("현재 화면 프리셋").setItems(names){_,i->
            tiles.clear()
            when(i){
                0->{tiles.add(Tile(1,"clock",.06f,.06f,190,76));tiles.add(Tile(2,"date",.06f,.22f,190,48));tiles.add(Tile(3,"memo",.62f,.08f,220,150,"오늘 할 일을 입력하세요"))}
                1->{tiles.add(Tile(11,"clock",.34f,.08f,230,90));tiles.add(Tile(12,"date",.35f,.25f,220,48));tiles.add(Tile(13,"battery",.72f,.06f,160,55));tiles.add(Tile(14,"memo",.06f,.62f,230,120,"ZERO BOOST SYSTEM"))}
                2->{tiles.add(Tile(21,"clock",.25f,.25f,240,100));tiles.add(Tile(22,"date",.29f,.43f,200,48))}
            }
            save();renderTiles();toast("${names[i]} 프리셋을 적용했습니다.")
        }.show()
    }
    private fun exportLayout(){
        save()
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
            putExtra(Intent.EXTRA_TITLE,"smart-desktop-$key.sod.json")
        },7303)
    }
    private fun importLayout(){
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);type="application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },7304)
    }
    private fun layoutJson():String{
        val root=JSONObject().put("format","SMART_ORGANIZER_DESKTOP_V1").put("posture",key).put("wallpaper",wallpaper)
        val rows=JSONArray()
        tiles.forEach{t->rows.put(JSONObject().put("id",t.id).put("type",t.type).put("x",t.x).put("y",t.y).put("w",t.w).put("h",t.h).put("text",t.text).put("pkg",t.pkg))}
        return root.put("tiles",rows).toString(2)
    }
    private fun writeLayout(uri:Uri){
        try{
            val stream=contentResolver.openOutputStream(uri)?:throw IllegalStateException("파일을 열 수 없습니다.")
            stream.bufferedWriter(Charsets.UTF_8).use{it.write(layoutJson())}
            toast("레이아웃 파일을 내보냈습니다.")
        }catch(e:Exception){toast("내보내기 실패: ${e.localizedMessage}")}
    }
    private fun readLayout(uri:Uri){
        try{
            val raw=contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{reader->
                val text=reader.readText()
                require(text.length<=2_000_000){"레이아웃 파일이 너무 큽니다."}
                text
            }?:throw IllegalStateException("파일을 읽을 수 없습니다.")
            val root=JSONObject(raw)
            require(root.optString("format")=="SMART_ORGANIZER_DESKTOP_V1"){"지원하지 않는 레이아웃 파일입니다."}
            val restored=mutableListOf<Tile>();val rows=root.getJSONArray("tiles")
            require(rows.length()<=100){"위젯은 최대 100개까지 복원할 수 있습니다."}
            for(i in 0 until rows.length()){
                val o=rows.getJSONObject(i);val type=o.getString("type")
                require(type in setOf("clock","date","battery","memo","app","detected")){"알 수 없는 위젯 형식"}
                restored.add(Tile(o.optLong("id",System.currentTimeMillis()+i),type,
                    o.optDouble("x",.05).toFloat().coerceIn(0f,1f),o.optDouble("y",.05).toFloat().coerceIn(0f,1f),
                    o.optInt("w",150).coerceIn(60,500),o.optInt("h",70).coerceIn(36,400),o.optString("text"),o.optString("pkg")))
            }
            val importedWallpaper=root.optString("wallpaper").takeIf{it.isNotBlank()&&it!="null"}
            val wallpaperAvailable=try{
                importedWallpaper?.let{contentResolver.openInputStream(Uri.parse(it))?.use{stream->stream.read()>=0}}?:false
            }catch(_:Exception){false}
            tiles.clear();tiles.addAll(restored)
            if(wallpaperAvailable)wallpaper=importedWallpaper
            save();render()
            toast(if(importedWallpaper!=null&&!wallpaperAvailable)
                "위젯 ${tiles.size}개 복원 완료. 배경은 원본 기기 전용 경로라 다시 선택해야 합니다."
                else "레이아웃 ${tiles.size}개 요소를 가져왔습니다.")
        }catch(e:Exception){toast("가져오기 실패: ${e.localizedMessage}")}
    }
    private fun pickApp(){
        val apps=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .map{it.loadLabel(packageManager).toString() to it.activityInfo.packageName}.distinctBy{it.second}.sortedBy{it.first.lowercase()}
        AlertDialog.Builder(this).setTitle("앱 바로가기 선택").setItems(apps.map{it.first}.toTypedArray()){_,i->add("app",apps[i].first,apps[i].second)}.show()
    }
    private fun pickAndAnalyze(){
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            type="image/*";addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        },7302)
    }
    private fun analyzeImage(uri:Uri){
        toast("사진의 객체와 위치를 분석하고 있습니다.")
        val image=try{InputImage.fromFilePath(this,uri)}catch(e:Exception){toast("이미지를 열 수 없습니다.");return}
        val options=ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects().enableClassification().build()
        val detector=ObjectDetection.getClient(options)
        detector.process(image).addOnSuccessListener{objects->
            wallpaper=uri.toString()
            val iw=image.width.coerceAtLeast(1);val ih=image.height.coerceAtLeast(1)
            val found=objects.take(24).mapNotNull{obj->
                val box=obj.boundingBox
                val label=obj.labels.maxByOrNull{it.confidence}?.text ?: "감지 객체"
                val x=(box.left.toFloat()/iw).coerceIn(0f,.92f)
                val y=(box.top.toFloat()/ih).coerceIn(0f,.92f)
                val w=(box.width().toFloat()/iw*canvas.width/resources.displayMetrics.density).toInt().coerceIn(90,260)
                val h=(box.height().toFloat()/ih*canvas.height/resources.displayMetrics.density).toInt().coerceIn(46,160)
                Tile(System.currentTimeMillis()+box.left+box.top,"detected",x,y,w,h,label,"")
            }
            tiles.removeAll{it.type=="detected"}
            tiles.addAll(found)
            prefs.edit().putString("wall_$key",wallpaper).apply()
            save();render()
            toast("분석 완료: 객체 ${found.size}개를 편집 가능한 위치 태그로 배치했습니다.")
            detector.close()
        }.addOnFailureListener{error->
            detector.close()
            toast("사진 분석 실패: ${error.localizedMessage}")
        }
    }
    private fun pickWallpaper(){
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)},7301)
    }
    @Deprecated("Legacy result API retained")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==7303 && resultCode==RESULT_OK){ data?.data?.let{writeLayout(it)}; return }
        if(requestCode==7304 && resultCode==RESULT_OK){ data?.data?.let{readLayout(it)}; return }
        if((requestCode==7301 || requestCode==7302) && resultCode==RESULT_OK){
            val uri=data?.data?:return
            try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
            if(requestCode==7302){ analyzeImage(uri); return }
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
        wallpaper?.let{raw->
            try{
                val uri=Uri.parse(raw)
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                if(bounds.outWidth>0 && bounds.outHeight>0){
                    val targetW=canvas.width.coerceAtLeast(resources.displayMetrics.widthPixels)
                    val targetH=canvas.height.coerceAtLeast(resources.displayMetrics.heightPixels)
                    var sample=1
                    while(bounds.outWidth/sample>targetW*2 || bounds.outHeight/sample>targetH*2)sample*=2
                    val options=BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=android.graphics.Bitmap.Config.RGB_565}
                    val bitmap=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}
                    if(bitmap!=null){
                        canvas.background=android.graphics.drawable.BitmapDrawable(resources,bitmap).apply{gravity=Gravity.FILL}
                        applied=true
                    }
                }
            }catch(_:Exception){toast("배경 이미지를 불러오지 못했습니다. 파일 권한 또는 백업 기기 경로를 확인하세요.")}
        }
        if(!applied)canvas.setBackgroundColor(0xff17649a.toInt())
        renderTiles()
    }
    private fun renderTiles(){
        if(!::canvas.isInitialized || canvas.width==0 || canvas.height==0)return
        canvas.removeAllViews()
        tiles.forEach{tile->
            tile.w=tile.w.coerceIn(60,500);tile.h=tile.h.coerceIn(36,400)
            clampTile(tile)
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
                "detected"->panel.addView(TextView(this).apply{text="⌖  "+tile.text;textSize=12f;gravity=Gravity.CENTER;setTextColor(0xffa6f5ff.toInt());typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(-1,-1))
            }
            panel.setOnClickListener{if(tile.type=="app")packageManager.getLaunchIntentForPackage(tile.pkg)?.let{startActivity(it)}}
            panel.setOnLongClickListener{tileMenu(tile);true}
            val lp=FrameLayout.LayoutParams(dp(tile.w),dp(tile.h)).apply{leftMargin=(tile.x*canvas.width).toInt().coerceIn(0,max(0,canvas.width-dp(tile.w)));topMargin=(tile.y*canvas.height).toInt().coerceIn(0,max(0,canvas.height-dp(tile.h)))}
            canvas.addView(panel,lp)
            var sx=0f;var sy=0f;var left=0;var top=0;var moved=false;var longPressed=false
            val gestures=GestureDetector(this,object:GestureDetector.SimpleOnGestureListener(){
                override fun onDown(e:MotionEvent):Boolean=true
                override fun onLongPress(e:MotionEvent){longPressed=true;tileMenu(tile)}
            })
            panel.setOnTouchListener{v,e->
                if(tile.type=="memo" && v.findFocus() is EditText)return@setOnTouchListener false
                gestures.onTouchEvent(e)
                when(e.action){
                    MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;moved=false;longPressed=false;val p=v.layoutParams as FrameLayout.LayoutParams;left=p.leftMargin;top=p.topMargin;true}
                    MotionEvent.ACTION_MOVE->{if(kotlin.math.abs(e.rawX-sx)>dp(5)||kotlin.math.abs(e.rawY-sy)>dp(5))moved=true
                        val p=v.layoutParams as FrameLayout.LayoutParams;p.leftMargin=(left+e.rawX-sx).toInt().coerceIn(0,max(0,canvas.width-v.width));p.topMargin=(top+e.rawY-sy).toInt().coerceIn(0,max(0,canvas.height-v.height));v.layoutParams=p;tile.x=p.leftMargin.toFloat()/canvas.width;tile.y=p.topMargin.toFloat()/canvas.height;true}
                    MotionEvent.ACTION_UP->{save();if(!moved&&!longPressed&&tile.type=="app")packageManager.getLaunchIntentForPackage(tile.pkg)?.let{startActivity(it)};true}
                    else->true
                }
            }
        }
    }
    private fun tileMenu(tile:Tile){
        AlertDialog.Builder(this).setTitle("요소 설정").setItems(arrayOf("크게","작게","내용 수정","삭제")){_,i->
            when(i){
                0->{tile.w=(tile.w+30).coerceAtMost(500);tile.h=(tile.h+20).coerceAtMost(400)}
                1->{tile.w=(tile.w-25).coerceAtLeast(60);tile.h=(tile.h-18).coerceAtLeast(36)}
                2->{val edit=EditText(this).apply{setText(tile.text)}
                    AlertDialog.Builder(this).setTitle("텍스트 편집").setView(edit).setNegativeButton("취소",null)
                        .setPositiveButton("저장"){_,_->tile.text=edit.text.toString();save();renderTiles()}.show();return@setItems}
                3->tiles.remove(tile)
            };clampTile(tile);save();renderTiles()
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
