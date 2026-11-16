package com.example.smartorganizer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.documentfile.provider.DocumentFile
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

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val entries = mutableListOf<Entry>()
    private val selectedTrees = linkedMapOf<String, Uri>()
    private val selectedLooseFiles = linkedMapOf<String, Uri>()
    private val checkedEntries = linkedSetOf<String>()
    private lateinit var summary: TextView
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var runScanButton: Button
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
    private lateinit var historyButton: Button
    private lateinit var launcherButton: Button
    private lateinit var wallpaperButton: Button
    private var pendingMoveEntry: Entry? = null
    private var pendingBatchMoveEntries: List<Entry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSelectedTrees()
        restoreLooseFiles()
        buildUi()
        updateFolderSelectionUi()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 12)
        }
        val title = TextView(this).apply {
            text = "Smart Organizer"
            textSize = 26f
            setPadding(0, 0, 0, 5)
        }
        val subtitle = TextView(this).apply {
            text = "파일 분석 · 중복 탐지 · APK 권한 점검"
            textSize = 14f
        }
        summary = TextView(this).apply {
            text = "분석할 폴더를 선택하세요. 실제 파일만 검사하며 자동 삭제하지 않습니다."
            textSize = 14f
            setPadding(0, 14, 0, 10)
        }
        scanButton = Button(this).apply {
            text = "＋ 스캔할 폴더 추가"
            setOnClickListener { chooseFolder() }
            setOnLongClickListener { showSelectedFolders(); true }
        }
        fullStorageScanButton = Button(this).apply {
            text = "전체 저장소 검사 (내부 저장소 선택)"
            setOnClickListener { chooseFullStorageRoot() }
        }
        runScanButton = Button(this).apply {
            text = "선택한 폴더 모두 스캔 (0개)"
            isEnabled = false
            setOnClickListener { scanSelectedFolders() }
        }
        addFilesButton = Button(this).apply { text = "＋ 폴더 밖 개별 파일 여러 개 추가"; setOnClickListener { chooseFiles() } }
        bulkDeleteButton = Button(this).apply { text = "선택한 파일 일괄 삭제"; isEnabled = false; setOnClickListener { confirmBulkDelete() } }
        manageFoldersButton = Button(this).apply {
            text = "선택 폴더 관리"
            setOnClickListener { showSelectedFolders() }
        }
        duplicateButton = Button(this).apply {
            text = "완전 중복 파일 검사 (SHA-256)"
            isEnabled = false
            setOnClickListener { scanExactDuplicates() }
        }
        insightButton = Button(this).apply {
            text = "정리 후보 분석 (대용량·오래됨·이름 중복)"
            isEnabled = false
            setOnClickListener { showOrganizationInsights() }
        }
        classifyButton = Button(this).apply {
            text = "파일 유형 자동 분류·이동 추천"
            isEnabled = false
            setOnClickListener { showAutoFileClassification() }
        }
        contentAuditButton = Button(this).apply {
            text = "파일 내용 시그니처 검사"
            isEnabled = false
            setOnClickListener { auditFileSignatures() }
        }
        apkButton = Button(this).apply {
            text = "APK 권한·버전 검사"
            isEnabled = false
            setOnClickListener { inspectApks() }
        }
        archiveButton = Button(this).apply {
            text = "ZIP 압축파일 내부 구성 검사"
            isEnabled = false
            setOnClickListener { inspectArchives() }
        }
        historyButton = Button(this).apply {
            text = "파일 작업 기록"
            setOnClickListener { showOperationHistory() }
        }
        appsButton = Button(this).apply {
            text = "설치된 앱 목록 및 분류"
            setOnClickListener { showLaunchableApps() }
        }
        wallpaperButton = Button(this).apply {
            text = "월페이퍼 스튜디오 (사진·동영상)"
            setOnClickListener { startActivity(Intent(this@MainActivity, WallpaperStudioActivity::class.java)) }
        }
        val sailorMoonButton = Button(this).apply {
            text = "☾ 세일러문 Fold 테마 설정"
            setOnClickListener { startActivity(Intent(this@MainActivity, SailorMoonSetupActivity::class.java)) }
        }
        launcherButton = Button(this).apply {
            text = "사진 기반 나만의 홈 화면 열기"
            setOnClickListener { startActivity(Intent(this@MainActivity, LauncherActivity::class.java)) }
        }
        val cyberDashboardButton = Button(this).apply {
            text = "⚡ 사이버 포뮬러 대시보드"
            setOnClickListener { startActivity(Intent(this@MainActivity, CyberDashboardActivity::class.java)) }
        }
        val desktopStudioButton = Button(this).apply {
            text = "🖥 AI 데스크톱 스튜디오"
            setOnClickListener { startActivity(Intent(this@MainActivity, DesktopStudioActivity::class.java)) }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scanButton)
            addView(addFilesButton)
            addView(bulkDeleteButton)
            addView(fullStorageScanButton)
            addView(runScanButton)
            addView(Button(this@MainActivity).apply { text = "폴더별 용량 보기"; setOnClickListener { showFolderSizeReport() } })
            addView(manageFoldersButton)
            addView(duplicateButton)
            addView(insightButton)
            addView(classifyButton)
            addView(contentAuditButton)
            addView(apkButton)
            addView(archiveButton)
            addView(historyButton)
            addView(appsButton)
            addView(wallpaperButton)
            addView(launcherButton)
            addView(sailorMoonButton)
            addView(cyberDashboardButton)
            addView(desktopStudioButton)
        }
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        scroll.addView(results)
        outer.addView(title)
        outer.addView(subtitle)
        outer.addView(summary)
        outer.addView(actions)
        outer.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (280 * resources.displayMetrics.density).toInt()
        ))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(outer) })
    }

    private fun chooseFullStorageRoot() {
        AlertDialog.Builder(this)
            .setTitle("전체 저장소 검사")
            .setMessage("다음 화면에서 ‘내부 저장소’ 최상위 폴더를 선택하면 접근이 허용된 하위 폴더를 재귀 검사합니다. Android 보호 영역(예: Android/data)은 제외될 수 있습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("저장소 선택") { _, _ -> chooseFolder(REQUEST_FULL_TREE) }
            .show()
    }

    private fun chooseFolder(requestCode: Int = REQUEST_TREE) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
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
                    copyBatchThenOfferMove(batch, destinationUri)
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
        val ext = n.substringAfterLast('.', "")
        return when {
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw") -> "사진"
            ext in setOf("mp4", "m4v", "mov", "mkv", "avi", "webm", "3gp") -> "동영상"
            ext in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus") -> "음악"
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
            contentResolver.openInputStream(uri)?.use { input -> input.read(header) } ?: -1
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
        addLine("파일 유형 자동 분류 추천", true)
        addLine("확장자와 파일명 기반 추천입니다. 파일 내용 검증이나 악성코드 판정은 아닙니다.")
        addLine("보호 경로·설정 데이터·설치 파일은 이동 대상에서 제외하거나 별도 주의가 필요합니다.")
        groups.forEach { (category, group) ->
            addLine("$category · ${group.size}개", true)
            val movable = group.filter { entry ->
                !isProtectedPath(entry) && !category.contains("이동 제한") &&
                    !category.contains("이동 제외") &&
                    !listOf(".apk", ".apks", ".aab", ".dex", ".so", ".jar", ".exe", ".dll").any { entry.name.endsWith(it, true) }
            }
            if (movable.size > 1) {
                val batchButton = Button(this).apply {
                    text = "이 분류 ${movable.size}개 파일 일괄 복사·검증"
                    setOnClickListener { chooseBatchMoveDestination(movable) }
                }
                results.addView(batchButton)
            }
            group.take(200).forEach { entry ->
                val blocked = isProtectedPath(entry) ||
                    category.contains("이동 제한") || category.contains("이동 제외")
                val row = TextView(this).apply {
                    text = (if (blocked) "🔒 " else "📄 ") + entry.relativePath + " · " + humanSize(entry.size)
                    textSize = 13f
                    setPadding(8, 7, 4, 7)
                    if (!blocked) setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("분류 이동")
                            .setMessage("추천 분류: $category\n원본: ${entry.relativePath}\n\n목적지 폴더를 선택하면 먼저 복사·크기를 검증합니다. 원본 삭제는 별도 승인 전까지 하지 않습니다.")
                            .setNegativeButton("취소", null)
                            .setPositiveButton("목적지 선택") { _, _ -> chooseMoveDestination(entry) }
                            .show()
                    }
                }
                results.addView(row)
            }
            if (group.size > 200) addLine("화면 표시 제한: 나머지 ${group.size - 200}개 생략")
        }
        summary.text = "파일 분류 추천 완료 · ${files.size}개 · 자동 이동/삭제 없음"
    }

    private fun showOrganizationInsights() {
        val files = entries.filter { !it.directory }
        if (files.isEmpty()) { toast("먼저 저장소를 스캔하세요."); return }
        val now = System.currentTimeMillis()
        val large = files.filter { it.size >= LARGE_FILE_BYTES }.sortedByDescending { it.size }.take(30)
        val old = files.filter { it.modified > 0L && now - it.modified >= OLD_FILE_AGE }.sortedBy { it.modified }.take(30)
        val duplicateNames = files.groupBy { it.name.lowercase(java.util.Locale.ROOT) }
            .filterValues { it.size > 1 }
        results.removeAllViews()
        addLine("정리 후보 분석", true)
        addLine("대용량 파일 (100MB 이상) · ${large.size}개", true)
        if (large.isEmpty()) addLine("해당 파일이 없습니다.")
        large.forEach { addLine("${it.relativePath} · ${humanSize(it.size)}") }
        addLine("오래된 파일 (180일 이상) · ${old.size}개", true)
        if (old.isEmpty()) addLine("해당 파일이 없습니다.")
        old.forEach { addLine("${it.relativePath} · ${formatDate(it.modified)}") }
        addLine("이름이 같은 파일 그룹 · ${duplicateNames.size}개", true)
        duplicateNames.entries.sortedByDescending { it.value.size }.take(50).forEach { (name, group) ->
            addLine("$name · ${group.size}개")
            group.take(5).forEach { addLine("  ${it.relativePath}") }
        }
        summary.text = "정리 후보 분석 완료 · 삭제는 사용자가 파일별로 직접 선택해야 합니다."
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
                    val doc = DocumentFile.fromSingleUri(this, entry.uri)
                    val ok = try { doc?.renameTo(newName) == true } catch (_: Exception) { false }
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

    private fun chooseBatchMoveDestination(files: List<Entry>) {
        val safe = files.filter { !isProtectedPath(it) &&
            !listOf(".apk", ".apks", ".aab", ".dex", ".so", ".jar", ".exe", ".dll").any { ext -> it.name.endsWith(ext, true) } }
        if (safe.isEmpty()) { toast("안전하게 이동할 수 있는 파일이 없습니다."); return }
        AlertDialog.Builder(this)
            .setTitle("분류 일괄 복사")
            .setMessage("${safe.size}개 파일을 선택한 폴더로 복사하고 각각 크기를 검증합니다. 이름 충돌·검증 실패 파일은 건너뜁니다. 원본 삭제는 복사 검증 후 별도 승인받습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("목적지 선택") { _, _ ->
                pendingMoveEntry = null
                pendingBatchMoveEntries = safe
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_MOVE_DESTINATION)
            }.show()
    }

    private fun copyBatchThenOfferMove(files: List<Entry>, destinationUri: Uri) {
        val destination = DocumentFile.fromTreeUri(this, destinationUri)
        if (destination == null || !destination.isDirectory || !destination.canWrite()) {
            toast("선택한 목적지에 쓸 수 없습니다."); return
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
                    contentResolver.openInputStream(entry.uri).use { input ->
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
                    AlertDialog.Builder(this).setTitle("일괄 복사·검증 완료")
                        .setMessage("성공 ${copied.size}개 · 실패 ${failed.size}개\n실패 예시: ${failed.take(5).joinToString("\\n")}\n\n검증 성공한 원본을 삭제해 이동을 완료할까요? 실패한 파일은 원본을 유지합니다.")
                        .setNegativeButton("복사본만 유지") { _, _ ->
                            copied.forEach { (src, dst) -> recordOperation("분류 일괄 복사", src.relativePath, dst.uri.toString()) }
                            toast("검증 성공 ${copied.size}개 복사본을 유지했습니다.")
                            if (selectedTrees.isNotEmpty()) scanSelectedFolders()
                        }
                        .setPositiveButton("검증 성공 원본 삭제") { _, _ ->
                            var deleted = 0
                            copied.forEach { (src, dst) ->
                                val ok = try { DocumentFile.fromSingleUri(this, src.uri)?.delete() == true } catch (_: Exception) { false }
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
                contentResolver.openInputStream(entry.uri).use { input ->
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
                val doc = DocumentFile.fromSingleUri(this, entry.uri)
                val ok = try { doc?.delete() == true } catch (_: Exception) { false }
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
                    contentResolver.openInputStream(entry.uri)?.use { input ->
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
                    contentResolver.openInputStream(entry.uri)?.use { raw ->
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
        if (entries.none { !it.directory }) return
        val snapshot = entries.toList()
        setBusy(true, "크기가 같은 파일 후보를 SHA-256으로 비교 중입니다…")
        worker.execute {
            val candidates = snapshot.filter { !it.directory && it.size > 0L }
                .groupBy { it.size }.values.filter { it.size > 1 }
            val groups = mutableMapOf<String, MutableList<Entry>>()
            for (bucket in candidates) for (entry in bucket) {
                try {
                    val md = MessageDigest.getInstance("SHA-256")
                    contentResolver.openInputStream(entry.uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            md.update(buffer, 0, count)
                        }
                    } ?: continue
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    groups.getOrPut(hash) { mutableListOf() }.add(entry)
                } catch (_: Exception) {}
            }
            val duplicates = groups.values.filter { it.size > 1 }
            runOnUiThread {
                results.removeAllViews()
                if (duplicates.isEmpty()) {
                    summary.text = "검사 가능한 파일에서 동일 내용의 중복 파일을 찾지 못했습니다."
                    addLine("완전 중복 파일 없음")
                } else {
                    summary.text = "내용이 완전히 같은 중복 파일 그룹 ${duplicates.size}개 · 자동 삭제하지 않았습니다."
                    duplicates.forEachIndexed { index, group ->
                        addLine("중복 그룹 ${index + 1}", true)
                        group.forEach { addEntryLine(it) }
                    }
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
        setBusy(true, "실행 가능한 설치 앱을 조회 중입니다…")
        worker.execute {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(intent, 0)
                .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
            runOnUiThread {
                results.removeAllViews()
                summary.text = "실행 가능한 설치 앱 ${apps.size}개 · 자동 변경 없음"
                val categories = apps.groupBy { (label, pkg) ->
                    val key = (label + " " + pkg).lowercase()
                    when {
                        listOf("camera", "photo", "gallery", "카메라", "갤러리", "사진").any { key.contains(it) } -> "사진·카메라"
                        listOf("game", "play", "게임").any { key.contains(it) } -> "게임·엔터테인먼트"
                        listOf("mail", "calendar", "office", "문서", "메일", "캘린더").any { key.contains(it) } -> "업무·생산성"
                        listOf("music", "audio", "video", "youtube", "음악", "동영상").any { key.contains(it) } -> "음악·동영상"
                        listOf("map", "nav", "지도", "내비").any { key.contains(it) } -> "지도·이동"
                        else -> "기타"
                    }
                }
                categories.toSortedMap().forEach { (category, list) ->
                    addLine("$category (${list.size})", true)
                    list.forEach { (label, pkg) ->
                        val line = TextView(this).apply {
                            val custom = getSharedPreferences("app_categories", MODE_PRIVATE).getString(pkg, null)
                            text = "📱 $label · $pkg" + if (custom != null) "  [$custom]" else ""
                            textSize = 13f
                            setPadding(8, 8, 4, 8)
                            setOnClickListener { showAppDetails(label, pkg) }
                            setOnLongClickListener { editAppCategory(label, pkg); true }
                        }
                        results.addView(line)
                    }
                }
                setBusy(false)
            }
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
                    val ok = try { DocumentFile.fromSingleUri(this, entry.uri)?.delete() == true } catch (_: Exception) { false }
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
                textSize = 13f; setPadding(8, 7, 4, 7)
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
            setPadding(if (heading) 0 else 4, if (heading) 14 else 6, 4, 6)
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
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_TREE = 7101
        private const val REQUEST_FILES = 7102
        private const val REQUEST_FULL_TREE = 7103
        private const val REQUEST_MOVE_DESTINATION = 7104
        private const val MAX_SCAN_ENTRIES = 100000
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
        private const val OLD_FILE_AGE = 180L * 24L * 60L * 60L * 1000L
        private val ARCHIVE_SCRIPT_EXTENSIONS = listOf(".sh", ".bat", ".cmd", ".ps1", ".js", ".vbs", ".py", ".jar")
        private val ARCHIVE_EXECUTABLE_EXTENSIONS = listOf(".exe", ".dll", ".so", ".dex", ".apk")
        private val ARCHIVE_RISKY_PATH_MARKERS = listOf("../", "/../", "autorun", "startup", "payload")
    }
}
