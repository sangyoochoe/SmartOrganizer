package com.example.smartorganizer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.Executors

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
    private var selectedTree: Uri? = null
    private lateinit var summary: TextView
    private lateinit var results: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var duplicateButton: Button
    private lateinit var appsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 16)
        }
        val title = TextView(this).apply {
            text = "Smart Organizer"
            textSize = 26f
            setPadding(0, 0, 0, 6)
        }
        val subtitle = TextView(this).apply {
            text = "1차 개발 · 실제 파일 분석"
            textSize = 14f
        }
        summary = TextView(this).apply {
            text = "분석할 폴더를 선택하세요. 샘플 파일이나 가짜 결과는 표시하지 않습니다."
            textSize = 14f
            setPadding(0, 18, 0, 14)
        }
        scanButton = Button(this).apply {
            text = "폴더 선택 및 스캔"
            setOnClickListener { chooseFolder() }
        }
        duplicateButton = Button(this).apply {
            text = "동일 내용 파일 검사"
            isEnabled = false
            setOnClickListener { scanExactDuplicates() }
        }
        appsButton = Button(this).apply {
            text = "설치된 앱 목록 보기"
            setOnClickListener { showLaunchableApps() }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scanButton)
            addView(duplicateButton)
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

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_TREE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
        selectedTree = uri
        scanFolder(uri)
    }

    private fun scanFolder(treeUri: Uri) {
        setBusy(true, "선택한 폴더와 하위 폴더를 실제로 읽는 중입니다…")
        worker.execute {
            val found = mutableListOf<Entry>()
            val root = DocumentFile.fromTreeUri(this, treeUri)
            var inaccessible = 0
            fun walk(dir: DocumentFile, parent: String) {
                val children = try { dir.listFiles() } catch (_: Exception) {
                    inaccessible++
                    emptyArray()
                }
                for (child in children) {
                    val name = child.name ?: "(이름 없음)"
                    val path = if (parent.isEmpty()) name else "$parent/$name"
                    val isDir = child.isDirectory
                    found.add(Entry(name, path, if (child.isFile) child.length() else 0L,
                        child.lastModified(), child.uri, isDir))
                    if (isDir) walk(child, path)
                }
            }
            if (root != null) walk(root, "") else inaccessible++
            runOnUiThread {
                entries.clear()
                entries.addAll(found)
                val fileCount = found.count { !it.directory }
                val dirCount = found.count { it.directory }
                val bytes = found.filter { !it.directory }.sumOf { it.size }
                summary.text = "실제 스캔 결과: 파일 ${fileCount}개 · 폴더 ${dirCount}개 · ${humanSize(bytes)}" +
                        if (inaccessible > 0) "\n일부 하위 폴더는 접근할 수 없었습니다." else ""
                duplicateButton.isEnabled = fileCount > 0
                renderEntries(found)
                setBusy(false)
            }
        }
    }

    private fun renderEntries(items: List<Entry>) {
        results.removeAllViews()
        if (items.isEmpty()) {
            addLine("선택한 위치에서 표시할 파일이나 폴더를 찾지 못했습니다.")
            return
        }
        items.take(5000).forEach { item ->
            val prefix = if (item.directory) "📁" else "📄"
            val detail = if (item.directory) "" else " · ${humanSize(item.size)}"
            addLine("$prefix  ${item.relativePath}$detail")
        }
        if (items.size > 5000) addLine("화면 표시 제한: ${items.size}개 중 처음 5,000개 표시")
    }

    private fun scanExactDuplicates() {
        if (selectedTree == null) return
        setBusy(true, "크기가 같은 파일 후보를 SHA-256으로 비교 중입니다. 파일 수에 따라 시간이 걸립니다…")
        worker.execute {
            val candidates = entries.filter { !it.directory && it.size > 0L }
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
                } catch (_: Exception) { /* 해당 파일은 읽을 수 없어 비교에서 제외 */ }
            }
            val duplicates = groups.values.filter { it.size > 1 }
            runOnUiThread {
                results.removeAllViews()
                if (duplicates.isEmpty()) {
                    summary.text = "검사 가능한 파일에서 동일 내용의 중복 파일을 찾지 못했습니다."
                    addLine("중복 파일 없음")
                } else {
                    summary.text = "실제 내용이 동일한 파일 그룹 ${duplicates.size}개를 찾았습니다. 자동 삭제하지 않았습니다."
                    duplicates.forEachIndexed { index, group ->
                        addLine("중복 그룹 ${index + 1}", true)
                        group.forEach { addLine("${it.relativePath} · ${humanSize(it.size)}") }
                    }
                }
                setBusy(false)
            }
        }
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
                summary.text = "실행 가능한 설치 앱 ${apps.size}개 (Android 공개 범위 내)"
                apps.forEach { (label, pkg) -> addLine("📱 $label · $pkg") }
                setBusy(false)
            }
        }
    }

    private fun addLine(text: String, heading: Boolean = false) {
        results.addView(TextView(this).apply {
            this.text = text
            textSize = if (heading) 16f else 13f
            if (heading) setPadding(0, 14, 0, 5) else setPadding(4, 6, 4, 6)
        })
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        if (message != null) summary.text = message
        scanButton.isEnabled = !busy
        duplicateButton.isEnabled = !busy && entries.any { !it.directory }
        appsButton.isEnabled = !busy
    }

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

    companion object { private const val REQUEST_TREE = 7101 }
}
