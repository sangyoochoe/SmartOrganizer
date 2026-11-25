package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.os.Environment
import android.provider.Settings
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import android.view.View
import androidx.documentfile.provider.DocumentFile
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.core.util.Consumer
import java.io.File
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

private data class Entry(
    val name: String,
    val relativePath: String,
    val size: Long,
    val modified: Long,
    val uri: Uri,
    val directory: Boolean
)


private class EbookActionIconDrawable(
    private val kind: String,
    private val tint: Int
) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.55f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val path = android.graphics.Path()
    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.scale(b.width() / 24f, b.height() / 24f)
        val r = android.graphics.RectF(3f, 2.5f, 21f, 21.5f)
        when (kind) {
            "storage" -> { canvas.drawRoundRect(r, 2f, 2f, paint); canvas.drawLine(3f, 8f, 21f, 8f, paint); canvas.drawCircle(17.5f, 5.3f, .7f, paint); canvas.drawCircle(19.5f, 5.3f, .7f, paint) }
            "media" -> { canvas.drawRoundRect(r, 3f, 3f, paint); canvas.drawCircle(12f, 12f, 4f, paint); canvas.drawPath(android.graphics.Path().apply { moveTo(10f, 9f); lineTo(16f, 12f); lineTo(10f, 15f); close() }, paint) }
            "folder", "download", "manage" -> { canvas.drawPath(android.graphics.Path().apply { moveTo(2.5f, 7f); lineTo(9f, 7f); lineTo(11f, 9f); lineTo(21.5f, 9f); lineTo(21.5f, 19f); lineTo(2.5f, 19f); close() }, paint); if (kind == "download") { canvas.drawLine(12f, 3f, 12f, 13f, paint); canvas.drawLine(8.5f, 10f, 12f, 13.5f, paint); canvas.drawLine(15.5f, 10f, 12f, 13.5f, paint) } }
            "scan", "review", "duplicate", "audit" -> { canvas.drawCircle(10f, 10f, 6f, paint); canvas.drawLine(14.5f, 14.5f, 21f, 21f, paint); if (kind == "duplicate") { canvas.drawCircle(10f, 10f, 2.5f, paint) } }
            "add" -> { canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawLine(12f, 7f, 12f, 17f, paint); canvas.drawLine(7f, 12f, 17f, 12f, paint) }
            "delete" -> { canvas.drawPath(android.graphics.Path().apply { moveTo(5f, 7f); lineTo(19f, 7f); moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f); moveTo(7f, 7f); lineTo(8f, 20f); lineTo(16f, 20f); lineTo(17f, 7f); moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f) }, paint) }
            "classify", "apps", "widgets" -> { for (x in 4..16 step 6) for (y in 4..16 step 6) canvas.drawRoundRect(x.toFloat(), y.toFloat(), x + 4f, y + 4f, 1f, 1f, paint) }
            "apk" -> { canvas.drawPath(android.graphics.Path().apply { moveTo(12f, 2.5f); lineTo(20f, 6f); lineTo(19f, 16f); lineTo(12f, 21.5f); lineTo(5f, 16f); lineTo(4f, 6f); close() }, paint); canvas.drawLine(8f, 12f, 11f, 15f, paint); canvas.drawLine(11f, 15f, 16f, 9f, paint) }
            "archive", "versions" -> { canvas.drawRoundRect(r, 2f, 2f, paint); canvas.drawLine(3f, 7f, 21f, 7f, paint); canvas.drawLine(9f, 11f, 15f, 11f, paint); canvas.drawLine(9f, 15f, 15f, 15f, paint) }
            "history" -> { canvas.drawCircle(12f, 12f, 9f, paint); canvas.drawLine(12f, 6f, 12f, 12f, paint); canvas.drawLine(12f, 12f, 16f, 14f, paint) }
            "theme" -> { canvas.drawPath(android.graphics.Path().apply { moveTo(19f, 16f); cubicTo(10f, 20f, 5f, 11f, 12f, 4f); cubicTo(12f, 13f, 16f, 16f, 19f, 16f); close() }, paint) }
            "dashboard" -> { canvas.drawRoundRect(r, 2f, 2f, paint); canvas.drawLine(3f, 9f, 21f, 9f, paint); canvas.drawLine(9f, 9f, 9f, 21f, paint); canvas.drawLine(9f, 15f, 21f, 15f, paint) }
            else -> { canvas.drawRoundRect(r, 3f, 3f, paint); canvas.drawLine(7f, 12f, 17f, 12f, paint); canvas.drawLine(12f, 7f, 12f, 17f, paint) }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = 30
    override fun getIntrinsicHeight(): Int = 30
}

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val entries = mutableListOf<Entry>()
    private val selectedTrees = linkedMapOf<String, Uri>()
    private val selectedLooseFiles = linkedMapOf<String, Uri>()
    private val checkedEntries = linkedSetOf<String>()
    private lateinit var summary: TextView
    private lateinit var storageSummary: TextView
    private lateinit var results: LinearLayout
    private lateinit var foldStatus: TextView
    private var windowInfoAdapter: WindowInfoTrackerCallbackAdapter? = null
    private val foldLayoutListener = Consumer<WindowLayoutInfo> { info -> updateFoldLayoutStatus(info) }
    private lateinit var scanButton: Button
    private lateinit var runScanButton: Button
    private lateinit var mediaScanButton: Button
    private lateinit var fullStorageScanButton: Button
    private lateinit var manageFoldersButton: Button
    private lateinit var addFilesButton: Button
    private lateinit var bulkDeleteButton: Button
    private lateinit var duplicateButton: Button
    private lateinit var insightButton: Button
    private lateinit var classifyButton: Button
    private lateinit var contentAuditButton: Button
    private lateinit var apkButton: Button
    private lateinit var appsButton: Button
    private lateinit var archiveButton: Button
    private lateinit var versionManagerButton: Button
    private lateinit var historyButton: Button
    private lateinit var exportListButton: Button
    private lateinit var launcherButton: Button
    private lateinit var wallpaperButton: Button
    private var pendingMoveEntry: Entry? = null
    private var pendingBatchMoveEntries: List<Entry> = emptyList()
    private var pendingBatchMoveFolderName: String? = null
    private var pendingWholeProjectRoot: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSelectedTrees()
        restoreLooseFiles()
        buildUi()
        updateFolderSelectionUi()
        registerFoldLayoutListener()
    }

    private fun registerFoldLayoutListener() {
        try {
            val adapter = WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(this))
            windowInfoAdapter = adapter
            adapter.addWindowLayoutInfoListener(this, mainExecutor, foldLayoutListener)
        } catch (_: Exception) {
            foldStatus.text = "화면 크기에 맞춰 자동 배치 중"
        }
    }

    private fun updateFoldLayoutStatus(info: WindowLayoutInfo) {
        val folding = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        foldStatus.text = when {
            folding == null -> "화면 모드 · 일반 화면 / 커버 화면에 맞춰 자동 배치"
            folding.state == FoldingFeature.State.HALF_OPENED -> "화면 모드 · 플렉스 모드 감지 · 원본 파일 보호 유지"
            folding.isSeparating -> "화면 모드 · 접힘 경계 감지 · 좌우 영역을 구분해 표시"
            else -> "화면 모드 · 펼친 화면 감지 · 넓은 화면 배치"
        }
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        val widthDp = resources.configuration.screenWidthDp
        val landscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val columns = when {
            widthDp >= 840 || (landscape && widthDp >= 700) -> 3
            widthDp >= 600 || landscape -> 2
            else -> 1
        }
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 14, 14, 18)
            setBackgroundColor(0xFFF4F0E6.toInt())
        }
        // Galaxy Z Fold inner display is typically wider than 600dp; use a true
        // side-by-side workspace there, while keeping the cover display stacked.
        val twoPane = widthDp >= 600 || (landscape && widthDp >= 600)
        val menuColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 10, 12)
        }
        val title = TextView(this).apply {
            text = "▤  스마트 정리함"; textSize = 27f
            setTextColor(0xFF28271F.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 5)
        }
        val subtitle = TextView(this).apply {
            text = "파일과 앱을 차분하게 정리하는 공간"; textSize = 13f
            setTextColor(0xFF777365.toInt())
        }
        foldStatus = TextView(this).apply {
            text = if (widthDp >= 600) "화면 모드 · 넓은 화면 배치" else "화면 모드 · 커버 화면 배치"
            textSize = 12f
            setTextColor(0xFF65734B.toInt())
            setPadding(12, 9, 12, 9)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFE9E3D5.toInt())
                cornerRadius = 7f
            }
        }
        summary = TextView(this).apply {
            text = "검사 전 · 내부 저장공간 전체 또는 원하는 폴더를 검사하세요."
            textSize = 14f; setTextColor(0xFF302F27.toInt()); setPadding(16, 15, 16, 15)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFF0EBDD.toInt()); cornerRadius = 10f }
        }
        storageSummary = TextView(this).apply {
            textSize = 13f; setTextColor(0xFF615F53.toInt()); setPadding(16, 12, 16, 12)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFE9E3D5.toInt()); cornerRadius = 10f }
        }
        scanButton = Button(this).apply { text = "＋ 스캔할 폴더 추가"; setOnClickListener { chooseFolder() }; setOnLongClickListener { showSelectedFolders(); true } }
        val downloadFolderButton = Button(this).apply { text = "⬇ Download 하위 폴더 선택"; setOnClickListener { chooseDownloadFolder() } }
        fullStorageScanButton = Button(this).apply { text = "내부 저장공간 전체 검사"; setOnClickListener { chooseFullStorageRoot() } }
        runScanButton = Button(this).apply { text = "선택한 폴더 모두 스캔 (0개)"; isEnabled = false; setOnClickListener { scanSelectedFolders() } }
        mediaScanButton = Button(this).apply { text = "사진 · 동영상 · 음악 검사"; setOnClickListener { requestMediaAccessAndScan() } }
        addFilesButton = Button(this).apply { text = "＋ 개별 파일 여러 개 추가"; setOnClickListener { chooseFiles() } }
        bulkDeleteButton = Button(this).apply { text = "선택 파일 일괄 삭제"; isEnabled = false; setOnClickListener { confirmBulkDelete() } }
        manageFoldersButton = Button(this).apply { text = "선택 폴더 관리"; setOnClickListener { showSelectedFolders() } }
        duplicateButton = Button(this).apply { text = "완전 중복 검사 · SHA-256"; isEnabled = false; setOnClickListener { scanExactDuplicates() } }
        insightButton = Button(this).apply { text = "정리 후보 분석"; isEnabled = false; setOnClickListener { showOrganizationInsights() } }
        classifyButton = Button(this).apply { text = "파일 유형 분류 · 이동 추천"; isEnabled = false; setOnClickListener { showAutoFileClassification() } }
        contentAuditButton = Button(this).apply { text = "파일 내용 시그니처 검사"; isEnabled = false; setOnClickListener { auditFileSignatures() } }
        apkButton = Button(this).apply { text = "APK 권한 · 버전 검사"; isEnabled = false; setOnClickListener { inspectApks() } }
        archiveButton = Button(this).apply { text = "ZIP 내부 구성 검사"; isEnabled = false; setOnClickListener { inspectArchives() } }
        historyButton = Button(this).apply { text = "파일 작업 기록"; setOnClickListener { showOperationHistory() } }
        versionManagerButton = Button(this).apply {
            text = "파일 버전 · 프로젝트 관리"
            isEnabled = false
            setOnClickListener { showVersionManager() }
        }
        appsButton = Button(this).apply { text = "앱 분류 · 그룹 관리"; setOnClickListener { showLaunchableApps() } }
        wallpaperButton = Button(this).apply { text = "월페이퍼 스튜디오"; setOnClickListener { startActivity(Intent(this@MainActivity, WallpaperStudioActivity::class.java)) } }
        val sailorMoonButton = Button(this).apply { text = "☾ 세일러문 Fold 테마"; setOnClickListener { startActivity(Intent(this@MainActivity, SailorMoonSetupActivity::class.java)) } }
        launcherButton = Button(this).apply { text = "사진 기반 나만의 홈 화면"; setOnClickListener { startActivity(Intent(this@MainActivity, LauncherActivity::class.java)) } }
        val cyberDashboardButton = Button(this).apply { text = "⚡ 사이버 포뮬러 대시보드"; setOnClickListener { startActivity(Intent(this@MainActivity, CyberDashboardActivity::class.java)) } }
        val desktopStudioButton = Button(this).apply { text = "🖥 AI 데스크톱 스튜디오"; setOnClickListener { startActivity(Intent(this@MainActivity, DesktopStudioActivity::class.java)) } }
        val widgetStudioButton = Button(this).apply { text = "▦ 나만의 위젯 스튜디오"; setOnClickListener { startActivity(Intent(this@MainActivity, WidgetStudioActivity::class.java)) } }
        val helpButton = Button(this).apply {
            text = "사용 방법 · 정리 순서"
            setOnClickListener { showUserGuide() }
        }
        val buttonLabels = linkedMapOf<Button, Pair<String, String>>(
            fullStorageScanButton to ("storage" to "내부 저장공간 전체 검사"),
            mediaScanButton to ("media" to "사진 · 동영상 · 음악 검사"),
            scanButton to ("folder" to "스캔할 폴더 추가"),
            downloadFolderButton to ("download" to "Download 하위 폴더 선택"),
            runScanButton to ("scan" to "선택한 폴더 모두 스캔 (0개)"),
            addFilesButton to ("add" to "개별 파일 여러 개 추가"),
            bulkDeleteButton to ("delete" to "선택 파일 일괄 삭제"),
            manageFoldersButton to ("manage" to "선택 폴더 관리"),
            duplicateButton to ("duplicate" to "완전 중복 검사 · SHA-256"),
            insightButton to ("review" to "정리 후보 분석"),
            classifyButton to ("classify" to "파일 유형 분류 · 이동 추천"),
            contentAuditButton to ("audit" to "파일 내용 시그니처 검사"),
            apkButton to ("apk" to "APK 권한 · 버전 검사"),
            archiveButton to ("archive" to "ZIP 내부 구성 검사"),
            historyButton to ("history" to "파일 작업 기록"),
            versionManagerButton to ("versions" to "파일 버전 · 프로젝트 관리"),
            appsButton to ("classify" to "앱 분류 · 그룹 관리"),
            wallpaperButton to ("archive" to "월페이퍼 스튜디오"),
            launcherButton to ("storage" to "사진 기반 나만의 홈 화면"),
            sailorMoonButton to ("theme" to "세일러문 Fold 테마"),
            cyberDashboardButton to ("dashboard" to "사이버 포뮬러 대시보드"),
            desktopStudioButton to ("manage" to "AI 데스크톱 스튜디오"),
            widgetStudioButton to ("widgets" to "나만의 위젯 스튜디오")
        )
        fun addSection(label: String, buttons: List<Button>) {
            val sectionIcon = when (label) {
                "파일 정리" -> "▣"
                "간단 점검" -> "⌕"
                "앱 관리" -> "▦"
                "꾸미기" -> "▧"
                else -> "·"
            }
            menuColumn.addView(TextView(this).apply {
                text = "$sectionIcon  $label"; textSize = 15f; setTextColor(0xFF65734B.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(3, 16, 3, 7)
                letterSpacing = 0.025f
            })
            val grid = android.widget.GridLayout(this).apply { columnCount = columns; useDefaultMargins = false; alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS }
            buttons.forEachIndexed { index, button ->
                val item = buttonLabels[button]
                if (item != null) { button.text = item.second; button.setCompoundDrawablesWithIntrinsicBounds(null, EbookActionIconDrawable(item.first, if (button === mediaScanButton || button === runScanButton || button === fullStorageScanButton) 0xFFF4F0E6.toInt() else 0xFF65734B.toInt()), null, null); button.compoundDrawablePadding = (5 * density).toInt() }
                button.isAllCaps = false; button.textSize = 12f; button.gravity = android.view.Gravity.CENTER
                button.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                button.setTextColor(if (button === mediaScanButton || button === runScanButton || button === fullStorageScanButton) 0xFFF4F0E6.toInt() else 0xFF302F27.toInt())
                button.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(if (button === mediaScanButton || button === runScanButton || button === fullStorageScanButton) 0xFF65734B.toInt() else 0xFFE8E1D2.toInt())
                    cornerRadius = 5f * density; setStroke((1 * density).toInt(), 0xFFC9C0AD.toInt())
                }
                button.setPadding((8*density).toInt(), (9*density).toInt(), (8*density).toInt(), (9*density).toInt())
                button.minHeight = (70*density).toInt()
                val lp = android.widget.GridLayout.LayoutParams(
                    android.widget.GridLayout.spec(index / columns, 1f),
                    android.widget.GridLayout.spec(index % columns, 1f)
                ).apply { width = 0; height = (82*density).toInt(); setMargins((4*density).toInt(), (4*density).toInt(), (4*density).toInt(), (4*density).toInt()) }
                grid.addView(button, lp)
            }
            menuColumn.addView(grid)
        }
        menuColumn.addView(title); menuColumn.addView(subtitle); menuColumn.addView(foldStatus); menuColumn.addView(summary); menuColumn.addView(storageSummary)
        addSection("파일 정리", listOf(fullStorageScanButton, mediaScanButton, scanButton, downloadFolderButton, runScanButton, addFilesButton, bulkDeleteButton, manageFoldersButton))
        val quickReviewButton = Button(this).apply { text = "한눈에 점검"; isEnabled = true; setOnClickListener { showOrganizationInsights() } }
        exportListButton = Button(this).apply { text = "파일 목록 내보내기"; isEnabled = false; setOnClickListener { exportFileList() } }
        addSection("간단 점검", listOf(quickReviewButton, classifyButton, insightButton, duplicateButton, exportListButton, versionManagerButton, historyButton, helpButton))
        addSection("앱 관리", listOf(appsButton))
        addSection("꾸미기", listOf(wallpaperButton, launcherButton, sailorMoonButton, cyberDashboardButton, desktopStudioButton, widgetStudioButton))
        val resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (twoPane) 14 else 0, 0, 0, 8)
        }
        resultPanel.addView(TextView(this).apply {
            text = if (twoPane) "검사 결과 · 상세 작업" else "검사 결과 전체 목록"
            textSize = 18f; setTextColor(0xFF28271F.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(2, 22, 2, 8)
        })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 6, 0, 8) }
        resultPanel.addView(results)

        if (twoPane) {
            val splitRoot = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(14, 12, 14, 14)
                setBackgroundColor(0xFFF4F0E6.toInt())
            }
            splitRoot.addView(ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                addView(menuColumn)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f))
            splitRoot.addView(View(this).apply {
                setBackgroundColor(0xFFC9C0AD.toInt())
            }, LinearLayout.LayoutParams((1 * density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT))
            splitRoot.addView(ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                addView(resultPanel)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f))
            setContentView(splitRoot)
        } else {
            outer.addView(menuColumn)
            outer.addView(resultPanel)
            setContentView(ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(outer) })
        }
        updateStorageSummary()
    }

    private fun showUserGuide() {
        val guideText = """
스마트 정리함 사용 설명서

1. 검사 시작하기
• ‘Download 전체 자동 검사’를 누르면 Android의 ‘모든 파일 접근’ 권한이 필요할 수 있습니다.
• 권한 설정 후 앱으로 돌아오면 Download 폴더와 하위 폴더를 검사합니다.
• ‘스캔할 폴더 추가’ 또는 ‘개별 파일 여러 개 추가’로 검사 범위를 직접 선택할 수도 있습니다.
• 미디어 검사는 허용한 사진·동영상·음악에 접근합니다.

2. 정리 후보와 이름 중복
• ‘정리 후보 분석’은 이름이 같은 파일을 그룹으로 묶어 보여줍니다.
• 이름이 같아도 내용이 다를 수 있습니다. 이름만 같다는 이유로 자동 삭제하지 마세요.
• 그룹 안의 파일 경로를 확인하고 파일을 눌러 ‘파일 정보 보기’로 위치와 크기를 확인할 수 있습니다.

3. 완전 중복 검사
• ‘완전 중복 검사 · SHA-256’은 파일 내용을 해시로 비교해 내용이 같은 파일을 찾습니다.
• 같은 내용의 파일이 여러 개라면 보존할 위치의 파일을 먼저 정하고, 나머지는 파일별 작업 메뉴에서 직접 삭제하세요.
• 현재 버전은 중복 그룹에서 하나만 자동으로 남기고 나머지를 일괄 삭제하지 않습니다.

4. 파일 이동·재분류
• 파일 목록에서 파일을 누르고 ‘안전한 폴더로 이동·재분류’를 선택하면 이동할 위치를 지정할 수 있습니다.
• 화면에서 분류되어 보이는 것과 실제 파일이 이동되는 것은 다릅니다. 실제 이동은 이동 메뉴에서 실행해야 합니다.
• 이동이 지원되지 않는 위치나 Android 보호 영역에서는 작업이 제한될 수 있습니다.

5. 삭제하기
• 파일을 누르고 ‘삭제’를 선택하면 확인 절차를 거칩니다.
• 여러 파일은 체크박스로 선택한 뒤 ‘선택 파일 일괄 삭제’를 사용할 수 있습니다.
• 삭제는 되돌리기 어려울 수 있으므로 경로와 파일 내용을 확인한 뒤 실행하세요. 현재 별도의 삭제 대기함이나 휴지통은 제공하지 않습니다.

6. 결과 목록 접기·펼치기
• 현재 버전은 검사 결과를 목록으로 표시합니다. 분류 그룹별 접기·펼치기 기능은 아직 제공하지 않습니다.

7. 앱 관리와 꾸미기
• ‘앱 분류 · 그룹 관리’에서 설치 앱의 분류와 앱 정보·권한·버전을 확인할 수 있습니다.
• 월페이퍼, 위젯, 데스크톱 스튜디오 등으로 화면을 꾸밀 수 있습니다.

안전 안내
• 검사 결과는 정리 판단을 돕기 위한 정보입니다. 파일을 자동으로 삭제하거나 이동하지 않습니다.
• Android/data·Android/obb 등 시스템 보호 폴더는 Android 버전과 기기 정책에 따라 접근이 제한될 수 있습니다.
• 중요한 파일은 정리 전에 별도로 백업하세요.
        """.trimIndent()
        val body = TextView(this).apply {
            text = guideText
            textSize = 15f
            setTextColor(0xFF37362F.toInt())
            setPadding(20, 16, 20, 16)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(body)
            setBackgroundColor(0xFFF4F0E6.toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("스마트 정리함 사용 설명서")
            .setView(scroll)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun updateStorageSummary() {
        try {
            val stat = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
            val total = stat.totalBytes.coerceAtLeast(0L)
            val free = stat.availableBytes.coerceAtLeast(0L)
            val used = (total - free).coerceAtLeast(0L)
            storageSummary.text = "저장 공간\n사용 \${humanSize(used)} / 전체 \${humanSize(total)}\n남은 공간 \${humanSize(free)}"
        } catch (_: Exception) { storageSummary.text = "저장 공간 정보를 가져올 수 없습니다." }
    }

    private fun requestMediaAccessAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            scanMediaLibrary()
        } else {
            requestPermissions(missing.toTypedArray(), REQUEST_MEDIA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                scanMediaLibrary()
            } else {
                toast("미디어 접근 권한이 필요합니다. 권한을 허용하거나 폴더를 직접 추가하세요.")
            }
        }
    }

    private fun scanMediaLibrary() {
        setBusy(true, "기기에서 접근 가능한 사진·동영상·음악을 검색 중입니다…")
        worker.execute {
            val found = mutableListOf<Entry>()
            var inaccessible = 0
            val collection = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.Files.FileColumns.MEDIA_TYPE
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString()
            )
            try {
                contentResolver.query(collection, projection, selection, args,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    while (cursor.moveToNext() && found.size < MAX_SCAN_ENTRIES) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "미디어 파일"
                        val relative = cursor.getString(pathCol) ?: "미디어"
                        val size = if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol).coerceAtLeast(0L)
                        val modified = if (cursor.isNull(dateCol)) 0L else cursor.getLong(dateCol) * 1000L
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        found.add(Entry(name, relative + name, size, modified, uri, false))
                    }
                } ?: run { inaccessible++ }
            } catch (_: SecurityException) {
                inaccessible++
            } catch (_: Exception) {
                inaccessible++
            }
            runOnUiThread {
                entries.clear()
                entries.addAll(found)
                checkedEntries.clear()
                renderEntries(found)
                setBusy(false)
                val mediaCount = found.size
                summary.text = "미디어 자동 검사 완료: 사진·동영상·음악 ${mediaCount}개" +
                    if (inaccessible > 0) " · 권한 또는 접근 제한 ${inaccessible}건" else ""
                duplicateButton.isEnabled = found.isNotEmpty()
                insightButton.isEnabled = found.isNotEmpty()
                classifyButton.isEnabled = found.isNotEmpty()
                contentAuditButton.isEnabled = found.isNotEmpty()
                apkButton.isEnabled = found.isNotEmpty()
                archiveButton.isEnabled = found.any { it.name.endsWith(".zip", true) }
                toast("접근 가능한 미디어 ${mediaCount}개를 찾았습니다. Android/data 등 앱 보호 폴더와 일반 문서 파일은 별도 폴더 접근이 필요합니다.")
            }
        }
    }

    private fun chooseFullStorageRoot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            AlertDialog.Builder(this).setTitle("내부 저장공간 전체 접근 권한")
                .setMessage("Download뿐 아니라 Documents, DCIM, Pictures, Movies, Music 및 사용자가 만든 모든 일반 폴더와 하위 폴더를 검사합니다. Android 시스템·앱 보호 영역은 접근이 제한될 수 있습니다. 검사만 수행하며 파일을 자동 이동·삭제하지 않습니다. Android 설정에서 ‘모든 파일 접근’을 허용해 주세요.")
                .setNegativeButton("취소", null).setPositiveButton("설정 열기") { _, _ ->
                    try { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName))) }
                    catch (_: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }.show()
        } else scanAllAccessibleStorage()
    }

    override fun onResume() {
        super.onResume()
        if (::storageSummary.isInitialized) updateStorageSummary()
        if (::fullStorageScanButton.isInitialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fullStorageScanButton.text = if (Environment.isExternalStorageManager()) "⬇ 내부 저장공간 전체 다시 검사 (권한 허용됨)" else "⬇ 내부 저장공간 전체 검사 (모든 파일 접근 권한 필요)"
        }
    }

    private fun scanAllAccessibleStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) { chooseFullStorageRoot(); return }
        val root = Environment.getExternalStorageDirectory()
        setBusy(true, "내부 저장공간 전체 폴더와 하위 폴더를 검사 중입니다…")
        worker.execute {
            val found = mutableListOf<Entry>()
            var inaccessible = 0
            var truncated = false
            val queue = java.util.ArrayDeque<File>()
            if (root.isDirectory && root.canRead()) queue.add(root) else inaccessible++
            val visited = hashSetOf<String>()
            while (queue.isNotEmpty() && !truncated) {
                val dir = queue.removeFirst()
                val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
                if (!visited.add(canonical)) continue
                val children = try { dir.listFiles() ?: emptyArray() } catch (_: Exception) { inaccessible++; emptyArray() }
                for (child in children) {
                    if (found.size >= MAX_SCAN_ENTRIES) { truncated = true; break }
                    val relative = child.absolutePath.removePrefix(root.absolutePath).trimStart(File.separatorChar)
                    val displayPath = if (relative.isBlank()) child.name else relative
                    val isDirectory = child.isDirectory
                    found.add(Entry(child.name, displayPath, if (child.isFile) child.length().coerceAtLeast(0L) else 0L, child.lastModified(), Uri.fromFile(child), isDirectory))
                    if (isDirectory && child.canRead()) queue.add(child) else if (isDirectory) inaccessible++
                }
            }
            runOnUiThread {
                entries.clear(); entries.addAll(found); checkedEntries.clear()
                val files = found.filter { !it.directory }
                summary.text = "내부 저장공간 전체 검사 완료 · 파일 ${files.size}개 · 폴더 ${found.count { it.directory }}개 · ${humanSize(files.sumOf { it.size })}" +
                    (if (inaccessible > 0) "\\n읽기 제한 위치 ${inaccessible}곳 (Android 보호 폴더 등)" else "") +
                    (if (truncated) "\\n안전 제한: ${MAX_SCAN_ENTRIES}개 항목까지만 검사했습니다." else "")
                duplicateButton.isEnabled = files.isNotEmpty(); insightButton.isEnabled = files.isNotEmpty(); classifyButton.isEnabled = files.isNotEmpty()
                contentAuditButton.isEnabled = files.isNotEmpty(); apkButton.isEnabled = files.isNotEmpty()
                archiveButton.isEnabled = files.any { it.name.endsWith(".zip", true) }
                renderEntries(found); setBusy(false); toast("접근 가능한 내부 저장공간 전체에서 ${files.size}개 파일을 찾았습니다.")
            }
        }
    }

    private fun openEntryInputStream(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            File(path).inputStream()
        } else contentResolver.openInputStream(uri)
    }

    private fun deleteEntry(uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            return File(path).delete()
        }
        return try { DocumentFile.fromSingleUri(this, uri)?.delete() == true } catch (_: Exception) { false }
    }

    private fun renameEntryUri(uri: Uri, newName: String): Boolean {
        if (uri.scheme == "file") {
            val source = File(uri.path ?: return false)
            return source.renameTo(File(source.parentFile, newName))
        }
        return try { DocumentFile.fromSingleUri(this, uri)?.renameTo(newName) == true } catch (_: Exception) { false }
    }

    private fun chooseFolder(requestCode: Int = REQUEST_TREE) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    /**
     * Android 11+ deliberately prevents selecting the Download directory itself
     * through ACTION_OPEN_DOCUMENT_TREE. Open it as the picker starting location
     * so the user can select a child folder instead.
     */
    private fun chooseDownloadFolder() {
        val downloadDocument = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Download"
        )
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadDocument)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_TREE)
            toast("Download 안으로 이동합니다. Android 제한으로 Download 최상위 대신 내부 하위 폴더를 선택하세요.")
        } catch (_: Exception) {
            toast("Download 폴더를 바로 열지 못했습니다. ‘스캔할 폴더 추가’에서 내부 저장소 > Download로 이동해 하위 폴더를 선택하세요.")
            chooseFolder()
        }
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode == REQUEST_EXPORT_APPS) {
            val uri = data?.data ?: return
            exportInstalledAppsCsvTo(uri)
            return
        }
        if (requestCode == REQUEST_EXPORT_LIST) {
            val uri = data?.data ?: return
            exportFileListTo(uri)
            return
        }
        if (requestCode == REQUEST_MOVE_DESTINATION) {
            val source = pendingMoveEntry
            pendingMoveEntry = null
            val destinationUri = data?.data
            if (destinationUri != null) {
                try {
                    val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    contentResolver.takePersistableUriPermission(destinationUri, flags)
                } catch (_: Exception) {}
                val batch = pendingBatchMoveEntries
                if (batch.isNotEmpty()) {
                    pendingBatchMoveEntries = emptyList()
                    val wholeRoot = pendingWholeProjectRoot
                    pendingWholeProjectRoot = null
                    val folderName = pendingBatchMoveFolderName
                    pendingBatchMoveFolderName = null
                    if (wholeRoot != null) copyWholeProjectPreservingTree(batch, destinationUri, wholeRoot)
                    else copyBatchThenOfferMove(batch, destinationUri, folderName)
                } else if (source != null) copyThenOfferMove(source, destinationUri)
            }
            return
        }
        if (requestCode == REQUEST_FILES) {
            val clip = data?.clipData
            val uris = if (clip != null) (0 until clip.itemCount).map { clip.getItemAt(it).uri } else listOfNotNull(data?.data)
            uris.forEach { uri ->
                val flags = (data?.flags ?: 0) and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
                selectedLooseFiles[uri.toString()] = uri
            }
            getSharedPreferences("scan_folders", MODE_PRIVATE).edit().putStringSet("loose_files", selectedLooseFiles.keys.toSet()).apply()
            toast("폴더 밖 개별 파일 ${uris.size}개를 추가했습니다. 루트에 놓인 파일도 선택할 수 있습니다.")
            updateFolderSelectionUi()
            return
        }
        if (requestCode != REQUEST_TREE && requestCode != REQUEST_FULL_TREE) return
        val uri = data?.data ?: return
        val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
        selectedTrees[uri.toString()] = uri
        saveSelectedTrees()
        updateFolderSelectionUi()
        toast("폴더를 추가했습니다. 다른 폴더도 추가한 뒤 전체 스캔을 누르세요.")
    }

    private fun chooseFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_FILES)
    }

    private fun saveSelectedTrees() {
        getSharedPreferences("scan_folders", MODE_PRIVATE).edit()
            .putStringSet("uris", selectedTrees.keys.toSet()).apply()
    }

    private fun restoreSelectedTrees() {
        val saved = getSharedPreferences("scan_folders", MODE_PRIVATE).getStringSet("uris", emptySet()) ?: emptySet()
        saved.forEach { value ->
            val uri = Uri.parse(value)
            val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri && it.isReadPermission }
            if (permission != null) selectedTrees[value] = uri
        }
    }

    private fun restoreLooseFiles() {
        val saved = getSharedPreferences("scan_folders", MODE_PRIVATE).getStringSet("loose_files", emptySet()) ?: emptySet()
        saved.forEach { value ->
            val uri = Uri.parse(value)
            val permission = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri && it.isReadPermission }
            if (permission != null) selectedLooseFiles[value] = uri
        }
    }

    private fun updateFolderSelectionUi() {
        runScanButton.text = "선택 위치 스캔 (폴더 ${selectedTrees.size}개 · 개별 파일 ${selectedLooseFiles.size}개)"
        runScanButton.isEnabled = selectedTrees.isNotEmpty() || selectedLooseFiles.isNotEmpty()
        manageFoldersButton.isEnabled = true
        summary.text = "선택된 폴더 ${selectedTrees.size}개 · 폴더 밖 파일 ${selectedLooseFiles.size}개"
    }

    private fun showSelectedFolders() {
        val items = selectedTrees.entries.toList()
        if (items.isEmpty()) { toast("먼저 폴더를 추가하세요."); return }
        val labels = items.map { DocumentFile.fromTreeUri(this, it.value)?.name ?: "선택 폴더" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("스캔 대상 폴더").setItems(labels) { _, which ->
            val item = items[which]
            AlertDialog.Builder(this).setMessage("${labels[which]} 폴더를 제외할까요?")
                .setNegativeButton("취소", null).setPositiveButton("제외") { _, _ -> selectedTrees.remove(item.key); saveSelectedTrees(); updateFolderSelectionUi() }.show()
        }.setPositiveButton("모두 해제") { _, _ -> selectedTrees.clear(); saveSelectedTrees(); entries.clear(); updateFolderSelectionUi() }
            .setNegativeButton("닫기", null).show()
    }

    private fun scanSelectedFolders() {
        val roots = selectedTrees.values.toList()
        val loose = selectedLooseFiles.values.toList()
        if (roots.isEmpty() && loose.isEmpty()) { toast("폴더 또는 개별 파일을 추가하세요."); return }
        setBusy(true, "전체 폴더와 하위 폴더를 검사 중입니다…")
        worker.execute {
            val found = mutableListOf<Entry>()
            var inaccessible = 0
            var truncated = false
            val seen = hashSetOf<String>()
            val queue = java.util.ArrayDeque<Pair<DocumentFile, String>>()
            roots.forEachIndexed { index, treeUri ->
                val root = try { DocumentFile.fromTreeUri(this, treeUri) } catch (_: Exception) { null }
                if (root == null) inaccessible++ else {
                    val rootName = root.name ?: "폴더 ${index + 1}"
                    queue.add(root to rootName)
                }
            }
            while (queue.isNotEmpty() && !truncated) {
                val (dir, parentPath) = queue.removeFirst()
                val children = try { dir.listFiles() } catch (_: Exception) { inaccessible++; emptyArray() }
                for (child in children) {
                    if (found.size >= MAX_SCAN_ENTRIES) { truncated = true; break }
                    val uriKey = child.uri.toString()
                    if (!seen.add(uriKey)) continue
                    val name = child.name ?: "(이름 없음)"
                    val path = "$parentPath/$name"
                    val isDir = child.isDirectory
                    found.add(Entry(name, path, if (child.isFile) child.length().coerceAtLeast(0L) else 0L, child.lastModified(), child.uri, isDir))
                    if (isDir) queue.add(child to path)
                }
            }
            loose.forEach { uri ->
                if (found.size >= MAX_SCAN_ENTRIES) truncated = true
                else if (seen.add(uri.toString())) {
                    try {
                        val doc = DocumentFile.fromSingleUri(this, uri)
                        if (doc != null) found.add(Entry(doc.name ?: "개별 파일", doc.name ?: "개별 파일", doc.length().coerceAtLeast(0L), doc.lastModified(), uri, false))
                        else inaccessible++
                    } catch (_: Exception) { inaccessible++ }
                }
            }
            runOnUiThread {
                entries.clear()
                entries.addAll(found)
                val files = entries.filter { !it.directory }
                summary.text = "전체 스캔 완료: 폴더 ${roots.size}개 · 개별 파일 ${loose.size}개 · 파일 ${files.size}개 · 하위 폴더 ${entries.count { it.directory }}개 · ${humanSize(files.sumOf { it.size })}" +
                    (if (inaccessible > 0) "\n일부 위치는 권한 제한 또는 읽기 오류로 제외되었습니다(${inaccessible}곳)." else "") +
                    (if (truncated) "\n안전 제한: ${MAX_SCAN_ENTRIES}개 항목까지만 검사했습니다." else "")
                duplicateButton.isEnabled = files.isNotEmpty()
                insightButton.isEnabled = files.isNotEmpty()
                classifyButton.isEnabled = files.isNotEmpty()
                contentAuditButton.isEnabled = files.isNotEmpty()
                apkButton.isEnabled = files.isNotEmpty()
                archiveButton.isEnabled = files.any { it.name.endsWith(".zip", true) }
                renderEntries(entries)
                setBusy(false)
            }
        }
    }
    private fun showFolderSizeReport() {
        val files = entries.filter { !it.directory }
        val totals = linkedMapOf<String, Long>()
        files.forEach { file ->
            val parts = file.relativePath.split("/")
            if (parts.size > 1) {
                for (i in 1 until parts.size) {
                    val folder = parts.take(i).joinToString("/")
                    totals[folder] = (totals[folder] ?: 0L) + file.size
                }
            }
        }
        results.removeAllViews()
        addLine("폴더별 사용 용량", true)
        if (totals.isEmpty()) addLine("집계할 폴더가 없습니다.")
        totals.entries.sortedByDescending { it.value }.take(500).forEach { (folder, size) -> addLine("📁 $folder · ${humanSize(size)}") }
        summary.text = "폴더별 용량 집계 완료 · 검사된 파일 ${files.size}개 기준"
    }

    private fun classifyFile(entry: Entry): String {
        val n = entry.name.lowercase(java.util.Locale.ROOT)
        val path = entry.relativePath.replace('\\', '/').lowercase(java.util.Locale.ROOT)
        val ext = n.substringAfterLast('.', "")
        return when {
            listOf("recordings/call/", "call recording", "통화 녹음", "통화녹음").any { path.contains(it) || n.contains(it) } -> "통화 녹음 (보관 전용)"
            listOf("recordings/voice recorder/", "voice recorder", "voice_record", "음성 녹음", "음성녹음", "녹음 파일").any { path.contains(it) || n.contains(it) } -> "음성 녹음 (보관 전용)"
            path.startsWith("hazel zip extractor/") || path.contains("/extracted/") ||
                path.startsWith("download/hipdogsound/") -> "개발·프로젝트 (폴더째 복사 권장 · 개별 이동 제외)"
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw") -> "사진"
            ext in setOf("mp4", "m4v", "mov", "mkv", "avi", "webm", "3gp", "ts") -> "동영상"
            ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3ga") -> "음악·오디오"
            ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "hwp", "hwpx", "md", "csv") -> "문서"
            ext in setOf("zip", "7z", "rar", "tar", "gz", "bz2") -> "압축 파일"
            ext in setOf("apk", "apks", "aab", "dex", "so", "jar", "exe", "dll") -> "설치·실행 파일 (이동 제한)"
            n.startsWith(".") || ext in setOf("db", "sqlite", "bak", "conf", "ini", "xml", "json", "dat") -> "설정·데이터 (이동 제외 권장)"
            else -> "기타 파일"
        }
    }

    private fun isProtectedPath(entry: Entry): Boolean {
        // The selected SAF root may prefix the path (e.g. "Internal storage").
        // Inspect every component to catch nested Android/data and Android/obb.
        val parts = entry.relativePath.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val protectedNames = setOf(
            "android", "data", "obb", "system", "vendor", "product", "apex",
            "proc", "sys", "dev", "metadata"
        )
        return parts.any { part ->
            part == ".." || part == "." ||
                part.lowercase(java.util.Locale.ROOT) in protectedNames
        }
    }

    private fun detectFileSignature(uri: Uri): String {
        val header = ByteArray(16)
        val count = try {
            openEntryInputStream(uri)?.use { input -> input.read(header) } ?: -1
        } catch (_: Exception) { -1 }
        if (count <= 0) return "읽기 불가"
        fun starts(vararg bytes: Int): Boolean =
            count >= bytes.size && bytes.indices.all { (header[it].toInt() and 0xff) == bytes[it] }
        return when {
            starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "PNG 이미지"
            starts(0xff, 0xd8, 0xff) -> "JPEG 이미지"
            starts(0x47, 0x49, 0x46, 0x38) -> "GIF 이미지"
            starts(0x25, 0x50, 0x44, 0x46) -> "PDF 문서"
            starts(0x50, 0x4b, 0x03, 0x04) || starts(0x50, 0x4b, 0x05, 0x06) -> "ZIP 컨테이너"
            starts(0x52, 0x61, 0x72, 0x21) -> "RAR 압축"
            starts(0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c) -> "7Z 압축"
            starts(0x4f, 0x67, 0x67, 0x53) -> "OGG 오디오 컨테이너"
            starts(0x49, 0x44, 0x33) -> "MP3 오디오"
            count >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> "WebP 이미지"
            count >= 8 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp" -> "MP4/ISO 미디어"
            starts(0x4d, 0x5a) -> "Windows 실행 파일 형식 (MZ)"
            starts(0x7f, 0x45, 0x4c, 0x46) -> "ELF 실행 파일"
            else -> "알 수 없음/시그니처 미지원"
        }
    }

    private fun auditFileSignatures() {
        val snapshot = entries.filter { !it.directory }.toList()
        if (snapshot.isEmpty()) { toast("먼저 파일을 스캔하세요."); return }
        setBusy(true, "파일 앞부분 시그니처를 읽어 확장자와 비교 중입니다…")
        worker.execute {
            val reports = mutableListOf<String>()
            snapshot.forEach { entry ->
                val detected = detectFileSignature(entry.uri)
                val ext = entry.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
                val mismatch = when {
                    detected == "PNG 이미지" -> ext !in setOf("png")
                    detected == "JPEG 이미지" -> ext !in setOf("jpg", "jpeg")
                    detected == "GIF 이미지" -> ext !in setOf("gif")
                    detected == "PDF 문서" -> ext != "pdf"
                    detected == "ZIP 컨테이너" -> ext !in setOf("zip", "docx", "xlsx", "pptx", "odt", "ods", "odp", "apks")
                    detected == "Windows 실행 파일 형식 (MZ)" -> ext !in setOf("exe", "dll")
                    detected == "ELF 실행 파일" -> ext !in setOf("so", "elf")
                    else -> false
                }
                reports.add((if (mismatch) "⚠ 확장자 불일치 의심" else "✓") +
                    "\\n${entry.relativePath}\\n확장자: ${if (ext.isEmpty()) "(없음)" else ext} · 내용 시그니처: $detected")
            }
            runOnUiThread {
                results.removeAllViews()
                addLine("파일 내용 시그니처 검사", true)
                addLine("파일 앞부분의 알려진 형식 표식만 확인합니다. 전체 파일 무결성·악성 여부·암호화 여부를 판정하지 않습니다.")
                reports.forEach { addLine(it) }
                summary.text = "시그니처 검사 완료 · ${snapshot.size}개 파일"
                setBusy(false)
            }
        }
    }

    private fun showAutoFileClassification() {
        val files = entries.filter { !it.directory }
        if (files.isEmpty()) { toast("먼저 폴더를 스캔하세요."); return }
        val groups = files.groupBy { classifyFile(it) }.toSortedMap()
        results.removeAllViews()
        addLine("파일 유형별 정리", true)
        addLine("분류별 전체 선택 후 선택 파일 삭제 또는 일괄 이동을 할 수 있습니다.")
        addLine("APK 설치 파일은 설치된 앱과 별개인 설치 패키지이므로 원하면 삭제할 수 있습니다. APK를 삭제해도 설치된 앱은 삭제되지 않습니다.")
        addLine("Android/.Trash 안의 파일은 삼성 내 파일 휴지통 항목일 수 있어 일괄 선택·삭제에서 제외합니다.")
        addLine("통화·음성 녹음, 개발 프로젝트, 설정·실행 파일은 안전을 위해 일괄 삭제·이동에서 보호합니다.")

        groups.forEach { (category, group) ->
            val isProject = category.contains("프로젝트")
            val isRecording = category.contains("녹음")
            val isRestricted = isProject || category.contains("이동 제한") || category.contains("이동 제외")
            fun isTrash(entry: Entry): Boolean =
                entry.relativePath.replace('/', '/').split('/').any {
                    it.equals(".Trash", true) || it.equals("Trash", true)
                } || entry.relativePath.contains("com.sec.android.app.myfiles", true)
            fun isInstaller(entry: Entry): Boolean =
                listOf(".apk", ".apks", ".aab").any { entry.name.endsWith(it, true) }
            fun isExecutableOrSystem(entry: Entry): Boolean =
                listOf(".dex", ".so", ".jar", ".exe", ".dll").any { entry.name.endsWith(it, true) }
            val movable = group.filter { entry ->
                !isProtectedPath(entry) && !isTrash(entry) && !isRecording &&
                    !category.contains("이동 제한") && !category.contains("이동 제외") && !isProject &&
                    !isInstaller(entry) && !isExecutableOrSystem(entry)
            }
            val deletable = group.filter { entry ->
                !isProtectedPath(entry) && !isTrash(entry) && !isRecording && !isProject &&
                    !isExecutableOrSystem(entry) &&
                    (!isRestricted || isInstaller(entry))
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 6, 8, 8)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFFF0EBDD.toInt())
                    cornerRadius = 14f * resources.displayMetrics.density
                }
            }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            val header = Button(this).apply {
                text = "▶ $category · ${group.size}개"
                isAllCaps = false
                setOnClickListener {
                    val open = body.visibility != View.VISIBLE
                    body.visibility = if (open) View.VISIBLE else View.GONE
                    text = (if (open) "▼ " else "▶ ") + "$category · ${group.size}개"
                }
            }
            val selected = linkedMapOf<String, Entry>()
            val count = TextView(this).apply {
                text = "선택 0개 / 삭제 가능 ${deletable.size}개 / 이동 가능 ${movable.size}개"
                textSize = 13f; setTextColor(0xFF37362F.toInt()); setPadding(8, 8, 8, 8)
            }
            body.addView(count)
            if (isRestricted || isRecording) body.addView(TextView(this).apply {
                text = when {
                    isRecording -> "🔒 통화·음성 녹음은 보관 보호 항목입니다. 일괄 삭제·이동할 수 없습니다."
                    isProject -> "⚠ 프로젝트 파일은 앱 실행·빌드에 필요할 수 있어 개별 작업을 제한합니다. 기존 폴더 구조를 유지한 전체 프로젝트 복사만 사용하세요."
                    else -> "🔒 시스템·설정·실행 관련 파일은 일괄 작업에서 제외합니다. APK 설치 패키지는 별도로 삭제할 수 있습니다."
                }
                textSize = 12f; setTextColor(0xFF8A6740.toInt()); setPadding(8, 4, 8, 8)
            })
            val checks = mutableListOf<CheckBox>()
            fun updateCount() {
                count.text = "선택 ${selected.size}개 / 삭제 가능 ${deletable.size}개 / 이동 가능 ${movable.size}개"
            }
            fun refreshChecks() {
                checks.forEach { box ->
                    val key = box.tag as? String ?: return@forEach
                    box.setOnCheckedChangeListener(null)
                    box.isChecked = selected.containsKey(key)
                    box.setOnCheckedChangeListener { _, checked ->
                        val item = group.firstOrNull { it.uri.toString() == key }
                        if (item != null) { if (checked) selected[key] = item else selected.remove(key); updateCount() }
                    }
                }
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply {
                text = "전체 선택"; isAllCaps = false; isEnabled = deletable.isNotEmpty()
                setOnClickListener { deletable.forEach { selected[it.uri.toString()] = it }; updateCount(); refreshChecks() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply {
                text = "선택 해제"; isAllCaps = false
                setOnClickListener { selected.clear(); updateCount(); refreshChecks() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply {
                text = "일괄 이동"; isAllCaps = false; isEnabled = movable.isNotEmpty()
                setOnClickListener {
                    val chosen = selected.values.filter { movable.any { m -> m.uri == it.uri } }
                    if (chosen.isEmpty()) toast("이동 가능한 파일을 선택하세요.")
                    else chooseBatchMoveDestination(chosen, category.replace(Regex("[^가-힣A-Za-z0-9 _-]"), "").take(30))
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(actions)
            body.addView(Button(this).apply {
                text = "선택 파일 삭제"; isAllCaps = false; isEnabled = deletable.isNotEmpty()
                setOnClickListener {
                    val chosen = selected.values.filter { deletable.any { d -> d.uri == it.uri } }
                    if (chosen.isEmpty()) toast("삭제할 파일을 선택하세요.")
                    else confirmDeleteGroup(chosen)
                }
            })
            group.take(1000).forEach { entry ->
                val blocked = !deletable.any { it.uri == entry.uri } && !movable.any { it.uri == entry.uri }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(4, 2, 4, 2) }
                val check = CheckBox(this).apply {
                    tag = entry.uri.toString()
                    isEnabled = !blocked
                    buttonTintList = android.content.res.ColorStateList.valueOf(if (blocked) 0xFF78889A.toInt() else 0xFF65734B.toInt())
                    setOnCheckedChangeListener { _, checked ->
                        val key = entry.uri.toString()
                        if (checked) selected[key] = entry else selected.remove(key)
                        updateCount()
                    }
                }
                checks.add(check); row.addView(check)
                row.addView(TextView(this).apply {
                    val badge = when { isTrash(entry) -> "🗑 "; isInstaller(entry) -> "📦 "; blocked -> "🔒 "; else -> "📄 " }
                    text = badge + entry.relativePath + " · " + humanSize(entry.size)
                    textSize = 12f; setTextColor(0xFF37362F.toInt()); setPadding(6, 7, 4, 7)
                    setOnClickListener { showFileActions(entry) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(row)
            }
            if (group.size > 1000) body.addView(TextView(this).apply {
                text = "표시 제한: ${group.size - 1000}개 파일은 목록에서 생략했습니다."
                setTextColor(0xFF37362F.toInt()); setPadding(8, 8, 8, 8)
            })
            card.addView(header); card.addView(body); results.addView(card)
        }
        summary.text = "분류 ${groups.size}개 · 파일 ${files.size}개 · 전체 선택 후 선택 삭제 가능 · 녹음/휴지통/프로젝트 보호"
    }

    private fun showVersionManager() {
        val files = entries.filter { !it.directory }
        if (files.isEmpty()) { toast("먼저 폴더를 스캔하세요."); return }
        results.removeAllViews()
        addLine("파일 버전 · 프로젝트 관리", true)
        addLine("① 파일명에 표시된 버전 ② 같은 프로젝트 폴더의 파일을 함께 살펴봅니다.")
        addLine("최신 판단은 버전 번호가 있으면 버전 번호 우선, 없으면 수정 날짜 참고입니다. 파일 내용과 용도를 확인한 뒤 정리하세요.")

        val versionPattern = Regex("(?i)(?:^|[._ -])(?:v|ver|version)[._ -]?(\\d+(?:[._-]\\d+)*)")
        fun versionOf(name: String): List<Int>? {
            val match = versionPattern.find(name) ?: return null
            return match.groupValues[1].split(Regex("[._-]")).mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
        }
        fun familyName(name: String): String {
            val withoutVersion = name.replace(versionPattern, " ").replace(Regex("\\s+"), " ").trim()
            return withoutVersion.lowercase(java.util.Locale.ROOT)
        }
        fun compareVersion(a: Entry, b: Entry): Int {
            val av = versionOf(a.name) ?: emptyList()
            val bv = versionOf(b.name) ?: emptyList()
            if (av.isNotEmpty() && bv.isNotEmpty()) {
                for (i in 0 until maxOf(av.size, bv.size)) {
                    val diff = (av.getOrElse(i) { 0 }).compareTo(bv.getOrElse(i) { 0 })
                    if (diff != 0) return diff
                }
            }
            return a.modified.compareTo(b.modified)
        }
        val versionGroups = files.groupBy { familyName(it.name) }
            .filter { (_, group) -> group.size > 1 && group.any { versionOf(it.name) != null } }
            .toSortedMap()
        addLine("파일명 버전 그룹 · ${versionGroups.size}개", true)
        if (versionGroups.isEmpty()) addLine("v1, v2, version 2.1 같은 버전 표기가 포함된 파일 그룹이 없습니다.")
        versionGroups.forEach { (family, group) ->
            val ordered = group.sortedWith(Comparator { a, b -> compareVersion(b, a) })
            val latest = ordered.first()
            val groupBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val toggle = Button(this).apply {
                text = "▶ ${family} · ${group.size}개  | 최신 후보: ${latest.name}"
                isAllCaps = false
            }
            val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
            toggle.setOnClickListener {
                val expanded = detail.visibility != android.view.View.VISIBLE
                detail.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
                toggle.text = (if (expanded) "▼ " else "▶ ") + "${family} · ${group.size}개  | 최신 후보: ${latest.name}"
            }
            ordered.forEachIndexed { index, entry ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                row.addView(CheckBox(this).apply {
                    text = if (entry.uri.toString() == latest.uri.toString()) "보존 후보" else "정리 후보"
                    isChecked = checkedEntries.contains(entry.uri.toString())
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) checkedEntries.add(entry.uri.toString()) else checkedEntries.remove(entry.uri.toString())
                        bulkDeleteButton.isEnabled = checkedEntries.isNotEmpty()
                    }
                })
                val label = TextView(this).apply {
                    text = "${entry.name} · ${humanSize(entry.size)} · ${formatDate(entry.modified)}" +
                        (if (entry.uri.toString() == latest.uri.toString()) "  ★ 최신 후보" else "  · 구버전 후보")
                    textSize = 12f
                    setTextColor(if (index == 0) 0xFF65734B.toInt() else 0xFF37362F.toInt())
                    setPadding(6, 8, 6, 8)
                    setOnClickListener { showFileActions(entry) }
                }
                row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
                detail.addView(row)
            }
            groupBox.addView(toggle)
            groupBox.addView(detail)
            results.addView(groupBox)
        }

        val projectGroups = files.groupBy { it.relativePath.substringBeforeLast("/", "").ifBlank { "(최상위 폴더)" } }
            .filter { (_, group) -> group.size >= 3 }
            .toSortedMap()
        addLine("프로젝트·폴더 단위 파일 그룹 · ${projectGroups.size}개", true)
        val projectRoot = inferProjectRoot(projectGroups.keys)
        val wholeProjectFiles = projectRoot?.let { root ->
            files.filter { it.relativePath.replace('\\', '/').startsWith("$root/") }
        } ?: emptyList()
        if (wholeProjectFiles.isNotEmpty() && projectRoot != null) {
            addLine("전체 프로젝트 복사 기준 경로: $projectRoot")
            results.addView(Button(this).apply {
                text = "🛡 전체 프로젝트 안전 복사 · ${wholeProjectFiles.size}개 파일"
                isAllCaps = false
                setOnClickListener { chooseWholeProjectCopy(wholeProjectFiles, projectRoot) }
            })
        }
        addLine("같은 폴더에 있는 파일을 프로젝트 후보로 묶습니다. 서로 다른 파일의 최신 여부는 자동 판정하지 않습니다.")
        projectGroups.forEach { (folder, group) ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val toggle = Button(this).apply { text = "▶ ${folder} · ${group.size}개"; isAllCaps = false }
            val moveGroup = Button(this).apply {
                text = "📁 이 프로젝트 파일 ${group.size}개를 폴더째 묶어 이동"
                isAllCaps = false
                setOnClickListener { chooseBatchMoveDestination(group, folder.substringAfterLast("/").ifBlank { "프로젝트" }) }
            }
            val detail = LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; visibility = android.view.View.GONE }
            toggle.setOnClickListener {
                val expanded = detail.visibility != android.view.View.VISIBLE
                detail.visibility = if (expanded) android.view.View.VISIBLE else android.view.View.GONE
                toggle.text = (if (expanded) "▼ " else "▶ ") + "${folder} · ${group.size}개"
            }
            group.sortedByDescending { it.modified }.forEach { entry ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                row.addView(CheckBox(this).apply {
                    isChecked = checkedEntries.contains(entry.uri.toString())
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) checkedEntries.add(entry.uri.toString()) else checkedEntries.remove(entry.uri.toString())
                        bulkDeleteButton.isEnabled = checkedEntries.isNotEmpty()
                    }
                })
                row.addView(TextView(this).apply {
                    text = "${entry.name} · ${humanSize(entry.size)} · ${formatDate(entry.modified)}"
                    textSize = 12f; setTextColor(0xFF37362F.toInt()); setPadding(6, 7, 6, 7)
                    setOnClickListener { showFileActions(entry) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                detail.addView(row)
            }
            box.addView(toggle); box.addView(moveGroup); box.addView(detail); results.addView(box)
        }
        summary.text = "버전 그룹 ${versionGroups.size}개 · 프로젝트 폴더 그룹 ${projectGroups.size}개 · 선택 후 하단 ‘선택 파일 일괄 삭제’ 또는 파일 메뉴에서 이동 가능"
    }

    // Whole-project copy intentionally preserves the source tree; no source deletion is performed.
    private fun inferProjectRoot(folderPaths: Set<String>): String? {
        val paths = folderPaths.filter { it != "(최상위 폴더)" }
            .map { it.replace('\\', '/').split('/').filter { part -> part.isNotBlank() } }
        if (paths.isEmpty()) return null
        val extractedIndex = paths.first().indexOfFirst { it.equals("extracted", true) }
        if (extractedIndex >= 0) {
            val candidate = paths.first().take(extractedIndex + 2)
            if (candidate.isNotEmpty()) return candidate.joinToString("/")
        }
        val common = mutableListOf<String>()
        for (index in 0 until (paths.minOfOrNull { it.size } ?: 0)) {
            val part = paths.first()[index]
            if (paths.all { it[index] == part }) common.add(part) else break
        }
        return common.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    private fun chooseWholeProjectCopy(files: List<Entry>, projectRoot: String) {
        val safeFiles = files.filter { !isProtectedPath(it) }
        if (safeFiles.isEmpty()) { toast("보호 경로를 제외하면 복사할 파일이 없습니다."); return }
        AlertDialog.Builder(this)
            .setTitle("전체 프로젝트 안전 복사")
            .setMessage("프로젝트 경로: $projectRoot\\n파일: ${safeFiles.size}개\\n\\n폴더 구조를 유지해 새 위치에 복사하고 모든 파일의 SHA-256을 비교합니다. 원본 파일과 원본 폴더는 삭제하거나 변경하지 않습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("복사 위치 선택") { _, _ ->
                pendingBatchMoveEntries = safeFiles
                pendingBatchMoveFolderName = null
                pendingWholeProjectRoot = projectRoot
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_MOVE_DESTINATION)
            }.show()
    }

    private fun copyWholeProjectPreservingTree(files: List<Entry>, destinationUri: Uri, projectRoot: String) {
        val selectedDestination = DocumentFile.fromTreeUri(this, destinationUri)
        if (selectedDestination == null || !selectedDestination.isDirectory || !selectedDestination.canWrite()) {
            toast("선택한 목적지에 쓸 수 없습니다."); return
        }
        val rootName = projectRoot.substringAfterLast("/").ifBlank { "프로젝트" }
        if (selectedDestination.findFile(rootName) != null) {
            toast("목적지에 같은 이름의 프로젝트가 이미 있습니다. 기존 폴더를 덮어쓰지 않도록 다른 위치를 선택하세요."); return
        }
        val rootFolder = selectedDestination.createDirectory(rootName)
        if (rootFolder == null || !rootFolder.isDirectory || !rootFolder.canWrite()) {
            toast("프로젝트 폴더를 만들 수 없습니다. 같은 이름의 파일이 있는지 확인하세요."); return
        }
        setBusy(true, "전체 프로젝트를 원본 보존 방식으로 복사·검증 중입니다…")
        worker.execute {
            var copied = 0
            val failures = mutableListOf<String>()
            val createdFiles = mutableListOf<DocumentFile>()
            val rootPrefix = "$projectRoot/"
            for (entry in files) {
                var output: DocumentFile? = null
                try {
                    if (!entry.relativePath.startsWith(rootPrefix)) throw IllegalStateException("프로젝트 기준 경로 밖의 파일")
                    val relative = entry.relativePath.removePrefix(rootPrefix).split('/').filter { it.isNotBlank() }
                    if (relative.isEmpty() || relative.any { it == "." || it == ".." || it.contains('\\') })
                        throw IllegalStateException("안전하지 않은 상대 경로")
                    var parent: DocumentFile = rootFolder
                    for (part in relative.dropLast(1)) {
                        val nextFolder = parent.findFile(part)?.takeIf { it.isDirectory }
                            ?: parent.createDirectory(part)
                            ?: throw IllegalStateException("하위 폴더 생성 실패: $part")
                        parent = nextFolder
                    }
                    val fileName = relative.last()
                    if (parent.findFile(fileName) != null) throw IllegalStateException("목적지에 같은 이름이 이미 존재")
                    val createdOutput = parent.createFile(contentResolver.getType(entry.uri) ?: "application/octet-stream", fileName)
                        ?: throw IllegalStateException("파일 생성 실패")
                    output = createdOutput
                    openEntryInputStream(entry.uri).use { input ->
                        if (input == null) throw IllegalStateException("원본 읽기 실패")
                        contentResolver.openOutputStream(createdOutput.uri, "w").use { out ->
                            if (out == null) throw IllegalStateException("목적지 쓰기 실패")
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                out.write(buffer, 0, count)
                            }
                            out.flush()
                        }
                    }
                    if (createdOutput.length() != entry.size) throw IllegalStateException("파일 크기 검증 실패")
                    if (sha256Uri(entry.uri) != sha256Uri(createdOutput.uri)) throw IllegalStateException("SHA-256 검증 실패")
                    copied++
                } catch (e: Exception) {
                    try { output?.delete() } catch (_: Exception) {}
                    failures.add("${entry.relativePath}: ${e.localizedMessage ?: "복사 실패"}")
                }
            }
            runOnUiThread {
                setBusy(false)
                val message = "검증 성공 ${copied}개 · 실패 ${failures.size}개\\n원본 파일과 폴더는 전혀 삭제하지 않았습니다.\\n실패 예시: ${failures.take(8).joinToString("\\n")}"
                AlertDialog.Builder(this).setTitle("전체 프로젝트 복사 결과")
                    .setMessage(message)
                    .setPositiveButton("확인", null).show()
                recordOperation("전체 프로젝트 안전 복사", projectRoot, "성공 $copied / 실패 ${failures.size}")
            }
        }
    }

    private fun exportFileList() {
        val snapshot = entries.toList()
        if (snapshot.isEmpty()) { toast("먼저 저장공간을 검사하세요."); return }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "SmartOrganizer_파일목록.csv")
        }
        startActivityForResult(intent, REQUEST_EXPORT_LIST)
    }

    private fun exportFileListTo(uri: Uri) {
        val snapshot = entries.toList()
        worker.execute {
            try {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { out ->
                    out.write("파일명,전체경로,종류,크기(bytes),수정일\n")
                    snapshot.forEach { entry ->
                        val kind = if (entry.directory) "폴더" else entry.name.substringAfterLast(".", "파일").uppercase()
                        val values = listOf(entry.name, entry.relativePath, kind, if (entry.directory) "" else entry.size.toString(), formatDate(entry.modified))
                        out.write(values.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
                        out.write("\n")
                    }
                } ?: throw IllegalStateException("파일을 저장할 수 없습니다.")
                runOnUiThread { toast("파일 목록 ${snapshot.size}개를 CSV로 저장했습니다.") }
            } catch (e: Exception) { runOnUiThread { toast("목록 저장 실패: ${e.message ?: "오류"}") } }
        }
    }
    private fun showOrganizationInsights() {
        val files = entries.filter { !it.directory }
        if (files.isEmpty()) { toast("먼저 저장공간을 검사하세요."); return }
        results.removeAllViews()
        addLine("파일 정리 그룹", true)
        addLine("그룹별 전체 선택·해제·일괄 이동을 지원합니다. 통화·음성 녹음은 보관 분류로 삭제 대상에서 제외합니다.")
        val now = System.currentTimeMillis()
        val duplicateGroups = files.groupBy { it.name.lowercase(java.util.Locale.ROOT) }.values.filter { it.size > 1 }
        val groups = linkedMapOf<String, List<Entry>>()
        if (duplicateGroups.isNotEmpty()) {
            addLine("중복 이름 검토 제안", true)
            addLine("파일명이 같다는 이유만으로 삭제하지 않습니다. 프로젝트·소스·휴지통·녹음 파일, 최신본, 크기가 다른 파일은 일괄 삭제 후보에서 제외합니다. 내용까지 같은지 확인하려면 SHA-256 검사를 사용하세요.")
            val selectedDuplicateCandidates = linkedMapOf<String, Entry>()
            val candidateCheckboxes = mutableListOf<Pair<CheckBox, Entry>>()
            val duplicateGroupsWithCandidates = duplicateGroups.map { sameName ->
                val newest = sameName.maxOfOrNull { it.modified } ?: 0L
                val keep = sameName.filter { it.modified == newest || it.modified <= 0L }
                val newestSize = sameName.filter { it.modified == newest && newest > 0L }.map { it.size }.distinct()
                val protectedByPath = sameName.filter { entry ->
                    val path = entry.relativePath.replace('\\', '/').lowercase(java.util.Locale.ROOT)
                    val name = entry.name.lowercase(java.util.Locale.ROOT)
                    isProtectedPath(entry) ||
                        path.contains("/.trash/") || path.contains("com.sec.android.app.myfiles") ||
                        path.contains("record") || path.contains("녹음") ||
                        path.contains("/src/") || path.contains("/assets/") || path.contains("/vendor/") ||
                        path.contains("/.git/") || path.contains("hazel zip extractor") ||
                        path.contains("hipdogsound") ||
                        name in setOf("readme.md", "cmakelists.txt", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", ".gitignore") ||
                        name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".cpp") || name.endsWith(".h") ||
                        name.endsWith(".ts") || name.endsWith(".js") || name.endsWith(".py") || name.endsWith(".task")
                }.map { it.uri.toString() }.toSet()
                val candidates = sameName.filter { entry ->
                    entry.modified > 0L && entry.modified < newest &&
                        newestSize.size == 1 && entry.size == newestSize.first() &&
                        entry.uri.toString() !in protectedByPath
                }
                Triple(sameName, keep, candidates)
            }
            val allSafeCandidates = duplicateGroupsWithCandidates.flatMap { it.third }.distinctBy { it.uri.toString() }
            val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            lateinit var globalSelect: CheckBox
            globalSelect = CheckBox(this).apply {
                text = "삭제 검토 가능한 항목 전체 선택 (${allSafeCandidates.size})"
                textSize = 14f; setTextColor(0xFF65734B.toInt()); isEnabled = allSafeCandidates.isNotEmpty()
                setOnCheckedChangeListener { _, checked ->
                    selectedDuplicateCandidates.clear()
                    if (checked) allSafeCandidates.forEach { selectedDuplicateCandidates[it.uri.toString()] = it }
                    candidateCheckboxes.forEach { (box, entry) ->
                        box.setOnCheckedChangeListener(null)
                        box.isChecked = checked
                        box.setOnCheckedChangeListener { _, value ->
                            if (value) selectedDuplicateCandidates[entry.uri.toString()] = entry
                            else selectedDuplicateCandidates.remove(entry.uri.toString())
                            globalSelect.isChecked = selectedDuplicateCandidates.size == allSafeCandidates.size && allSafeCandidates.isNotEmpty()
                        }
                    }
                }
            }
            val globalDelete = Button(this).apply {
                text = "선택 삭제"; isAllCaps = false; isEnabled = allSafeCandidates.isNotEmpty()
                setOnClickListener {
                    val chosen = selectedDuplicateCandidates.values.toList()
                    if (chosen.isEmpty()) toast("먼저 전체 선택 또는 개별 선택을 해주세요.")
                    else confirmDeleteGroup(chosen)
                }
            }
            toolbar.addView(globalSelect, LinearLayout.LayoutParams(0, -2, 1f))
            toolbar.addView(globalDelete)
            results.addView(toolbar)
            addLine("한 번의 삭제 확인으로 선택한 후보를 처리합니다. 선택 전에 그룹별 경로와 파일 크기를 확인하세요.")
            duplicateGroupsWithCandidates.forEachIndexed { index, triple ->
                val sameName = triple.first
                val keep = triple.second
                val candidates = triple.third
                val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 6, 8, 8); background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFF0EBDD.toInt()); cornerRadius = 14f * resources.displayMetrics.density } }
                val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
                val groupCheck = CheckBox(this).apply {
                    text = "이름 중복 ${index + 1}: ${sameName.first().name} · ${sameName.size}개 | 안전 후보 ${candidates.size}개"
                    textSize = 14f; setTextColor(0xFF65734B.toInt()); isEnabled = candidates.isNotEmpty()
                    setOnCheckedChangeListener { _, checked ->
                        candidates.forEach { entry ->
                            if (checked) selectedDuplicateCandidates[entry.uri.toString()] = entry
                            else selectedDuplicateCandidates.remove(entry.uri.toString())
                        }
                        candidateCheckboxes.filter { pair -> candidates.any { it.uri == pair.second.uri } }.forEach { (box, _) ->
                            box.setOnCheckedChangeListener(null); box.isChecked = checked
                            val entry = candidates.first { it.uri == box.tag }
                            box.setOnCheckedChangeListener { _, value ->
                                if (value) selectedDuplicateCandidates[entry.uri.toString()] = entry
                                else selectedDuplicateCandidates.remove(entry.uri.toString())
                                globalSelect.setOnCheckedChangeListener(null)
                                globalSelect.isChecked = selectedDuplicateCandidates.size == allSafeCandidates.size && allSafeCandidates.isNotEmpty()
                                globalSelect.setOnCheckedChangeListener { _, all ->
                                    selectedDuplicateCandidates.clear()
                                    if (all) allSafeCandidates.forEach { selectedDuplicateCandidates[it.uri.toString()] = it }
                                    candidateCheckboxes.forEach { (cb, item) -> cb.setOnCheckedChangeListener(null); cb.isChecked = all }
                                }
                            }
                        }
                        globalSelect.setOnCheckedChangeListener(null)
                        globalSelect.isChecked = selectedDuplicateCandidates.size == allSafeCandidates.size && allSafeCandidates.isNotEmpty()
                        globalSelect.setOnCheckedChangeListener { _, all ->
                            selectedDuplicateCandidates.clear()
                            if (all) allSafeCandidates.forEach { selectedDuplicateCandidates[it.uri.toString()] = it }
                            candidateCheckboxes.forEach { (cb, item) -> cb.setOnCheckedChangeListener(null); cb.isChecked = all }
                        }
                    }
                }
                val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                heading.addView(groupCheck, LinearLayout.LayoutParams(0, -2, 1f))
                heading.addView(Button(this).apply { text = "펼치기"; isAllCaps = false; setOnClickListener { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE } })
                body.addView(TextView(this).apply { text = "보존/날짜 미상: ${keep.size}개 · 안전 삭제 검토: ${candidates.size}개 · 전체 동일 크기 기준"; setTextColor(0xFF37362F.toInt()); setPadding(8, 6, 8, 6) })
                keep.forEach { entry -> body.addView(TextView(this).apply { text = "✓ 보존 · ${entry.relativePath} · ${humanSize(entry.size)} · ${formatDate(entry.modified)}"; setTextColor(0xFF65734B.toInt()); setPadding(8, 5, 8, 5) }) }
                sameName.filter { entry -> entry !in keep && entry !in candidates }.forEach { entry ->
                    body.addView(TextView(this).apply { text = "🔒 자동 선택 제외(경로/파일 크기 확인 필요) · ${entry.relativePath} · ${humanSize(entry.size)}"; setTextColor(0xFF8A6740.toInt()); textSize = 12f; setPadding(8, 5, 8, 5) })
                }
                candidates.forEach { entry ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                    val candidateBox = CheckBox(this).apply {
                        tag = entry.uri.toString()
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) selectedDuplicateCandidates[entry.uri.toString()] = entry
                            else selectedDuplicateCandidates.remove(entry.uri.toString())
                            globalSelect.setOnCheckedChangeListener(null)
                            globalSelect.isChecked = selectedDuplicateCandidates.size == allSafeCandidates.size && allSafeCandidates.isNotEmpty()
                            globalSelect.setOnCheckedChangeListener { _, all ->
                                selectedDuplicateCandidates.clear()
                                if (all) allSafeCandidates.forEach { selectedDuplicateCandidates[it.uri.toString()] = it }
                                candidateCheckboxes.forEach { (cb, item) -> cb.setOnCheckedChangeListener(null); cb.isChecked = all }
                            }
                        }
                    }
                    candidateCheckboxes.add(candidateBox to entry)
                    row.addView(candidateBox)
                    row.addView(TextView(this).apply { text = "삭제 검토 · ${entry.relativePath} · ${humanSize(entry.size)} · ${formatDate(entry.modified)}"; setTextColor(0xFF8A6740.toInt()); textSize = 12f; setPadding(6, 7, 4, 7) }, LinearLayout.LayoutParams(0, -2, 1f))
                    body.addView(row)
                }
                card.addView(heading); card.addView(body); results.addView(card)
            }
        }
        groups["대용량 파일"] = files.filter { it.size >= LARGE_FILE_BYTES }.sortedByDescending { it.size }
        val oldFiles = files.filter { it.modified > 0L && now - it.modified >= OLD_FILE_AGE }
        oldFiles.groupBy { classifyFile(it) }.toSortedMap().forEach { (type, list) -> groups["오래된 파일 · $type"] = list.sortedBy { it.modified } }
        groups["APK 설치 파일"] = files.filter { it.name.endsWith(".apk", true) }
        groups["ZIP 압축 파일"] = files.filter { it.name.endsWith(".zip", true) }
        groups.forEach { (title, groupFiles) ->
            if (groupFiles.isEmpty()) return@forEach
            val isRecording = title.contains("녹음")
            val movable = groupFiles.filter { !isProtectedPath(it) && !listOf(".apk", ".apks", ".aab", ".dex", ".so", ".jar", ".exe", ".dll").any { ext -> it.name.endsWith(ext, true) } }
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 6, 8, 8); background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFF0EBDD.toInt()); cornerRadius = 14f * resources.displayMetrics.density } }
            val header = TextView(this).apply { text = "▶ $title · ${groupFiles.size}개"; textSize = 15f; setTextColor(0xFF65734B.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(10, 12, 10, 12) }
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            val selected = linkedMapOf<String, Entry>()
            val boxes = linkedMapOf<String, CheckBox>()
            val count = TextView(this).apply { text = "선택 0개 / 이동 가능 ${movable.size}개"; textSize = 13f; setTextColor(0xFF37362F.toInt()); setPadding(8, 8, 8, 8) }
            fun refreshSelection() {
                count.text = "선택 ${selected.size}개 / 이동 가능 ${movable.size}개"
                boxes.forEach { (key, box) ->
                    box.setOnCheckedChangeListener(null); box.isChecked = selected.containsKey(key)
                    box.setOnCheckedChangeListener { _, checked -> val item = groupFiles.firstOrNull { it.uri.toString() == key }; if (item != null) { if (checked) selected[key] = item else selected.remove(key); count.text = "선택 ${selected.size}개 / 이동 가능 ${movable.size}개" } }
                }
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(Button(this).apply { text = "폴더 전체 선택"; isAllCaps = false; setOnClickListener { movable.forEach { selected[it.uri.toString()] = it }; refreshSelection() } }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply { text = "선택 해제"; isAllCaps = false; setOnClickListener { selected.clear(); refreshSelection() } }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(Button(this).apply { text = "폴더 전체 이동"; isAllCaps = false; isEnabled = movable.isNotEmpty(); setOnClickListener { if (movable.isEmpty()) toast("이동 가능한 파일이 없습니다.") else chooseBatchMoveDestination(movable, title.replace(Regex("[^가-힣A-Za-z0-9 _-]"), "").take(32)) } }, LinearLayout.LayoutParams(0, -2, 1f))
            body.addView(count); body.addView(actions)
            if (isRecording) body.addView(TextView(this).apply { text = "🔒 녹음 파일은 보관 권장 항목입니다. 일괄 삭제 버튼을 제공하지 않습니다."; setTextColor(0xFF8A6740.toInt()); setPadding(8, 6, 8, 6) })
            if (!isRecording) body.addView(Button(this).apply { text = "선택 파일 삭제"; isAllCaps = false; setOnClickListener { val chosen = selected.values.toList(); if (chosen.isEmpty()) toast("삭제할 파일을 선택하세요.") else confirmDeleteGroup(chosen) } })
            groupFiles.take(1500).forEach { entry ->
                val key = entry.uri.toString()
                val blocked = isProtectedPath(entry) || isRecording || listOf(".apk", ".apks", ".aab", ".dex", ".so", ".jar", ".exe", ".dll").any { entry.name.endsWith(it, true) }
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(4, 2, 4, 2) }
                val check = CheckBox(this).apply { isEnabled = !blocked; buttonTintList = android.content.res.ColorStateList.valueOf(if (blocked) 0xFF78889A.toInt() else 0xFF65734B.toInt()); setOnCheckedChangeListener { _, checked -> if (checked) selected[key] = entry else selected.remove(key); count.text = "선택 ${selected.size}개 / 이동 가능 ${movable.size}개" } }
                boxes[key] = check; row.addView(check)
                row.addView(TextView(this).apply { text = "${entry.relativePath} · ${humanSize(entry.size)} · ${formatDate(entry.modified)}"; textSize = 12f; setTextColor(0xFF37362F.toInt()); setPadding(6, 8, 4, 8); setOnClickListener { showFileActions(entry) } }, LinearLayout.LayoutParams(0, -2, 1f))
                body.addView(row)
            }
            if (groupFiles.size > 1500) body.addView(TextView(this).apply { text = "표시 제한: 1,500개"; setTextColor(0xFF37362F.toInt()) })
            header.setOnClickListener { val open = body.visibility != View.VISIBLE; body.visibility = if (open) View.VISIBLE else View.GONE; header.text = (if (open) "▼ " else "▶ ") + "$title · ${groupFiles.size}개" }
            card.addView(header); card.addView(body); results.addView(card)
        }
        summary.text = "파일 ${files.size}개 · 오래된 파일은 유형별 분리 · 통화/음성 녹음 보관 보호"
    }
    private fun confirmDeleteGroup(chosen: List<Entry>) {
        if (getSharedPreferences("quick_actions", MODE_PRIVATE).getBoolean("safe_mode", false)) { toast("안전 정리 모드가 켜져 있어 일괄 삭제가 차단되었습니다."); return }
        AlertDialog.Builder(this).setTitle("${chosen.size}개 파일 삭제")
            .setMessage("선택한 파일만 삭제합니다. 되돌릴 수 없습니다. 계속할까요?")
            .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                var success = 0
                chosen.forEach { entry -> if (try { deleteEntry(entry.uri) } catch (_: Exception) { false }) { success++; recordOperation("그룹 삭제", entry.relativePath, "삭제 완료") } }
                toast("${success}/${chosen.size}개 파일 삭제 완료")
                showOrganizationInsightsAfterDelete()
            }.show()
    }

    private fun showOrganizationInsightsAfterDelete() {
        if (selectedTrees.isNotEmpty() || selectedLooseFiles.isNotEmpty()) scanSelectedFolders() else showOrganizationInsights()
    }
    private fun addEntryLine(entry: Entry) {
        val line = TextView(this).apply {
            text = "• ${entry.relativePath} · ${humanSize(entry.size)}"
            textSize = 13f
            setPadding(8, 7, 4, 7)
            setOnClickListener { showFileActions(entry) }
        }
        results.addView(line)
    }

    private fun showFileActions(entry: Entry) {
        val options = arrayOf("파일 정보 보기", "이름 변경", "안전한 폴더로 이동·재분류", "삭제")
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this).setMessage(
                        "경로: ${entry.relativePath}\n크기: ${humanSize(entry.size)}\n수정: ${formatDate(entry.modified)}"
                    ).setPositiveButton("확인", null).show()
                    1 -> renameEntry(entry)
                    2 -> chooseMoveDestination(entry)
                    3 -> confirmDelete(entry)
                }
            }.setNegativeButton("닫기", null).show()
    }

    private fun renameEntry(entry: Entry) {
        val input = EditText(this).apply { setText(entry.name); selectAll() }
        AlertDialog.Builder(this).setTitle("새 파일 이름")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("변경") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName.contains("/") || newName == "." || newName == "..") {
                    toast("올바른 파일 이름을 입력하세요.")
                } else {
                    val ok = renameEntryUri(entry.uri, newName)
                    toast(if (ok) "이름을 변경했습니다. 다시 스캔해 주세요." else "이 위치에서는 이름 변경을 지원하지 않습니다.")
                    if (ok) recordOperation("이름 변경", entry.relativePath, newName)
                    if (ok) if (selectedTrees.isNotEmpty()) scanSelectedFolders()
                }
            }.show()
    }


    private fun chooseMoveDestination(entry: Entry) {
        if (isProtectedPath(entry)) {
            toast("경로 중간에 시스템·앱 데이터 보호 폴더가 있어 이동을 차단했습니다.")
            return
        }
        if (listOf(".apk", ".apks", ".aab", ".dex", ".so").any { entry.name.endsWith(it, true) }) {
            toast("설치·실행 관련 파일은 자동 재분류 이동 대상에서 제외합니다."); return
        }
        pendingMoveEntry = entry
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_MOVE_DESTINATION)
    }

    private fun chooseBatchMoveDestination(files: List<Entry>, groupFolderName: String? = null) {
        val safe = files.filter { !isProtectedPath(it) &&
            !listOf(".apk", ".apks", ".aab", ".dex", ".so", ".jar", ".exe", ".dll").any { ext -> it.name.endsWith(ext, true) } }
        if (safe.isEmpty()) { toast("안전하게 이동할 수 있는 파일이 없습니다."); return }
        val folderNote = groupFolderName?.let { "\\n선택한 목적지 안에 ‘$it’ 폴더를 만들어 파일을 함께 넣습니다." } ?: ""
        AlertDialog.Builder(this)
            .setTitle(if (groupFolderName == null) "파일 일괄 이동" else "프로젝트 묶음 이동")
            .setMessage("${safe.size}개 파일을 한 번에 복사하고 각각 크기를 검증합니다. 이름 충돌·검증 실패 파일은 건너뜁니다.$folderNote\\n원본은 복사 검증 후 이동 완료를 승인할 때만 삭제합니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("이동 위치 선택") { _, _ ->
                pendingMoveEntry = null
                pendingBatchMoveEntries = safe
                pendingBatchMoveFolderName = groupFolderName
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_MOVE_DESTINATION)
            }.show()
    }

    // Compare full file contents, not only byte counts, before offering source deletion.
    private fun sha256Uri(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = openEntryInputStream(uri) ?: throw IllegalStateException("파일을 읽을 수 없습니다.")
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyBatchThenOfferMove(files: List<Entry>, destinationUri: Uri, groupFolderName: String? = null) {
        if (pendingBatchMoveFolderName != null && groupFolderName == null) {
            copyWholeProjectPreservingTree(files, destinationUri, pendingBatchMoveFolderName!!)
            pendingBatchMoveFolderName = null
            pendingBatchMoveEntries = emptyList()
            return
        }
        val selectedDestination = DocumentFile.fromTreeUri(this, destinationUri)
        if (selectedDestination == null || !selectedDestination.isDirectory || !selectedDestination.canWrite()) {
            toast("선택한 목적지에 쓸 수 없습니다."); return
        }
        val destination = if (groupFolderName.isNullOrBlank()) selectedDestination else {
            selectedDestination.findFile(groupFolderName)?.takeIf { it.isDirectory }
                ?: selectedDestination.createDirectory(groupFolderName)
        }
        if (destination == null || !destination.isDirectory || !destination.canWrite()) {
            toast("프로젝트 폴더를 목적지에 만들 수 없습니다. 같은 이름의 파일·폴더가 있는지 확인하세요."); return
        }
        val destName = (destination.name ?: "").trim().lowercase(java.util.Locale.ROOT)
        val blockedNames = setOf("android", "data", "obb", "system", "vendor", "product", "apex", "windows", "program files")
        val uriText = try { Uri.decode(destinationUri.toString()).lowercase(java.util.Locale.ROOT) } catch (_: Exception) { "" }
        if (destName in blockedNames || listOf("/android/data", "/android/obb", "/system/", "/vendor/", "/product/", "/apex/").any { uriText.contains(it) }) {
            toast("보호 영역으로 판단되는 목적지라 일괄 복사를 차단했습니다."); return
        }
        setBusy(true, "분류 파일 ${files.size}개를 순서대로 복사·검증 중입니다…")
        worker.execute {
            val copied = mutableListOf<Pair<Entry, DocumentFile>>()
            val failed = mutableListOf<String>()
            for (entry in files) {
                var output: DocumentFile? = null
                try {
                    if (entry.name.isBlank() || entry.name == "." || entry.name == ".." || entry.name.contains('/'))
                        throw IllegalStateException("안전하지 않은 파일 이름")
                    output = destination.createFile(contentResolver.getType(entry.uri) ?: "application/octet-stream", entry.name)
                        ?: throw IllegalStateException("같은 이름이 있거나 생성 불가")
                    var bytes = 0L
                    openEntryInputStream(entry.uri).use { input ->
                        if (input == null) throw IllegalStateException("원본 읽기 실패")
                        contentResolver.openOutputStream(output.uri, "w").use { out ->
                            if (out == null) throw IllegalStateException("목적지 쓰기 실패")
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                bytes += n
                            }
                            out.flush()
                        }
                    }
                    if (bytes != entry.size || output.length() != entry.size)
                        throw IllegalStateException("크기 검증 실패")
                    if (sha256Uri(entry.uri) != sha256Uri(output.uri))
                        throw IllegalStateException("SHA-256 내용 검증 실패")
                    copied.add(entry to output)
                } catch (e: Exception) {
                    try { output?.delete() } catch (_: Exception) {}
                    failed.add("${entry.name}: ${e.localizedMessage ?: "복사 실패"}")
                }
            }
            runOnUiThread {
                setBusy(false)
                if (copied.isEmpty()) {
                    AlertDialog.Builder(this).setTitle("일괄 복사 결과")
                        .setMessage("검증에 성공한 파일이 없습니다. 실패 ${failed.size}개")
                        .setPositiveButton("확인", null).show()
                } else {
                    AlertDialog.Builder(this).setTitle("안전 이동 준비 완료")
                        .setMessage("성공 ${copied.size}개 · 실패 ${failed.size}개\n실패 예시: ${failed.take(5).joinToString("\\n")}\n\n검증에 성공한 ${copied.size}개 파일을 이동 완료할까요? 승인하면 해당 원본을 삭제합니다. 실패한 파일은 원본을 유지합니다.")
                        .setNegativeButton("복사본만 유지") { _, _ ->
                            copied.forEach { (src, dst) -> recordOperation("분류 일괄 복사", src.relativePath, dst.uri.toString()) }
                            toast("검증 성공 ${copied.size}개 복사본을 유지했습니다.")
                            if (selectedTrees.isNotEmpty()) scanSelectedFolders()
                        }
                        .setPositiveButton("원본 삭제하여 이동 완료") { _, _ ->
                            var deleted = 0
                            copied.forEach { (src, dst) ->
                                val ok = try { deleteEntry(src.uri) } catch (_: Exception) { false }
                                if (ok) { deleted++; recordOperation("분류 일괄 이동", src.relativePath, dst.uri.toString()) }
                            }
                            toast("원본 삭제 ${deleted}/${copied.size}개 · 나머지는 원본도 유지했습니다.")
                            if (selectedTrees.isNotEmpty()) scanSelectedFolders()
                        }.show()
                }
            }
        }
    }

    private fun copyThenOfferMove(entry: Entry, destinationUri: Uri) {
        val destination = DocumentFile.fromTreeUri(this, destinationUri)
        if (destination == null || !destination.isDirectory || !destination.canWrite()) {
            toast("선택한 목적지에 쓸 수 없습니다."); return
        }
        val destinationName = (destination.name ?: "").trim()
        val blockedDestinationNames = setOf("android", "data", "obb", "system", "vendor", "product", "apex", "windows", "program files")
        if (destinationName.lowercase(java.util.Locale.ROOT) in blockedDestinationNames) {
            toast("시스템·앱 데이터로 오인될 수 있는 목적지라 이동을 차단했습니다."); return
        }
        val destinationPath = try { Uri.decode(destinationUri.toString()).lowercase(java.util.Locale.ROOT) } catch (_: Exception) { "" }
        if (listOf("/android/data", "/android/obb", "/system/", "/vendor/", "/product/", "/apex/").any { destinationPath.contains(it) }) {
            toast("보호 영역으로 판단되는 목적지라 이동을 차단했습니다."); return
        }
        if (entry.name.isBlank() || entry.name == "." || entry.name == ".." || entry.name.contains('/')) {
            toast("안전하지 않은 파일 이름이어서 이동을 취소했습니다."); return
        }
        val output = try { destination.createFile(contentResolver.getType(entry.uri) ?: "application/octet-stream", entry.name) } catch (_: Exception) { null }
        if (output == null) { toast("목적지에 파일을 만들 수 없습니다. 같은 이름의 파일이 있는지 확인하세요."); return }
        setBusy(true, "파일을 복사하고 크기를 검증하는 중입니다…")
        worker.execute {
            var bytes = 0L
            try {
                openEntryInputStream(entry.uri).use { input ->
                    if (input == null) throw IllegalStateException("원본을 읽을 수 없습니다.")
                    contentResolver.openOutputStream(output.uri, "w").use { out ->
                        if (out == null) throw IllegalStateException("목적지에 쓸 수 없습니다.")
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            bytes += n
                        }
                        out.flush()
                    }
                }
                if (bytes != entry.size || output.length() != entry.size) throw IllegalStateException("원본과 복사본 크기가 일치하지 않습니다.")
                if (sha256Uri(entry.uri) != sha256Uri(output.uri)) throw IllegalStateException("SHA-256 내용 검증에 실패했습니다.")
            } catch (e: Exception) {
                try { output.delete() } catch (_: Exception) {}
                runOnUiThread { setBusy(false); toast("복사 실패: " + (e.localizedMessage ?: "파일 접근 오류")) }
                return@execute
            }
            runOnUiThread {
                setBusy(false)
                AlertDialog.Builder(this).setTitle("복사 및 검증 완료")
                    .setMessage(entry.name + "\n복사 크기: " + humanSize(bytes) + "\n\n원본을 삭제해 이동을 완료할까요? 원본 삭제는 사용자가 최종 승인해야 합니다.")
                    .setNegativeButton("복사본만 유지") { _, _ ->
                        recordOperation("재분류 복사", entry.relativePath, output.uri.toString())
                        toast("복사본을 저장했습니다. 원본은 유지했습니다.")
                        if (selectedTrees.isNotEmpty()) scanSelectedFolders()
                    }
                    .setPositiveButton("원본도 삭제하여 이동") { _, _ ->
                        val source = DocumentFile.fromSingleUri(this, entry.uri)
                        val deleted = try { source?.delete() == true } catch (_: Exception) { false }
                        if (deleted) {
                            recordOperation("안전 이동", entry.relativePath, output.uri.toString())
                            toast("검증 후 원본을 삭제해 이동했습니다.")
                        } else toast("복사본은 생성했지만 원본 삭제 권한이 없어 두 파일이 모두 남아 있습니다.")
                        if (selectedTrees.isNotEmpty()) scanSelectedFolders()
                    }.show()
            }
        }
    }

    private fun confirmDelete(entry: Entry) {
        if (getSharedPreferences("quick_actions", MODE_PRIVATE).getBoolean("safe_mode", false)) {
            toast("안전 정리 모드가 켜져 있어 개별 삭제도 차단되었습니다.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("파일 삭제 확인")
            .setMessage("${entry.relativePath}\n\n이 파일을 삭제할까요? 이 작업은 되돌릴 수 없을 수 있습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                val ok = deleteEntry(entry.uri)
                toast(if (ok) "파일을 삭제했습니다." else "삭제할 수 없습니다. 권한 또는 위치 제한을 확인하세요.")
                if (ok) recordOperation("삭제", entry.relativePath, "삭제 완료")
                if (ok && selectedTrees.isNotEmpty()) scanSelectedFolders()
            }.show()
    }

    private fun signerSha256(info: android.content.pm.PackageInfo): List<String> {
        if (android.os.Build.VERSION.SDK_INT < 28) return emptyList()
        return try {
            val signing = info.signingInfo ?: return emptyList()
            val signers = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            signers.map { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                    .joinToString(":") { "%02X".format(it) }
            }.sorted()
        } catch (_: Exception) { emptyList() }
    }

    private fun inspectApks() {
        val apks = entries.filter { !it.directory && it.name.endsWith(".apk", true) }
        setBusy(true, "APK 서명 지문·권한·설치 앱 버전을 대조 중입니다…")
        worker.execute {
            val reports = mutableListOf<String>()
            for (entry in apks) {
                var temp: File? = null
                try {
                    val tempFile = File.createTempFile("apk-inspect-", ".apk", cacheDir)
                    temp = tempFile
                    openEntryInputStream(entry.uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > 1024L * 1024L * 1024L) throw IllegalStateException("APK가 1GB 검사 한도를 초과했습니다.")
                                output.write(buffer, 0, n)
                            }
                        }
                    } ?: throw IllegalStateException("파일을 열 수 없음")
                    val flags = PackageManager.GET_PERMISSIONS or
                        (if (android.os.Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0)
                    @Suppress("DEPRECATION")
                    val info = packageManager.getPackageArchiveInfo(tempFile.absolutePath, flags)
                        ?: throw IllegalArgumentException("APK 매니페스트를 읽을 수 없음")
                    val label = try {
                        info.applicationInfo?.let { archiveApp ->
                            archiveApp.sourceDir = tempFile.absolutePath
                            archiveApp.publicSourceDir = tempFile.absolutePath
                            archiveApp.loadLabel(packageManager).toString()
                        } ?: entry.name
                    } catch (_: Exception) { entry.name }
                    val requested = info.requestedPermissions?.toList() ?: emptyList()
                    val dangerous = requested.filter { permission ->
                        try {
                            packageManager.getPermissionInfo(permission, 0).protectionLevel and
                                android.content.pm.PermissionInfo.PROTECTION_MASK_BASE ==
                                android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                        } catch (_: Exception) { false }
                    }
                    val sensitive = requested.filter { permission ->
                        listOf("SMS", "CONTACTS", "RECORD_AUDIO", "CAMERA", "LOCATION",
                            "ACCESSIBILITY", "INSTALL_PACKAGES", "QUERY_ALL_PACKAGES",
                            "SYSTEM_ALERT_WINDOW", "READ_CALL_LOG", "WRITE_CALL_LOG",
                            "READ_PHONE_STATE", "BODY_SENSORS").any { permission.contains(it) }
                    }
                    val installed = try {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(info.packageName, 0)
                    } catch (_: Exception) { null }
                    val archiveSigners = signerSha256(info)
                    val installedSigners = installed?.let { signerSha256(it) } ?: emptyList()
                    val signatureResult = when {
                        installed == null -> "동일 패키지 설치 앱 없음"
                        archiveSigners.isEmpty() || installedSigners.isEmpty() -> "서명 비교 불가 (지원/API 제한)"
                        archiveSigners == installedSigners -> "설치 앱과 서명 지문 일치"
                        else -> "주의: 설치 앱과 서명 지문 불일치"
                    }
                    val versionResult = when {
                        installed == null -> "설치 버전 비교 대상 없음"
                        info.longVersionCode == installed.longVersionCode -> "설치 버전과 동일"
                        info.longVersionCode > installed.longVersionCode -> "설치 버전보다 높은 APK 버전"
                        else -> "설치 버전보다 낮은 APK 버전"
                    }
                    reports.add(buildString {
                        append("APK: ${entry.relativePath}\n앱 이름: $label\n패키지: ${info.packageName}\n")
                        append("APK 버전: ${info.versionName ?: "알 수 없음"} (${info.longVersionCode})\n")
                        append("설치 상태: ${if (installed == null) "미설치" else "동일 패키지 설치됨"} · $versionResult\n")
                        append("서명 대조: $signatureResult\n")
                        if (archiveSigners.isNotEmpty()) append("APK 인증서 SHA-256: ${archiveSigners.joinToString("; ")}\n")
                        append("요청 권한: ${requested.size}개 · Android 위험 권한으로 확인된 항목: ${dangerous.size}개\n")
                        dangerous.take(30).forEach { append("  • 위험 권한: $it\n") }
                        if (sensitive.isNotEmpty()) {
                            append("추가 검토 권한 패턴 (${sensitive.size}개):\n")
                            sensitive.forEach { append("  • $it\n") }
                        }
                        if (installed != null && archiveSigners.isNotEmpty() && installedSigners.isNotEmpty() && archiveSigners != installedSigners)
                            append("경고: 같은 패키지명이라도 서명이 다르면 다른 배포자 또는 변조 가능성이 있습니다. 설치/덮어쓰기를 진행하지 마세요.\n")
                        append("판정 한계: 서명·버전·권한 메타데이터만 검사합니다. 코드 동작, 취약점, 악성 행위는 검사하지 않으며 안전 인증이 아닙니다.")
                    })
                } catch (e: Exception) {
                    reports.add("APK: ${entry.relativePath}\n분석 불가: ${e.message ?: "읽기 오류"}")
                } finally { try { temp?.delete() } catch (_: Exception) {} }
            }
            runOnUiThread {
                results.removeAllViews()
                if (reports.isEmpty()) addLine("선택 폴더에서 APK 파일을 찾지 못했습니다.")
                reports.forEachIndexed { i, report ->
                    addLine("APK 보안 점검 ${i + 1}", true)
                    addLine(report)
                }
                summary.text = "APK 서명·버전·위험 권한 대조 완료 · 악성코드 백신 검사나 안전 인증은 아닙니다."
                setBusy(false)
            }
        }
    }


    private fun inspectArchives() {
        val archives = entries.filter { !it.directory && it.name.endsWith(".zip", true) }
        setBusy(true, "ZIP 내부 파일 목록과 위험 신호를 읽는 중입니다…")
        worker.execute {
            val reports = mutableListOf<String>()
            for (entry in archives) {
                var count = 0
                var expanded = 0L
                var apkCount = 0
                var scriptCount = 0
                var executableCount = 0
                var suspiciousPaths = 0
                val samples = mutableListOf<String>()
                try {
                    openEntryInputStream(entry.uri)?.use { raw ->
                        ZipInputStream(raw.buffered()).use { zip ->
                            while (true) {
                                val item = zip.nextEntry ?: break
                                if (!item.isDirectory) {
                                    count++
                                    val lower = item.name.lowercase()
                                    if (lower.endsWith(".apk")) apkCount++
                                    if (ARCHIVE_SCRIPT_EXTENSIONS.any { lower.endsWith(it) }) scriptCount++
                                    if (ARCHIVE_EXECUTABLE_EXTENSIONS.any { lower.endsWith(it) }) executableCount++
                                    if (ARCHIVE_RISKY_PATH_MARKERS.any { lower.contains(it) }) suspiciousPaths++
                                    if (samples.size < 30) samples.add(item.name)
                                    val buffer = ByteArray(32 * 1024)
                                    while (true) {
                                        val n = zip.read(buffer)
                                        if (n < 0) break
                                        expanded += n
                                    }
                                }
                                zip.closeEntry()
                            }
                        }
                    } ?: throw IllegalStateException("파일을 열 수 없음")
                    reports.add(buildString {
                        append("ZIP: ${entry.relativePath}\n")
                        append("내부 파일: ${count}개 · 읽은 압축 해제 데이터: ${humanSize(expanded)}\n")
                        append("APK: ${apkCount}개 · 스크립트: ${scriptCount}개 · 실행 파일 형식: ${executableCount}개\n")
                        append("주의 경로명 패턴: ${suspiciousPaths}개\n내부 파일 예시:\n")
                        if (samples.isEmpty()) append("  (파일 없음)\n")
                        else samples.forEach { append("  • $it\n") }
                        if (count > samples.size) append("  … 나머지 ${count - samples.size}개 생략\n")
                        append("평가: 파일명·확장자 기반 정적 신호일 뿐, 악성 여부를 확정하지 않습니다. 압축파일은 실행하거나 추출하지 않았습니다.")
                    })
                } catch (e: Exception) {
                    reports.add("ZIP: ${entry.relativePath}\n분석 불가: ${e.message ?: "손상되었거나 지원하지 않는 압축 형식"}")
                }
            }
            runOnUiThread {
                results.removeAllViews()
                if (reports.isEmpty()) addLine("선택한 폴더에서 ZIP 파일을 찾지 못했습니다.")
                reports.forEachIndexed { i, report ->
                    addLine("압축파일 분석 ${i + 1}", true)
                    addLine(report)
                }
                summary.text = "ZIP 내부 구성 검사 완료 · 파일을 실행하거나 추출하지 않았습니다."
                setBusy(false)
            }
        }
    }

    private fun scanExactDuplicates() {
        val snapshot = entries.filter { !it.directory && it.size > 0L }
        if (snapshot.isEmpty()) { toast("먼저 저장공간을 검사하세요."); return }
        setBusy(true, "동일 크기 파일의 SHA-256을 비교 중입니다…")
        worker.execute {
            val buckets = snapshot.groupBy { it.size }.values.filter { it.size > 1 }
            val hashGroups = mutableMapOf<String, MutableList<Entry>>()
            var processed = 0
            for (bucket in buckets) for (entry in bucket) {
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    openEntryInputStream(entry.uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    } ?: continue
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    hashGroups.getOrPut(hash) { mutableListOf() }.add(entry)
                } catch (_: Exception) { }
                processed++
                if (processed % 100 == 0) {
                    val done = processed
                    runOnUiThread { summary.text = "SHA-256 검사 중 · $done / ${buckets.sumOf { it.size }}개" }
                }
            }
            val duplicateGroups = hashGroups.values.filter { it.size > 1 }
            val safeGroups = duplicateGroups.mapNotNull { group ->
                val protected = group.filter { entry ->
                    val path = entry.relativePath.replace('\\', '/').lowercase(java.util.Locale.ROOT)
                    val name = entry.name.lowercase(java.util.Locale.ROOT)
                    isProtectedPath(entry) ||
                        path.contains("/.trash/") || path.contains("com.sec.android.app.myfiles") ||
                        path.contains("record") || path.contains("녹음") ||
                        path.contains("/src/") || path.contains("/assets/") || path.contains("/vendor/") ||
                        path.contains("/.git/") || path.contains("hipdogsound") || path.contains("hazel zip extractor") ||
                        name.endsWith(".task") || name.endsWith(".kt") || name.endsWith(".java") ||
                        name.endsWith(".cpp") || name.endsWith(".h") || name.endsWith(".ts") ||
                        name.endsWith(".js") || name.endsWith(".py")
                }.map { it.uri.toString() }.toSet()
                val eligible = group.filter { it.uri.toString() !in protected }
                if (eligible.size < 2) null else {
                    val newest = eligible.maxOfOrNull { it.modified } ?: 0L
                    val keep = eligible.filter { it.modified == newest || it.modified <= 0L }
                    val remove = eligible.filter { it.modified > 0L && (newest <= 0L || it.modified < newest) }
                    if (remove.isEmpty()) null else Triple(group, keep, remove)
                }
            }
            runOnUiThread {
                results.removeAllViews()
                if (safeGroups.isEmpty()) {
                    summary.text = "SHA-256 완전 중복 검사 완료 · 안전 삭제 후보 없음"
                    addLine("내용이 완전히 같은 파일 중 자동 선택 가능한 안전 후보가 없습니다.")
                    addLine("보호 경로, 프로젝트 파일, 녹음 파일, 최신본, 날짜 미상 파일은 삭제 후보에서 제외했습니다.")
                } else {
                    val allCandidates = safeGroups.flatMap { it.third }.distinctBy { it.uri.toString() }
                    val selected = linkedMapOf<String, Entry>()
                    val rowBoxes = mutableListOf<Pair<CheckBox, Entry>>()
                    addLine("SHA-256 완전 중복 · ${safeGroups.size}개 그룹", true)
                    addLine("파일 내용이 완전히 같은 그룹만 표시합니다. 녹음·휴지통·프로젝트·소스 파일은 보호합니다.")
                    val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                    lateinit var allCheck: CheckBox
                    allCheck = CheckBox(this).apply {
                        text = "안전 삭제 후보 전체 선택 (${allCandidates.size})"
                        setTextColor(0xFF65734B.toInt())
                        setOnCheckedChangeListener { _, checked ->
                            selected.clear()
                            if (checked) allCandidates.forEach { selected[it.uri.toString()] = it }
                            rowBoxes.forEach { (box, entry) ->
                                box.setOnCheckedChangeListener(null); box.isChecked = checked
                                box.setOnCheckedChangeListener { _, value ->
                                    if (value) selected[entry.uri.toString()] = entry else selected.remove(entry.uri.toString())
                                }
                            }
                        }
                    }
                    bar.addView(allCheck, LinearLayout.LayoutParams(0, -2, 1f))
                    bar.addView(Button(this).apply {
                        text = "선택 삭제"; isAllCaps = false
                        setOnClickListener {
                            val chosen = selected.values.toList()
                            if (chosen.isEmpty()) toast("삭제할 안전 후보를 먼저 선택하세요.")
                            else confirmDeleteGroup(chosen)
                        }
                    })
                    results.addView(bar)
                    addLine("한 번의 확인으로 선택한 파일을 삭제합니다. 각 그룹에서 최신본은 보존 후보로 남깁니다.")
                    safeGroups.forEachIndexed { index, triple ->
                        val group = triple.first
                        val keep = triple.second
                        val remove = triple.third
                        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 6, 8, 8); background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFF0EBDD.toInt()); cornerRadius = 14f * resources.displayMetrics.density } }
                        val title = TextView(this).apply {
                            text = "중복 그룹 ${index + 1} · ${group.first().name} · ${group.size}개 · 삭제 후보 ${remove.size}개"
                            setTextColor(0xFF65734B.toInt()); textSize = 14f; setPadding(8, 10, 8, 10)
                        }
                        card.addView(title)
                        keep.forEach { entry -> card.addView(TextView(this).apply { text = "✓ 보존 · ${entry.relativePath} · ${humanSize(entry.size)}"; setTextColor(0xFF65734B.toInt()); setPadding(8, 4, 8, 4) }) }
                        remove.forEach { entry ->
                            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                            val box = CheckBox(this)
                            box.setOnCheckedChangeListener { _, checked ->
                                if (checked) selected[entry.uri.toString()] = entry else selected.remove(entry.uri.toString())
                                allCheck.setOnCheckedChangeListener(null)
                                allCheck.isChecked = selected.size == allCandidates.size && allCandidates.isNotEmpty()
                                allCheck.setOnCheckedChangeListener { _, all ->
                                    selected.clear()
                                    if (all) allCandidates.forEach { selected[it.uri.toString()] = it }
                                    rowBoxes.forEach { (cb, item) -> cb.setOnCheckedChangeListener(null); cb.isChecked = all }
                                }
                            }
                            rowBoxes.add(box to entry); row.addView(box)
                            row.addView(TextView(this).apply { text = "삭제 검토 · ${entry.relativePath} · ${humanSize(entry.size)}"; setTextColor(0xFF8A6740.toInt()); textSize = 12f; setPadding(5, 6, 4, 6) }, LinearLayout.LayoutParams(0, -2, 1f))
                            card.addView(row)
                        }
                        results.addView(card)
                    }
                    summary.text = "SHA-256 완전 중복 ${safeGroups.size}개 그룹 · 안전 삭제 후보 ${allCandidates.size}개"
                }
                setBusy(false)
            }
        }
    }

    private fun recordOperation(action: String, original: String, result: String) {
        val prefs = getSharedPreferences("operation_history", MODE_PRIVATE)
        val line = "${System.currentTimeMillis()}|${action}|${original}|${result}"
        val old = prefs.getString("records", "") ?: ""
        prefs.edit().putString("records", (old.lines().filter { it.isNotBlank() } + line).takeLast(200).joinToString("\n")).apply()
    }

    private fun showOperationHistory() {
        val records = getSharedPreferences("operation_history", MODE_PRIVATE)
            .getString("records", "")?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        results.removeAllViews()
        addLine("최근 파일 작업 기록 (최대 200건)", true)
        if (records.isEmpty()) addLine("기록된 파일 변경 작업이 없습니다.")
        records.asReversed().forEach { row ->
            val parts = row.split("|", limit = 4)
            if (parts.size == 4) addLine("${formatDate(parts[0].toLongOrNull() ?: 0L)} · ${parts[1]}\n${parts[2]} → ${parts[3]}")
        }
        summary.text = "파일 이름 변경·삭제 성공 기록 · 삭제 복구 기능은 아직 지원하지 않습니다."
    }

    private fun showLaunchableApps() {
        setBusy(true, "설치 앱을 불러오는 중…")
        worker.execute {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, 0)
                .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }.sortedBy { it.first.lowercase() }
            runOnUiThread {
                results.removeAllViews()
                val prefs = getSharedPreferences("app_categories", MODE_PRIVATE)
                val groups = apps.groupBy { (label, pkg) -> prefs.getString(pkg, null) ?: automaticAppCategory(label, pkg) }.toSortedMap()
                summary.text = "설치 앱 ${apps.size}개 · SmartOrganizer 내부 앱 폴더"
                addLine("앱 폴더 구성", true)
                addLine("앱을 체크하고 폴더 이름을 입력하면 이 앱 안에서 폴더별로 묶어 관리합니다.")
                addLine("삼성 One UI 홈 화면의 실제 폴더는 일반 앱이 직접 수정할 수 없어 자동 이동하지 않습니다.")
                results.addView(Button(this).apply {
                    text = "설치 앱 전체 목록 CSV 내보내기"
                    isAllCaps = false
                    setOnClickListener { exportInstalledAppsCsv() }
                })
                val selected = mutableSetOf<String>()
                val selectedCount = TextView(this).apply { text = "선택한 앱: 0개"; textSize = 14f; setTextColor(0xFF37362F.toInt()); setPadding(8, 8, 8, 8) }
                val folderName = EditText(this).apply { hint = "폴더 이름 (예: 쇼핑, 은행 및 카드)"; setSingleLine(true); setTextColor(0xFF172033.toInt()); setHintTextColor(0xFF68778C.toInt()) }
                results.addView(selectedCount)
                results.addView(folderName)
                results.addView(Button(this).apply { text = "선택한 앱을 폴더에 묶기"; isAllCaps = false; setOnClickListener {
                    val name = folderName.text.toString().trim()
                    if (selected.isEmpty()) { toast("먼저 앱을 선택하세요."); return@setOnClickListener }
                    if (name.isBlank()) { toast("폴더 이름을 입력하세요."); return@setOnClickListener }
                    val edit = prefs.edit(); selected.forEach { edit.putString(it, name) }; edit.apply()
                    recordOperation("앱 폴더 분류", selected.joinToString(","), name)
                    toast("${selected.size}개 앱을 ‘$name’ 폴더로 분류했습니다. 삼성 홈 화면에는 자동 반영되지 않습니다.")
                    showLaunchableApps()
                } })
                results.addView(Button(this).apply { text = "선택한 앱 자동 분류로 되돌리기"; isAllCaps = false; setOnClickListener {
                    if (selected.isEmpty()) { toast("먼저 앱을 선택하세요."); return@setOnClickListener }
                    val edit = prefs.edit(); selected.forEach { edit.remove(it) }; edit.apply(); showLaunchableApps()
                } })
                groups.forEach { (category, list) ->
                    val folderHeader = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(4, 4, 4, 4)
                    }
                    val categoryChecks = mutableMapOf<String, CheckBox>()
                    val folderCheck = CheckBox(this).apply {
                        text = "전체"
                        textSize = 12f
                        setTextColor(0xFF302F27.toInt())
                        minWidth = (64 * resources.displayMetrics.density).toInt()
                        buttonTintList = android.content.res.ColorStateList.valueOf(0xFF65734B.toInt())
                        contentDescription = "$category 폴더 하위 앱 전체 선택"
                        setOnCheckedChangeListener { _, checked ->
                            list.forEach { (_, pkg) -> if (checked) selected.add(pkg) else selected.remove(pkg) }
                            categoryChecks.forEach { (pkg, box) ->
                                box.setOnCheckedChangeListener(null)
                                box.isChecked = checked
                                box.setOnCheckedChangeListener { _, isChecked ->
                                    if (isChecked) selected.add(pkg) else selected.remove(pkg)
                                    selectedCount.text = "선택한 앱: ${selected.size}개"
                                }
                            }
                            selectedCount.text = "선택한 앱: ${selected.size}개"
                        }
                    }
                    folderHeader.addView(folderCheck)
                    val folderTitle = TextView(this).apply {
                        text = "📁 $category · ${list.size}개   ← 앱을 길게 눌러 여기로 드래그"
                        textSize = 15f; setTextColor(0xFF65734B.toInt())
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        setPadding(8, 12, 8, 12)
                    }
                    folderHeader.addView(folderTitle, LinearLayout.LayoutParams(0, -2, 1f))
                    val acceptDrop = android.view.View.OnDragListener { _, event ->
                        when (event.action) {
                            android.view.DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType("text/plain") == true
                            android.view.DragEvent.ACTION_DRAG_ENTERED -> { folderTitle.alpha = 0.55f; true }
                            android.view.DragEvent.ACTION_DRAG_EXITED -> { folderTitle.alpha = 1f; true }
                            android.view.DragEvent.ACTION_DROP -> {
                                folderTitle.alpha = 1f
                                val pkg = event.clipData?.getItemAt(0)?.text?.toString()
                                if (!pkg.isNullOrBlank() && apps.any { it.second == pkg }) {
                                    prefs.edit().putString(pkg, category).apply()
                                    recordOperation("앱 수동 폴더 이동", pkg, category)
                                    toast("앱을 ‘$category’ 폴더로 이동했습니다.")
                                    showLaunchableApps()
                                }
                                true
                            }
                            android.view.DragEvent.ACTION_DRAG_ENDED -> { folderTitle.alpha = 1f; true }
                            else -> true
                        }
                    }
                    folderHeader.setOnDragListener(acceptDrop)
                    folderTitle.setOnDragListener(acceptDrop)
                    results.addView(folderHeader)
                    list.forEach { (label, pkg) ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setPadding(4, 3, 4, 3)
                            setBackgroundColor(0xFF111D2E.toInt())
                        }
                        val appCheck = CheckBox(this).apply {
                            buttonTintList = android.content.res.ColorStateList.valueOf(0xFF65734B.toInt())
                            isChecked = selected.contains(pkg)
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selected.add(pkg) else selected.remove(pkg)
                                selectedCount.text = "선택한 앱: ${selected.size}개"
                            }
                        }
                        categoryChecks[pkg] = appCheck
                        row.addView(appCheck)
                        val appLabel = TextView(this).apply {
                            text = "⠿  📱 $label  (길게 눌러 이동)"
                            textSize = 14f; setTextColor(0xFF37362F.toInt())
                            setPadding(8, 10, 8, 10)
                            setOnClickListener { showAppDetails(label, pkg) }
                            setOnLongClickListener { view ->
                                val clip = android.content.ClipData.newPlainText("installed-app-package", pkg)
                                if (android.os.Build.VERSION.SDK_INT >= 24) {
                                    view.startDragAndDrop(clip, android.view.View.DragShadowBuilder(view), pkg, 0)
                                } else {
                                    @Suppress("DEPRECATION")
                                    view.startDrag(clip, android.view.View.DragShadowBuilder(view), pkg, 0)
                                }
                                true
                            }
                        }
                        row.addView(appLabel, LinearLayout.LayoutParams(0, -2, 1f))
                        results.addView(row)
                    }
                }
                setBusy(false)
            }
        }
    }

    private fun exportInstalledAppsCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "SmartOrganizer_설치앱목록.csv")
        }
        startActivityForResult(intent, REQUEST_EXPORT_APPS)
    }

    private fun exportInstalledAppsCsvTo(uri: Uri) {
        setBusy(true, "설치된 앱 목록을 CSV로 만드는 중…")
        worker.execute {
            try {
                val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val launchable = packageManager.queryIntentActivities(launchIntent, 0)
                    .map { it.activityInfo.packageName }.toSet()
                val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .sortedBy { runCatching { it.loadLabel(packageManager).toString().lowercase(java.util.Locale.ROOT) }.getOrDefault(it.packageName) }
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { out ->
                    out.write("앱 이름,패키지명,버전,자동 분류,시스템 앱,활성화,실행 아이콘\n")
                    fun csv(value: String) = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
                    installed.forEach { app ->
                        val label = runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(app.packageName)
                        val pkg = app.packageName
                        val version = runCatching {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(pkg, 0).versionName ?: ""
                        }.getOrDefault("")
                        val isSystem = app.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                        val enabled = app.enabled
                        val category = automaticAppCategory(label, pkg)
                        out.write(listOf(label, pkg, version, category, if (isSystem) "예" else "아니오", if (enabled) "예" else "아니오", if (launchable.contains(pkg)) "예" else "아니오").joinToString(",") { csv(it) })
                        out.write("\n")
                    }
                } ?: throw IllegalStateException("파일을 저장할 수 없습니다.")
                runOnUiThread { setBusy(false); toast("설치 앱 ${installed.size}개의 목록을 CSV로 저장했습니다.") }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false); toast("앱 목록 저장 실패: ${e.message ?: "오류"}") }
            }
        }
    }

    private fun automaticAppCategory(label: String, packageName: String): String {
        val l = label.lowercase(java.util.Locale.ROOT)
        val p = packageName.lowercase(java.util.Locale.ROOT)
        val key = "$l $p"
        fun has(vararg words: String) = words.any { key.contains(it) }
        // Exact package overrides are evaluated before broad keyword rules.
        return when {
            p in setOf("com.taobao.taobao", "com.einnovation.temu", "com.uxlayer.wipoint",
                "com.towneers.www", "kr.co.quicket", "com.coupang.mobile", "com.ssg",
                "com.gmarket.mobile", "com.auction.android", "com.musinsa.store",
                "com.kurly.mobile", "com.ohouse", "com.zigzag", "com.ably.ably",
                "com.cafe24.shopping", "com.happypointcard.happyorder") -> "쇼핑·커머스"
            p in setOf("com.sktelecom.tauth", "com.sktelecom.minit", "com.tms",
                "com.skt.t_smart_charge", "com.skt.prod.dialer", "com.skt.skaf.a000z00040",
                "com.skt.skaf.oa00018282", "com.skt.skaf.oa00199800", "com.skt.vcoloring") -> "통신사·멤버십"
            p in setOf("com.lgt.tmoney", "kr.co.tmoney.tia", "com.tmoney.manager",
                "com.kakao.taxi", "socar.socar", "kr.co.turu.parking",
                "kr.co.azsmart.apartner") -> "교통·모빌리티·생활"
            p in setOf("co.kr.catchtable.android.catchtable_app", "co.kr.waldlust.megacoffee",
                "com.starbucks.co", "com.happypointcard.happyorder", "com.daiso.mall") -> "쇼핑·외식·멤버십"
            p in setOf("com.shazam.android") -> "음악·오디오"
            p in setOf("com.hello.german") -> "교육·학습"
            p in setOf("com.danfoss.koolapp", "com.tuya.smart") -> "스마트홈·IoT"
            p in setOf("com.microsoft.teams") -> "업무·협업"
            p in setOf("com.rarlab.rar", "com.app.cq.ring", "org.rotomlauncher.mobile",
                "app.revanced.manager.flutter", "com.rokidbrew.phone") -> "유틸리티·시스템 도구"
            p in setOf("com.nhn.android.webtoon", "net.daum.android.webtoon") -> "웹툰·만화"
            p in setOf("com.soomgo") -> "생활 서비스·전문가 매칭"
            p in setOf("com.hdechome.myhills", "kr.co.azsmart.apartner") -> "주거·아파트"
            p in setOf("cheehoon.ha.particulateforecaster") -> "날씨·환경"
            p in setOf("com.ktcs.whowho") -> "전화·스팸 차단"
            p in setOf("com.tf.thinkdroid.viewer") -> "문서·PDF"
            p in setOf("org.openbor.engine", "com.risingfighter.app") -> "게임·엔터테인먼트"
            p.startsWith("com.android.") || p.startsWith("com.sec.android.") ||
                p.startsWith("com.samsung.android.") || p.startsWith("com.qualcomm.") ||
                p.startsWith("com.qti.") || p.startsWith("vendor.") -> "시스템·기기 기능"
            has("camera", "photo", "gallery", "갤러리", "사진", "카메라", "filmora", "clipdown", "studio", "vepreload") -> "사진·영상 편집"
            has("bank", "pay", "finance", "증권", "은행", "카드", "뱅킹", "토스", "업비트", "kb", "shinhan", "woori", "hana", "nh농협", "카카오페이") -> "은행 및 카드"
            has("shop", "shopping", "store", "market", "쇼핑", "쿠팡", "11번가", "지마켓", "옥션", "에이블리", "오늘의집", "컬리", "aliexpress", "무신사", "temu", "taobao", "淘宝", "다이소", "당근", "번개장터") -> "쇼핑·커머스"
            has("game", "게임", "play", "rising fighter", "gamebox", "gamesir", "kmplayer", "openbor") -> "게임·엔터테인먼트"
            has("mail", "calendar", "office", "문서", "메일", "캘린더", "업무", "word", "excel", "powerpoint", "pdf", "한글", "스프레드시트", "회사 포털", "stud", "github", "notion", "teams") -> "문서·업무"
            has("music", "audio", "video", "youtube", "음악", "동영상", "drama", "disney", "netflix", "wavve", "tving", "shazam") -> "음악·동영상"
            has("map", "nav", "지도", "내비", "교통", "지하철", "버스", "kakao.map", "tmap", "tmoney", "티머니", "쏘카", "socar", "주차", "parking") -> "지도·교통"
            has("health", "헬스", "건강", "fitness", "sleep", "수면", "의료", "병원", "med") -> "건강·의료"
            has("weather", "날씨", "기상", "미세미세", "particulate") -> "날씨·환경"
            has("book", "reader", "ebook", "독서", "도서", "교보", "밀리", "리디") -> "독서"
            has("webtoon", "웹툰", "만화") -> "웹툰·만화"
            has("chat", "talk", "messenger", "메신저", "카카오톡", "텔레그램", "whatsapp", "discord", "line") -> "메신저·소통"
            has("google", "chrome", "edge", "browser", "검색", "인터넷", "gemini", "chatgpt", "ai", "copilot") -> "인터넷·AI"
            has("government", "gov", "정부", "민원", "홈택스", "손택스", "위택스", "인증", "공동인증", "공공", "국세청") -> "공공·인증"
            has("samsung", "삼성", "smartthings", "galaxy", "oneui") -> "삼성 기본 앱"
            has("file", "explorer", "파일", "압축", "zip", "extractor", "4shared", "apk", "rar") -> "파일·도구"
            has("travel", "hotel", "airline", "여행", "항공", "숙박") -> "여행·이동"
            has("telecom", "membership", "t world", "t월드", "멤버십", "v컬러링", "pass", "통신사") -> "통신사·멤버십"
            has("restaurant", "cafe", "coffee", "카페", "맛집", "예약", "catchtable", "메가mgc", "스타벅스") -> "외식·예약·멤버십"
            has("soomgo", "숨고", "service", "전문가") -> "생활 서비스"
            has("smart home", "iot", "tuya", "rokid", "relay") -> "스마트홈·IoT"
            has("education", "learn", "study", "학습", "교육", "language", "독일어") -> "교육·학습"
            else -> "기타 (직접 확인)"
        }
    }

    private fun showAppDetails(label: String, packageName: String) {
        try {
            val info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val version = packageInfo.versionName ?: "알 수 없음"
            val requested = packageInfo.requestedPermissions?.toList() ?: emptyList()
            val dangerous = requested.filter { permission ->
                try {
                    packageManager.getPermissionInfo(permission, 0).protectionLevel and
                        android.content.pm.PermissionInfo.PROTECTION_MASK_BASE ==
                        android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                } catch (_: Exception) { false }
            }
            val custom = getSharedPreferences("app_categories", MODE_PRIVATE).getString(packageName, "자동 분류")
            val permissionSummary = if (dangerous.isEmpty()) "선언된 위험 권한 없음 또는 확인 불가" else
                dangerous.take(12).joinToString("\\n") { "• $it" } +
                    if (dangerous.size > 12) "\\n… 외 ${dangerous.size - 12}개" else ""
            AlertDialog.Builder(this).setTitle(label)
                .setMessage("패키지: $packageName\n버전: $version\n분류: $custom\n시스템 앱 여부: ${if (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) "시스템 앱" else "사용자 설치 앱"}\n요청 권한: ${requested.size}개 · 위험 권한 선언: ${dangerous.size}개\n$permissionSummary\n\n위 항목은 앱이 선언한 권한입니다. 실제 허용 상태는 Android 앱 정보에서 확인하세요.")
                .setPositiveButton("앱 정보·권한 열기") { _, _ ->
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")))
                    } catch (_: Exception) { toast("Android 앱 정보 화면을 열 수 없습니다.") }
                }
                .setNeutralButton("분류 변경") { _, _ -> editAppCategory(label, packageName) }
                .setNegativeButton("닫기", null).show()
        } catch (e: Exception) { toast("앱 정보를 읽지 못했습니다: ${e.message}") }
    }

    private fun editAppCategory(label: String, packageName: String) {
        val current = getSharedPreferences("app_categories", MODE_PRIVATE).getString(packageName, "기타") ?: "기타"
        val input = EditText(this).apply { setText(current); hint = "예: 업무, 금융, 자주 사용" }
        AlertDialog.Builder(this).setTitle("$label 분류 지정")
            .setMessage("원하는 분류명을 입력하세요.")
            .setView(input).setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    getSharedPreferences("app_categories", MODE_PRIVATE).edit().putString(packageName, value).apply()
                    toast("앱 분류를 저장했습니다.")
                    showLaunchableApps()
                }
            }.setNeutralButton("자동 분류로 초기화") { _, _ ->
                getSharedPreferences("app_categories", MODE_PRIVATE).edit().remove(packageName).apply()
                showLaunchableApps()
            }.show()
    }

    private fun confirmBulkDelete() {
        if (getSharedPreferences("quick_actions", MODE_PRIVATE).getBoolean("safe_mode", false)) {
            toast("빠른 설정의 '안전 정리 모드'가 켜져 있어 일괄 삭제가 차단되었습니다.")
            return
        }
        val chosen = entries.filter { checkedEntries.contains(it.uri.toString()) && !it.directory }
        if (chosen.isEmpty()) { toast("먼저 파일 왼쪽 체크박스를 선택하세요."); return }
        AlertDialog.Builder(this).setTitle("${chosen.size}개 파일 일괄 삭제")
            .setMessage("선택한 파일을 삭제합니다. 되돌릴 수 없습니다. 계속할까요?")
            .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                var success = 0
                chosen.forEach { entry ->
                    val ok = try { deleteEntry(entry.uri) } catch (_: Exception) { false }
                    if (ok) { success++; checkedEntries.remove(entry.uri.toString()); recordOperation("일괄 삭제", entry.relativePath, "삭제 완료") }
                }
                toast("$success/${chosen.size}개 파일을 삭제했습니다.")
                scanSelectedFolders()
            }.show()
    }

    private fun renderEntries(items: List<Entry>) {
        results.removeAllViews()
        if (items.isEmpty()) { addLine("선택한 위치에서 표시할 파일이나 폴더를 찾지 못했습니다."); return }
        items.take(3000).forEach { item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            if (!item.directory) {
                row.addView(CheckBox(this).apply {
                    isChecked = checkedEntries.contains(item.uri.toString())
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) checkedEntries.add(item.uri.toString()) else checkedEntries.remove(item.uri.toString())
                        bulkDeleteButton.isEnabled = checkedEntries.isNotEmpty()
                    }
                })
            }
            val detail = if (item.directory) "" else " · ${humanSize(item.size)}"
            val label = TextView(this).apply {
                text = (if (item.directory) "📁 " else "📄 ") + item.relativePath + detail
                textSize = 13f
                setTextColor(0xFF37362F.toInt())
                setPadding(8, 10, 8, 10)
                setOnClickListener { if (!item.directory) showFileActions(item) }
            }
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            results.addView(row)
        }
        bulkDeleteButton.isEnabled = checkedEntries.isNotEmpty()
        if (items.size > 3000) addLine("화면 표시 제한: ${items.size}개 중 처음 3,000개 표시")
    }

    private fun addLine(text: String, heading: Boolean = false) {
        results.addView(TextView(this).apply {
            this.text = text
            textSize = if (heading) 16f else 13f
            setTextColor(if (heading) 0xFF65734B.toInt() else 0xFF37362F.toInt())
            if (heading) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(if (heading) 0 else 8, if (heading) 14 else 9, 8, 9)
            if (!heading) {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF111D2E.toInt())
                    cornerRadius = 10f * resources.displayMetrics.density
                }
            }
        })
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        if (message != null) summary.text = message
        scanButton.isEnabled = !busy
        fullStorageScanButton.isEnabled = !busy
        runScanButton.isEnabled = !busy && (selectedTrees.isNotEmpty() || selectedLooseFiles.isNotEmpty())
        addFilesButton.isEnabled = !busy
        bulkDeleteButton.isEnabled = !busy && checkedEntries.isNotEmpty()
        manageFoldersButton.isEnabled = !busy
        duplicateButton.isEnabled = !busy && entries.any { !it.directory }
        insightButton.isEnabled = !busy && entries.any { !it.directory }
        classifyButton.isEnabled = !busy && entries.any { !it.directory }
        contentAuditButton.isEnabled = !busy && entries.any { !it.directory }
        apkButton.isEnabled = !busy && entries.any { !it.directory }
        archiveButton.isEnabled = !busy && entries.any { !it.directory && it.name.endsWith(".zip", true) }
        versionManagerButton.isEnabled = !busy && entries.any { !it.directory }
        exportListButton.isEnabled = !busy && entries.isNotEmpty()
        appsButton.isEnabled = !busy
    }

    private fun formatDate(time: Long): String =
        if (time <= 0L) "알 수 없음" else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        do { value /= 1024.0; index++ } while (value >= 1024 && index < units.lastIndex)
        return "${DecimalFormat("#,##0.##").format(value)} ${units[index]}"
    }

    override fun onDestroy() {
        windowInfoAdapter?.removeWindowLayoutInfoListener(foldLayoutListener)
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_MEDIA_PERMISSION = 7105
        private const val REQUEST_TREE = 7101
        private const val REQUEST_FILES = 7102
        private const val REQUEST_FULL_TREE = 7103
        private const val REQUEST_MOVE_DESTINATION = 7104
        private const val REQUEST_EXPORT_LIST = 7106
        private const val REQUEST_EXPORT_APPS = 7107
        private const val MAX_SCAN_ENTRIES = 100000
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
        private const val OLD_FILE_AGE = 180L * 24L * 60L * 60L * 1000L
        private val ARCHIVE_SCRIPT_EXTENSIONS = listOf(".sh", ".bat", ".cmd", ".ps1", ".js", ".vbs", ".py", ".jar")
        private val ARCHIVE_EXECUTABLE_EXTENSIONS = listOf(".exe", ".dll", ".so", ".dex", ".apk")
        private val ARCHIVE_RISKY_PATH_MARKERS = listOf("../", "/../", "autorun", "startup", "payload")
    }
}

