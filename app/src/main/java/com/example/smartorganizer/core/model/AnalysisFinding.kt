package com.example.smartorganizer.core.model

enum class FindingSeverity { INFO, REVIEW, WARNING, HIGH }

enum class FindingKind {
    LARGE_FILE, OLD_FILE, EMPTY_FOLDER, SAME_NAME, SAME_SIZE,
    EXACT_DUPLICATE, SIMILAR_FILE, APK_METADATA, APK_PERMISSION,
    SIGNATURE, SUSPICIOUS_INDICATOR, UNANALYZABLE
}

/** Every finding should explain its evidence; a finding never authorizes deletion by itself. */
data class AnalysisFinding(
    val kind: FindingKind,
    val severity: FindingSeverity,
    val title: String,
    val evidence: List<String>,
    val confidence: Float? = null,
    val relatedPaths: List<String> = emptyList()
)
