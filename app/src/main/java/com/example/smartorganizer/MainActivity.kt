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
    private lateinit var summary: TextView
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var runScanButton: Button
    private lateinit var manageFoldersButton: Button
    private lateinit var duplicateButton: Button
    private lateinit var insightButton: Button
    private lateinit var apkButton: Button
    private lateinit var appsButton: Button
    private lateinit var archiveButton: Button
    private lateinit var historyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSelectedTrees()
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
        runScanButton = Button(this).apply {
            text = "선택한 폴더 모두 스캔 (0개)"
            isEnabled = false
            setOnClickListener { scanSelectedFolders() }
        }
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
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scanButton)
            addView(runScanButton)
            addView(manageFoldersButton)
            addView(duplicateButton)
            addView(insightButton)
            addView(apkButton)
            addView(archiveButton)
            addView(historyButton)
            addView(appsButton)
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
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(outer)
    }

    private fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_TREE)
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_TREE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
        selectedTrees[uri.toString()] = uri
        saveSelectedTrees()
        updateFolderSelectionUi()
        toast("폴더를 추가했습니다. 다른 폴더도 추가한 뒤 전체 스캔을 누르세요.")
    }

    private fun saveSelectedTrees() {
        getSharedPreferences("scan_folders", MODE_PRIVATE).edit()
            .putStringSet("uris", selectedTrees.keys.toSet()).apply()
    }

    private fun restoreSelectedTrees() {
        val saved = getSharedPreferences("scan_folders", MODE_PRIVATE)
            .getStringSet("uris", emptySet()) ?: emptySet()
        saved.forEach { value ->
            val uri = Uri.parse(value)
            val permission = contentResolver.persistedUriPermissions
                .firstOrNull { it.uri == uri && it.isReadPermission }
            if (permission != null) selectedTrees[value] = uri
        }
    }

    private fun updateFolderSelectionUi() {
        runScanButton.text = "선택한 폴더 모두 스캔 (${selectedTrees.size}개)"
        runScanButton.isEnabled = selectedTrees.isNotEmpty()
        manageFoldersButton.isEnabled = true
        summary.text = "선택된 폴더 ${selectedTrees.size}개 · 폴더 추가 버튼을 길게 누르면 목록/해제 관리"
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
        if (roots.isEmpty()) { toast("먼저 폴더를 추가하세요."); return }
        setBusy(true, "선택한 폴더 ${roots.size}개와 하위 폴더를 스캔 중입니다…")
        worker.execute {
            val found = mutableListOf<Entry>()
            var inaccessible = 0
            roots.forEachIndexed { index, treeUri ->
                val root = DocumentFile.fromTreeUri(this, treeUri)
                val rootName = root?.name ?: "폴더 ${index + 1}"
                fun walk(dir: DocumentFile, parent: String) {
                    val children = try { dir.listFiles() } catch (_: Exception) { inaccessible++; emptyArray() }
                    for (child in children) {
                        val name = child.name ?: "(이름 없음)"
                        val path = if (parent.isEmpty()) "$rootName/$name" else "$rootName/$parent/$name"
                        val isDir = child.isDirectory
                        found.add(Entry(name, path, if (child.isFile) child.length() else 0L, child.lastModified(), child.uri, isDir))
                        if (isDir) walk(child, if (parent.isEmpty()) name else "$parent/$name")
                    }
                }
                if (root != null) walk(root, "") else inaccessible++
            }
            runOnUiThread {
                entries.clear(); entries.addAll(found.distinctBy { it.uri.toString() })
                val files = entries.filter { !it.directory }
                summary.text = "전체 스캔 완료: 선택 폴더 ${roots.size}개 · 파일 ${files.size}개 · 하위 폴더 ${entries.count { it.directory }}개 · ${humanSize(files.sumOf { it.size })}" + if (inaccessible > 0) "\n일부 위치는 Android 권한 제한으로 읽지 못했습니다." else ""
                duplicateButton.isEnabled = files.isNotEmpty(); insightButton.isEnabled = files.isNotEmpty(); apkButton.isEnabled = files.isNotEmpty()
                archiveButton.isEnabled = files.any { it.name.endsWith(".zip", true) }
                renderEntries(entries); setBusy(false)
            }
        }
    }

    private fun showOrganizationInsights() {
        val snapshot = entries.filter { !it.directory }
        setBusy(true, "대용량·오래된 파일·이름 중복 후보를 계산 중입니다…")
        worker.execute {
            val now = System.currentTimeMillis()
            val large = snapshot.filter { it.size >= LARGE_FILE_BYTES }.sortedByDescending { it.size }
            val old = snapshot.filter { it.modified > 0L && now - it.modified >= OLD_FILE_AGE }
                .sortedBy { it.modified }
            val sameNames = snapshot.groupBy { it.name.lowercase() }.values.filter { it.size > 1 }
            val apk = snapshot.filter { it.name.endsWith(".apk", true) }
            fun category(entry: Entry): String {
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                return when (ext) {
                    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> "사진"
                    "mp4", "mkv", "mov", "avi", "webm", "3gp" -> "동영상"
                    "mp3", "m4a", "wav", "flac", "aac", "ogg" -> "음악·오디오"
                    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "hwp", "hwpx" -> "문서"
                    "zip", "7z", "rar", "tar", "gz" -> "압축파일"
                    "apk", "xapk", "apks" -> "설치파일"
                    else -> "기타"
                }
            }
            val categories = snapshot.groupBy { category(it) }
            runOnUiThread {
                results.removeAllViews()
                addLine("정리 후보 분석 결과", true)
                addLine("파일 종류별 분류", true)
                categories.toSortedMap().forEach { (name, files) ->
                    val total = files.sumOf { it.size }
                    addLine("$name: ${files.size}개 · ${humanSize(total)}")
                }
                addLine("대용량 파일 (100MB 이상): ${large.size}개")
                large.take(100).forEach { addEntryLine(it) }
                addLine("오래된 파일 (180일 이상): ${old.size}개", true)
                old.take(100).forEach { addEntryLine(it) }
                addLine("이름이 같은 파일 그룹: ${sameNames.size}개", true)
                sameNames.take(100).forEachIndexed { i, group ->
                    addLine("그룹 ${i + 1}")
                    group.forEach { addEntryLine(it) }
                }
                addLine("APK 파일: ${apk.size}개", true)
                apk.forEach { addEntryLine(it) }
                addLine("주의: 오래됨·이름 중복은 삭제 근거가 아닙니다. 항목을 눌러 개별 작업을 선택하세요.")
                summary.text = "정리 후보 분석 완료 · 아무 파일도 자동 변경하거나 삭제하지 않았습니다."
                setBusy(false)
            }
        }
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
        val options = arrayOf("파일 정보 보기", "이름 변경", "삭제")
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AlertDialog.Builder(this).setMessage(
                        "경로: ${entry.relativePath}\n크기: ${humanSize(entry.size)}\n수정: ${formatDate(entry.modified)}"
                    ).setPositiveButton("확인", null).show()
                    1 -> renameEntry(entry)
                    2 -> confirmDelete(entry)
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

    private fun confirmDelete(entry: Entry) {
        AlertDialog.Builder(this)
            .setTitle("파일 삭제 확인")
            .setMessage("${entry.relativePath}\n\n이 파일을 삭제할까요? 이 작업은 되돌릴 수 없을 수 있습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                val doc = DocumentFile.fromSingleUri(this, entry.uri)
                val ok = try { doc?.delete() == true } catch (_: Exception) { false }
                toast(if (ok) "파일을 삭제했습니다." else "삭제할 수 없습니다. 권한 또는 위치 제한을 확인하세요.")
                if (ok) recordOperation("삭제", entry.relativePath, "삭제 완료")
                if (ok) selectedTree?.let { scanFolder(it) }
            }.show()
    }

    private fun inspectApks() {
        val apks = entries.filter { !it.directory && it.name.endsWith(".apk", true) }
        setBusy(true, "APK 파일을 임시 사본으로 읽어 패키지·버전·요청 권한을 확인 중입니다…")
        worker.execute {
            val reports = mutableListOf<String>()
            for (entry in apks) {
                var temp: File? = null
                try {
                    val tempFile = File.createTempFile("apk-inspect-", ".apk", cacheDir)
                    temp = tempFile
                    contentResolver.openInputStream(entry.uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("파일을 열 수 없음")
                    @Suppress("DEPRECATION")
                    val info = packageManager.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)
                        ?: throw IllegalArgumentException("APK 매니페스트를 읽을 수 없음")
                    val label = try {
                        info.applicationInfo?.let { archiveApp ->
                            archiveApp.sourceDir = tempFile.absolutePath
                            archiveApp.publicSourceDir = tempFile.absolutePath
                            archiveApp.loadLabel(packageManager).toString()
                        } ?: entry.name
                    } catch (_: Exception) { entry.name }
                    val requested = info.requestedPermissions?.toList() ?: emptyList()
                    val sensitive = requested.filter { permission ->
                        permission.contains("SMS") || permission.contains("CONTACTS") ||
                                permission.contains("RECORD_AUDIO") || permission.contains("CAMERA") ||
                                permission.contains("LOCATION") || permission.contains("ACCESSIBILITY") ||
                                permission.contains("INSTALL_PACKAGES") || permission.contains("QUERY_ALL_PACKAGES") ||
                                permission.contains("SYSTEM_ALERT_WINDOW")
                    }
                    reports.add(buildString {
                        append("APK: ${entry.relativePath}\n")
                        append("앱 이름: $label\n패키지: ${info.packageName}\n")
                        append("버전명: ${info.versionName ?: "알 수 없음"} · 버전코드: ${info.longVersionCode}\n")
                        append("요청 권한: ${requested.size}개\n")
                        if (sensitive.isEmpty()) append("주의 권한으로 분류한 항목 없음\n")
                        else {
                            append("검토할 권한 (${sensitive.size}개):\n")
                            sensitive.forEach { append("  • $it\n") }
                        }
                        append("평가: 권한은 기능상 필요할 수도 있으며, 이 결과만으로 악성 여부를 판정하지 않습니다.")
                    })
                } catch (e: Exception) {
                    reports.add("APK: ${entry.relativePath}\n분석 불가: ${e.message ?: "읽기 오류"}")
                } finally { try { temp?.delete() } catch (_: Exception) {} }
            }
            runOnUiThread {
                results.removeAllViews()
                if (reports.isEmpty()) addLine("선택 폴더에서 APK 파일을 찾지 못했습니다.")
                reports.forEachIndexed { i, report ->
                    addLine("APK 분석 ${i + 1}", true)
                    addLine(report)
                }
                summary.text = "APK 메타데이터 검사 완료 · 정적 권한 점검이며 백신 검사나 악성코드 확정 판정은 아닙니다."
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
        if (selectedTrees.isEmpty()) return
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
        prefs.edit().putString("records", (old.lines().filter { it.isNotBlank() } + line).takeLast(200).joinToString("\\n")).apply()
    }

    private fun showOperationHistory() {
        val records = getSharedPreferences("operation_history", MODE_PRIVATE)
            .getString("records", "")?.lines()?.filter { it.isNotBlank() } ?: emptyList()
        results.removeAllViews()
        addLine("최근 파일 작업 기록 (최대 200건)", true)
        if (records.isEmpty()) addLine("기록된 파일 변경 작업이 없습니다.")
        records.asReversed().forEach { row ->
            val parts = row.split("|", limit = 4)
            if (parts.size == 4) addLine("${formatDate(parts[0].toLongOrNull() ?: 0L)} · ${parts[1]}\\n${parts[2]} → ${parts[3]}")
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
            val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "알 수 없음"
            val custom = getSharedPreferences("app_categories", MODE_PRIVATE).getString(packageName, "자동 분류")
            AlertDialog.Builder(this).setTitle(label)
                .setMessage("패키지: $packageName\n버전: $version\n분류: $custom\n시스템 앱 여부: ${if (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) "시스템 앱" else "사용자 설치 앱"}")
                .setPositiveButton("확인", null).setNeutralButton("분류 변경") { _, _ -> editAppCategory(label, packageName) }.show()
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

    private fun renderEntries(items: List<Entry>) {
        results.removeAllViews()
        if (items.isEmpty()) {
            addLine("선택한 위치에서 표시할 파일이나 폴더를 찾지 못했습니다.")
            return
        }
        items.take(3000).forEach { item ->
            val prefix = if (item.directory) "📁" else "📄"
            val detail = if (item.directory) "" else " · ${humanSize(item.size)}"
            addLine("$prefix ${item.relativePath}$detail")
        }
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
        runScanButton.isEnabled = !busy && selectedTrees.isNotEmpty()
        manageFoldersButton.isEnabled = !busy
        duplicateButton.isEnabled = !busy && entries.any { !it.directory }
        insightButton.isEnabled = !busy && entries.any { !it.directory }
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
        private const val LARGE_FILE_BYTES = 100L * 1024L * 1024L
        private const val OLD_FILE_AGE = 180L * 24L * 60L * 60L * 1000L
        private val ARCHIVE_SCRIPT_EXTENSIONS = listOf(".sh", ".bat", ".cmd", ".ps1", ".js", ".vbs", ".py", ".jar")
        private val ARCHIVE_EXECUTABLE_EXTENSIONS = listOf(".exe", ".dll", ".so", ".dex", ".apk")
        private val ARCHIVE_RISKY_PATH_MARKERS = listOf("../", "/../", "autorun", "startup", "payload")
    }
}
