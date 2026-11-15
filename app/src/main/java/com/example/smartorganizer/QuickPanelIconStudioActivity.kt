package com.example.smartorganizer

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class QuickPanelIconStudioActivity : Activity() {
    private val icons = listOf("달","태양","비행기","손전등","Wi-Fi","블루투스","배터리","위치","방해금지")
    private val colors = listOf(Color.rgb(104,232,218), Color.rgb(255,174,74), Color.WHITE, Color.rgb(116,190,255))
    private var colorIndex = 0
    private var selected = 0
    private lateinit var preview: IconPreview
    private lateinit var colorLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(18),dp(18),dp(18)); setBackgroundColor(Color.rgb(7,17,29)) }
        root.addView(TextView(this).apply { text="퀵패널 아이콘 스튜디오"; textSize=24f; setTextColor(Color.WHITE); setTypeface(null,1) })
        root.addView(TextView(this).apply { text="아이콘을 고르고 색상을 바꿔 PNG 세트로 저장하세요."; textSize=13f; setTextColor(0xff9ab0c2.toInt()); setPadding(0,dp(8),0,dp(14)) })
        preview=IconPreview()
        root.addView(preview,LinearLayout.LayoutParams(-1,dp(190)))
        colorLabel=TextView(this).apply { setTextColor(Color.WHITE); textSize=14f; gravity=android.view.Gravity.CENTER; setPadding(0,dp(8),0,dp(8)) }
        root.addView(colorLabel)
        root.addView(Button(this).apply { text="아이콘 색상 변경"; setOnClickListener { colorIndex=(colorIndex+1)%colors.size; updatePreview() } })
        val grid=GridLayout(this).apply { columnCount=3 }
        icons.forEachIndexed { i,name ->
            grid.addView(Button(this).apply { text=name; setOnClickListener { selected=i; updatePreview() } },
                GridLayout.LayoutParams().apply { width=0; height=dp(54); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) })
        }
        root.addView(grid)
        root.addView(Button(this).apply { text="선택 아이콘 PNG 저장"; setOnClickListener { saveOne() } })
        root.addView(Button(this).apply { text="퀵패널 아이콘 전체 ZIP 만들기"; setOnClickListener { savePack() } })
        root.addView(TextView(this).apply { text="내보낸 PNG는 테마 자료로 사용할 수 있습니다. 삼성 시스템 퀵패널에 자동 적용되지는 않습니다."; setTextColor(0xff9ab0c2.toInt()); textSize=12f; setPadding(0,dp(12),0,0) })
        setContentView(ScrollView(this).apply { addView(root) })
        updatePreview()
    }

    private fun updatePreview() { colorLabel.text="선택: ${icons[selected]} · 색상 ${colorIndex+1}/${colors.size}"; preview.invalidate() }

    private inner class IconPreview: View(this@QuickPanelIconStudioActivity) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas) {
            super.onDraw(c)
            val tile=dp(112).toFloat()
            val left=(width-tile)/2f; val top=(height-tile)/2f
            paint.color=0xff202d43.toInt(); c.drawRoundRect(left,top,left+tile,top+tile,dp(16).toFloat(),dp(16).toFloat(),paint)
            drawGlyph(c,selected,colors[colorIndex],width/2f,height/2f,tile*.34f)
        }
    }

    private fun drawGlyph(c:Canvas,index:Int,color:Int,cx:Float,cy:Float,r:Float) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; style=Paint.Style.STROKE; strokeWidth=r*.12f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
        when(index) {
            0 -> { c.drawCircle(cx,cy,r*.72f,p); p.style=Paint.Style.FILL; p.color=0xff202d43.toInt(); c.drawCircle(cx+r*.32f,cy-r*.24f,r*.62f,p) }
            1 -> { c.drawCircle(cx,cy,r*.43f,p); for(i in 0..7){ val a=Math.PI*i/4; c.drawLine(cx+kotlin.math.cos(a).toFloat()*r*.65f,cy+kotlin.math.sin(a).toFloat()*r*.65f,cx+kotlin.math.cos(a).toFloat()*r*.95f,cy+kotlin.math.sin(a).toFloat()*r*.95f,p) } }
            2 -> { val path=Path().apply { moveTo(cx,cy-r); lineTo(cx-r*.18f,cy-r*.12f); lineTo(cx-r*.9f,cy+r*.18f); lineTo(cx-r*.86f,cy+r*.4f); lineTo(cx-r*.12f,cy+r*.25f); lineTo(cx-r*.18f,cy+r*.8f); lineTo(cx,cy+r*.95f); lineTo(cx+r*.18f,cy+r*.8f); lineTo(cx+r*.12f,cy+r*.25f); lineTo(cx+r*.86f,cy+r*.4f); lineTo(cx+r*.9f,cy+r*.18f); lineTo(cx+r*.18f,cy-r*.12f); close() }; p.style=Paint.Style.FILL; c.drawPath(path,p) }
            3 -> { c.drawRoundRect(cx-r*.35f,cy-r*.8f,cx+r*.35f,cy+r*.55f,r*.12f,r*.12f,p); c.drawLine(cx-r*.25f,cy+r*.72f,cx+r*.25f,cy+r*.72f,p) }
            4 -> { for(i in 1..3) c.drawArc(cx-r*i*.25f,cy-r*i*.25f,cx+r*i*.25f,cy+r*i*.25f,220f,100f,false,p); p.style=Paint.Style.FILL; c.drawCircle(cx,cy+r*.45f,r*.09f,p) }
            5 -> { val path=Path().apply { moveTo(cx-r*.25f,cy-r); lineTo(cx+r*.35f,cy-r*.25f); lineTo(cx-r*.25f,cy+r*.1f); lineTo(cx+r*.35f,cy+r*.75f); lineTo(cx-r*.25f,cy+r); moveTo(cx+r*.35f,cy-r*.25f); lineTo(cx-r*.25f,cy+r*.1f); lineTo(cx+r*.35f,cy+r*.75f) }; c.drawPath(path,p) }
            6 -> { c.drawRoundRect(cx-r*.65f,cy-r*.38f,cx+r*.65f,cy+r*.38f,r*.12f,r*.12f,p); c.drawRect(cx+r*.65f,cy-r*.15f,cx+r*.82f,cy+r*.15f,p); p.style=Paint.Style.FILL; c.drawRect(cx-r*.48f,cy-r*.2f,cx+r*.35f,cy+r*.2f,p) }
            7 -> { c.drawCircle(cx,cy-r*.15f,r*.48f,p); val path=Path().apply { moveTo(cx-r*.42f,cy+r*.08f); lineTo(cx,cy+r*.85f); lineTo(cx+r*.42f,cy+r*.08f) }; c.drawPath(path,p) }
            else -> { c.drawCircle(cx,cy,r*.75f,p); c.drawLine(cx-r*.55f,cy-r*.55f,cx+r*.55f,cy+r*.55f,p) }
        }
    }

    private fun png(index:Int):ByteArray {
        val size=192
        val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        drawGlyph(canvas,index,colors[colorIndex],size/2f,size/2f,size*.34f)
        val out=ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle()
        return out.toByteArray()
    }
    private fun saveOne() {
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="image/png"; putExtra(Intent.EXTRA_TITLE,"quickpanel_${selected}.png") }
        pending=png(selected); startActivityForResult(intent,501)
    }
    private var pending:ByteArray?=null
    private fun savePack() {
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/zip"; putExtra(Intent.EXTRA_TITLE,"QuickPanel-Icons.zip") }
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> icons.indices.forEach { i -> zip.putNextEntry(ZipEntry("quickpanel_${i}_${icons[i]}.png")); zip.write(png(i)); zip.closeEntry() } }
        pending=out.toByteArray(); startActivityForResult(intent,501)
    }
    @Deprecated("Legacy result API retained")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==501 && resultCode==RESULT_OK) {
            val bytes=pending ?: return
            try { contentResolver.openOutputStream(data?.data ?: return)?.use { it.write(bytes) }; Toast.makeText(this,"아이콘 파일을 저장했습니다.",Toast.LENGTH_LONG).show() }
            catch(e:Exception) { Toast.makeText(this,"저장 실패: ${e.localizedMessage}",Toast.LENGTH_LONG).show() }
        }
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
