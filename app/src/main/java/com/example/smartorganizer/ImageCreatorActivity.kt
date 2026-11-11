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
        val bg = 0xff07101b.toInt()
        val panel = 0xff101d2d.toInt()
        val cyan = 0xff72eee2.toInt()
        val muted = 0xff8395aa.toInt()
        fun rounded(color: Int, radius: Float, stroke: Int = 0): android.graphics.drawable.GradientDrawable =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
                if (stroke != 0) setStroke(dp(1), stroke)
            }
        fun styleButton(button: Button, primary: Boolean = false) {
            button.isAllCaps = false; button.textSize = 13f
            button.setTextColor(if (primary) bg else 0xffd7e6f1.toInt())
            button.background = rounded(if (primary) cyan else panel, 13f, if (primary) 0 else 0xff293d50.toInt())
            button.minHeight = dp(48)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "✦  SMART ORGANIZER"; textSize = 15f; setTextColor(cyan); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "FOLD7 WALLPAPER LAB"; textSize = 9f; setTextColor(muted)
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "나만의 배경을\n완성해 보세요"
            textSize = 27f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(18), 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "이미지를 고르고 분위기를 선택한 뒤, Fold 화면에 맞춰 저장합니다."
            textSize = 12f; setTextColor(muted)
        })
        val steps = TextView(this).apply {
            text = "① 이미지 선택     ─────     ② 효과 선택     ─────     ③ 저장"
            textSize = 10f; setTextColor(cyan); setPadding(0, dp(16), 0, dp(12))
        }
        root.addView(steps)

        val arena = LinearLayout(this).apply {
            orientation = if (resources.configuration.screenWidthDp >= 600) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val artColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        preview = EffectPreview().apply {
            background = rounded(0xff0c1c30.toInt(), 20f, 0xff29465a.toInt())
        }
        artColumn.addView(preview, LinearLayout.LayoutParams(-1, if (resources.configuration.screenWidthDp >= 600) -1 else dp(310), 1f))
        artColumn.addView(TextView(this).apply {
            text = "LIVE PREVIEW   ·   GALAXY Z FOLD7"
            textSize = 9f; setTextColor(muted); setPadding(dp(4), dp(10), 0, dp(8))
        })
        arena.addView(artColumn, LinearLayout.LayoutParams(0, -1, if (resources.configuration.screenWidthDp >= 600) 1.08f else 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (resources.configuration.screenWidthDp >= 600) dp(22) else 0, dp(8), 0, 0)
        }
        prompt = EditText(this).apply {
            hint = "예: 은하수 속 전사, 푸른 번개를 두른 용"
            setTextColor(Color.WHITE); setHintTextColor(muted); minLines = 2; textSize = 13f
            background = rounded(panel, 12f, 0xff293d50.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        controls.addView(TextView(this).apply { text = "01  ·  이미지"; textSize = 11f; setTextColor(cyan); setPadding(0, dp(4), 0, dp(7)) })
        val importRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val pick = Button(this).apply { text = "사진 불러오기" }
        styleButton(pick)
        pick.setOnClickListener { pickImage() }
        val ai = Button(this).apply { text = "AI 이미지 생성" }
        styleButton(ai, true); ai.setOnClickListener { openAiGenerator() }
        importRow.addView(pick, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(6) })
        importRow.addView(ai, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(6) })
        controls.addView(importRow)
        controls.addView(prompt, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(8) })
        controls.addView(TextView(this).apply { text = "02  ·  분위기 선택"; textSize = 11f; setTextColor(cyan); setPadding(0, dp(17), 0, dp(7)) })
        val effects = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf("에너지 오라" to Effect.AURA, "불꽃 파동" to Effect.FIRE, "푸른 번개" to Effect.LIGHTNING, "은하수" to Effect.GALAXY, "원본 이미지" to Effect.NONE).forEach { (label, value) ->
            val choice = Button(this).apply { text = "◇   $label"; textAlignment = View.TEXT_ALIGNMENT_VIEW_START }
            styleButton(choice)
            choice.setOnClickListener {
                effect = value; generated = null; preview.invalidate()
                status.text = "$label 효과를 선택했습니다. 저장 전에 미리보기를 확인하세요."
            }
            effects.addView(choice, LinearLayout.LayoutParams(-1, dp(43)).apply { bottomMargin = dp(5) })
        }
        controls.addView(effects)
        status = TextView(this).apply {
            text = "이미지를 선택하거나 AI 생성기를 열어 시작하세요."
            textSize = 11f; setTextColor(muted); setPadding(0, dp(8), 0, dp(8))
        }
        controls.addView(status)
        val save = Button(this).apply { text = "효과 적용 · PNG 저장"; styleButton(this, true); setOnClickListener { saveComposite() } }
        controls.addView(save, LinearLayout.LayoutParams(-1, dp(52)))
        val apply = Button(this).apply { text = "배경화면으로 적용"; styleButton(this); setOnClickListener { applyWallpaper() } }
        controls.addView(apply, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(7) })
        arena.addView(controls, LinearLayout.LayoutParams(0, -1, if (resources.configuration.screenWidthDp >= 600) .92f else 1f))
        root.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
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