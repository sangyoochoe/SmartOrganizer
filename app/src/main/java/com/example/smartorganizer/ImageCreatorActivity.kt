package com.example.smartorganizer

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*

class ImageCreatorActivity : Activity() {
    private lateinit var preview: EffectPreview
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private var source: Bitmap? = null
    private var effect = Effect.AURA
    private var generated: Bitmap? = null
    enum class Effect { AURA, FIRE, LIGHTNING, GALAXY, NONE }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            setBackgroundColor(Color.rgb(15, 19, 31))
        }
        root.addView(TextView(this).apply { text = "AI 배경화면 크리에이터"; textSize = 24f; setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply {
            text = "사진에 빛·오라·불꽃 효과를 합성하고 Fold7 배경으로 저장하세요."
            textSize = 13f; setTextColor(0xffc4cada.toInt())
        })
        prompt = EditText(this).apply {
            hint = "장면 묘사: 은하수 속 전사, 푸른 번개를 두른 용…"
            setTextColor(Color.WHITE); setHintTextColor(0xff9ca8bd.toInt()); minLines = 2
        }
        root.addView(prompt)
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER }
        top.addView(Button(this).apply { text = "사진 불러오기"; setOnClickListener { pickImage() } })
        top.addView(Button(this).apply { text = "AI 이미지 만들기"; setOnClickListener { openAiGenerator() } })
        root.addView(top)
        preview = EffectPreview()
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(10) })
        val effects = LinearLayout(this).apply { gravity = Gravity.CENTER }
        listOf("오라" to Effect.AURA, "불꽃" to Effect.FIRE, "번개" to Effect.LIGHTNING, "은하" to Effect.GALAXY, "원본" to Effect.NONE).forEach { (label, value) ->
            effects.addView(Button(this).apply { text = label; textSize = 11f; setOnClickListener { effect = value; generated = null; preview.invalidate() } },
                LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        root.addView(effects)
        status = TextView(this).apply { text = "사진을 선택하거나 AI 이미지 생성으로 이동하세요."; textSize = 12f; setTextColor(0xffc4cada.toInt()) }
        root.addView(status)
        root.addView(Button(this).apply { text = "효과 적용 · PNG로 저장"; setOnClickListener { saveComposite() } })
        root.addView(Button(this).apply { text = "현재 이미지 배경화면 적용"; setOnClickListener { applyWallpaper() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 8301)
    }

    @Deprecated("Legacy result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 8301 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        source = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        generated = null; preview.invalidate()
        status.text = if (source != null) "사진을 불러왔습니다. 효과를 선택해 보세요." else "이미지를 읽을 수 없습니다."
    }

    private fun openAiGenerator() {
        val description = prompt.text.toString().trim()
        if (description.isEmpty()) { prompt.error = "원하는 이미지 장면을 먼저 입력하세요."; return }
        val url = Uri.parse("https://www.bing.com/images/create?q=" + Uri.encode(description))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
            status.text = "웹 AI 이미지 생성기를 열었습니다. 생성한 이미지를 저장한 뒤 여기서 불러오세요."
        } catch (_: Exception) { status.text = "웹 브라우저를 열 수 없습니다." }
    }

    private fun compose(): Bitmap? {
        val input = source ?: return null
        val scale = min(1f, 1800f / max(input.width, input.height))
        val w = (input.width * scale).roundToInt().coerceAtLeast(1)
        val h = (input.height * scale).roundToInt().coerceAtLeast(1)
        val base = Bitmap.createScaledBitmap(input, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(base); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = w * .5f; val cy = h * .48f; val radius = max(w, h) * .48f
        when (effect) {
            Effect.AURA -> {
                paint.shader = RadialGradient(cx, cy, radius, intArrayOf(0x665ecbff, 0x334c5dff, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint); paint.shader = null
                paint.style = Paint.Style.STROKE; paint.strokeWidth = max(3f, w * .006f)
                for (i in 0..3) { paint.color = Color.argb(45 - i * 7, 105, 210, 255); canvas.drawCircle(cx, cy, radius * (.22f + i * .11f), paint) }
            }
            Effect.FIRE -> {
                paint.shader = LinearGradient(0f, h.toFloat(), w.toFloat(), h * .18f, intArrayOf(0x99ff3b08.toInt(), 0x55ff9b20, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint); paint.shader = null
                val random = java.util.Random(41)
                repeat(100) { val x = random.nextFloat() * w; val y = h * (.58f + random.nextFloat() * .4f)
                    paint.color = Color.argb(random.nextInt(100) + 70, 255, random.nextInt(150) + 80, 20)
                    canvas.drawCircle(x, y, random.nextFloat() * w * .012f + 2f, paint) }
            }
            Effect.LIGHTNING -> {
                val random = java.util.Random(17); paint.style = Paint.Style.STROKE; paint.strokeWidth = max(2f, w * .004f); paint.color = 0xff8ceaff.toInt()
                repeat(7) {
                    val path = Path(); var x = random.nextFloat() * w; var y = random.nextFloat() * h * .8f; path.moveTo(x, y)
                    repeat(7) { x += (random.nextFloat() - .5f) * w * .18f; y += random.nextFloat() * h * .12f
                        path.lineTo(x.coerceIn(0f, w.toFloat()), y.coerceIn(0f, h.toFloat())) }
                    canvas.drawPath(path, paint)
                }
            }
            Effect.GALAXY -> {
                paint.shader = RadialGradient(cx, cy, radius, intArrayOf(0x554f35b5, 0x333d83d8, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint); paint.shader = null
                val random = java.util.Random(2025)
                repeat(220) { paint.color = Color.argb(random.nextInt(180) + 60, 220, 230, 255)
                    canvas.drawCircle(random.nextFloat() * w, random.nextFloat() * h, random.nextFloat() * max(1f, w * .002f) + 1f, paint) }
            }
            Effect.NONE -> Unit
        }
        paint.shader = null; paint.style = Paint.Style.FILL
        return base
    }

    private fun saveComposite() {
        val bitmap = compose() ?: run { status.text = "먼저 사진을 불러오세요."; return }
        generated = bitmap
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SmartOrganizer_" + System.currentTimeMillis() + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartOrganizer")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("저장 위치를 만들 수 없습니다.")
            contentResolver.openOutputStream(uri)?.use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IllegalStateException("PNG 저장 실패") }
            status.text = "Pictures/SmartOrganizer에 PNG로 저장했습니다."; preview.invalidate()
        } catch (e: Exception) { status.text = "저장 실패: " + e.localizedMessage }
    }

    private fun applyWallpaper() {
        val bitmap = generated ?: compose() ?: run { status.text = "먼저 사진을 불러오고 효과를 적용하세요."; return }
        try { WallpaperManager.getInstance(this).setBitmap(bitmap); status.text = "홈 화면 배경화면 적용을 요청했습니다." }
        catch (e: Exception) { status.text = "배경 적용 실패: " + e.localizedMessage }
    }

    private inner class EffectPreview : View(this@ImageCreatorActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); canvas.drawColor(0xff20283a.toInt())
            val bitmap = generated ?: source
            if (bitmap != null) {
                val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                val dw = bitmap.width * scale; val dh = bitmap.height * scale
                val rect = RectF((width-dw)/2, (height-dh)/2, (width+dw)/2, (height+dh)/2)
                canvas.drawBitmap(bitmap, null, rect, paint)
                if (generated == null && effect != Effect.NONE) {
                    paint.color = when(effect) { Effect.FIRE -> 0x66ff6b18; Effect.LIGHTNING -> 0x6685dfff; Effect.GALAXY -> 0x665b4bcc; else -> 0x6653aaff }.toInt()
                    canvas.drawRect(rect, paint); paint.color = Color.WHITE
                }
            } else { paint.color = Color.WHITE; paint.textSize = dp(15).toFloat(); canvas.drawText("선택한 이미지 미리보기", dp(20).toFloat(), height / 2f, paint) }
        }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}