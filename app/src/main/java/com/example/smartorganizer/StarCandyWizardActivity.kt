package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StarCandyWizardActivity : Activity() {
    private val bg = Color.rgb(7,17,29)
    private val card = Color.rgb(18,36,53)
    private val mint = Color.rgb(104,232,218)
    private val muted = Color.rgb(137,160,178)
    private var step = 0
    private var device = "폴드7"
    private var theme = "드래곤볼"
    private var screen = "내부 화면"
    private var photo: Uri? = null
    private var target = WallpaperManager.FLAG_SYSTEM
    private var pendingZip: java.io.File? = null
    private lateinit var page: LinearLayout
    private val prefs by lazy { getSharedPreferences("star_candy_theme", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = prefs.getString("device", "폴드7") ?: "폴드7"
        theme = prefs.getString("theme", "드래곤볼") ?: "드래곤볼"
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(22),dp(18),dp(22),dp(20)) }
        root.addView(label("✦  별사탕 Wallpaper",22,Color.WHITE,true))
        root.addView(label("LIVE WALLPAPER COLLECTION     " + (step+1) + " / 3",11,mint,true).apply { setPadding(0,dp(16),0,dp(18)) })
        val scroll = ScrollView(this)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(page)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        when(step) { 0 -> devicePage(); 1 -> themePage(); else -> applyPage() }
    }

    private fun label(s:String,size:Int,color:Int,bold:Boolean) = TextView(this).apply {
        text=s; textSize=size.toFloat(); setTextColor(color)
        if(bold) setTypeface(null,android.graphics.Typeface.BOLD)
    }
    private fun heading(k:String,t:String,d:String) {
        page.addView(label(k,12,mint,true))
        page.addView(label(t,29,Color.WHITE,true).apply { setPadding(0,dp(10),0,dp(8)) })
        page.addView(label(d,14,muted,false).apply { setPadding(0,0,0,dp(22)) })
    }
    private fun option(t:String,d:String,selected:Boolean=false,action:()->Unit) {
        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18))
            setBackgroundColor(if(selected) Color.rgb(23,62,68) else card); setOnClickListener { action() }
        }
        box.addView(label(t+(if(selected) "   ✓" else "   →"),20,if(selected) mint else Color.WHITE,true))
        box.addView(label(d,12,muted,false).apply { setPadding(0,dp(7),0,0) })
        page.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun primary(t:String,action:()->Unit) {
        page.addView(Button(this).apply { text=t; setTextColor(Color.rgb(3,35,43)); setBackgroundColor(mint); textSize=16f; setOnClickListener { action() } },
            LinearLayout.LayoutParams(-1,dp(56)).apply { topMargin=dp(14) })
    }
    private fun back(action:()->Unit) { page.addView(Button(this).apply { text="← 이전"; setOnClickListener { action() } }) }

    private fun devicePage() {
        heading("01 / SELECT DEVICE","기종을 선택하세요","나의 휴대폰에 어울리는 화면 구성을 선택해 주세요.")
        option("폴드7","GALAXY Z FOLD7 · 커버 / 내부 화면",device=="폴드7") { device="폴드7"; render() }
        option("폴드8 울트라","기기 프로필",device=="폴드8 울트라") { device="폴드8 울트라"; render() }
        option("폴드8","기기 프로필",device=="폴드8") { device="폴드8"; render() }
        primary("선택완료     NEXT   →") { prefs.edit().putString("device",device).apply(); step=1; render() }
    }
    private fun themePage() {
        heading("02 / SELECT THEME","테마를 선택하세요","좋아하는 장면을 매일 만나는 배경으로.")
        listOf("헬로키티","드래곤볼","별사탕 샘플").forEach { item ->
            option(item,"테마 미리보기 · 사용자 사진을 연결해 적용",theme==item) { theme=item; render() }
        }
        primary("선택완료     NEXT   →") { prefs.edit().putString("theme",theme).apply(); step=2; render() }
        back { step=0; render() }
    }
    private fun applyPage() {
        heading("03 / MAKE IT YOURS","어디에 적용할까요?","원하는 화면에 적용하거나 테마 자료를 보관하세요.")
        option("내부 화면","넓게 펼쳐지는 잠금화면과 홈화면",screen=="내부 화면") { screen="내부 화면"; render() }
        option("외부 화면","접었을 때 만나는 나만의 작은 화면",screen=="외부 화면") { screen="외부 화면"; render() }
        option("잠금화면","화면을 켤 때 처음 만나는 장면",target==WallpaperManager.FLAG_LOCK) { target=WallpaperManager.FLAG_LOCK; render() }
        option("홈화면","매일 쓰는 화면에 배경 적용",target==WallpaperManager.FLAG_SYSTEM) { target=WallpaperManager.FLAG_SYSTEM; render() }
        option("충전 배경화면","충전 연결 시 사용할 영상·이미지",false) { startActivity(Intent(this,WallpaperStudioActivity::class.java)) }
        option("아이콘","테마 아이콘을 커스텀 런처에 배치",false) { startActivity(Intent(this,LauncherActivity::class.java)) }
        option("퀵패널 아이콘 제작","달·태양·비행기·손전등 등 아이콘을 직접 그려 PNG로 저장",false) { startActivity(Intent(this,QuickPanelIconStudioActivity::class.java)) }
        option("원본 사진 관리","사진을 고르고 이 기기에 적용",false) { pickPhoto() }
        option("자료 풀세트 다운로드","선택한 사진을 ZIP으로 저장",false) { exportZip() }
        option("홈·잠금 배경 초기화","삼성 기본 배경 복원은 설정에서 직접 진행",false) {
            AlertDialog.Builder(this).setMessage("기본 배경 복원은 삼성 설정에서 직접 진행해야 합니다.")
                .setPositiveButton("확인",null).show()
        }
        primary("선택한 화면에 사진 적용") { if(photo==null) pickPhoto() else applySelected() }
        back { step=1; render() }
    }
    private fun pickPhoto() {
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        startActivityForResult(i,901)
    }
    @Deprecated("Legacy result API retained")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK) return
        if(requestCode==901) {
            photo=data?.data
            photo?.let { try { contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {} }
            Toast.makeText(this,"사진을 선택했어요. 적용 버튼을 눌러주세요.",Toast.LENGTH_SHORT).show()
        } else if(requestCode==902) saveZipResult(data?.data)
    }
    private fun applySelected() {
        val uri=photo ?: return
        try {
            contentResolver.openInputStream(uri).use { input ->
                if(input==null) error("사진을 열 수 없습니다.")
                WallpaperManager.getInstance(this).setStream(input,null,true,target)
            }
            prefs.edit().putString(if(screen=="외부 화면") "cover_photo" else "inner_photo",uri.toString()).putString("last_apply_target",if(target==WallpaperManager.FLAG_LOCK) "lock" else "home").apply()
            Toast.makeText(this,"배경 적용 요청을 보냈습니다. 기기에서 확인하세요.",Toast.LENGTH_LONG).show()
        } catch(e:Exception) { Toast.makeText(this,"적용 실패: "+(e.localizedMessage ?: ""),Toast.LENGTH_LONG).show() }
    }
    private fun exportZip() {
        val uri=photo ?: prefs.getString(if(screen=="외부 화면") "cover_photo" else "inner_photo",null)?.let { Uri.parse(it) }
        if(uri==null) { pickPhoto(); return }
        try {
            val file=java.io.File(cacheDir,"StarCandy-"+device+"-"+theme+".zip")
            contentResolver.openInputStream(uri).use { input ->
                if(input==null) error("사진을 열 수 없습니다.")
                ZipOutputStream(file.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("wallpaper.jpg")); input.copyTo(zip); zip.closeEntry()
                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write(("기종: "+device+"\n테마: "+theme+"\n사용자 선택 사진\n").toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
            }
            pendingZip=file
            val save=Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/zip"; putExtra(Intent.EXTRA_TITLE,file.name) }
            startActivityForResult(save,902)
        } catch(e:Exception) { Toast.makeText(this,"ZIP 생성 실패: "+(e.localizedMessage ?: ""),Toast.LENGTH_LONG).show() }
    }
    private fun saveZipResult(uri:Uri?) {
        val file=pendingZip ?: return
        if(uri==null) return
        try { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }; Toast.makeText(this,"사진 자료 ZIP을 저장했습니다.",Toast.LENGTH_LONG).show() }
        catch(e:Exception) { Toast.makeText(this,"저장 실패: "+(e.localizedMessage ?: ""),Toast.LENGTH_LONG).show() }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
