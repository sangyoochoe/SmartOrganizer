package com.example.smartorganizer.features.launcher

/** Draft only: the photo-based custom launcher is intentionally implemented last. */
data class LauncherLayoutDraft(
    val backgroundUri: String,
    val foldedLayout: List<LauncherItemDraft>,
    val unfoldedLayout: List<LauncherItemDraft>,
    val createdAtEpochMillis: Long
)

data class LauncherItemDraft(
    val packageName: String,
    val label: String,
    val row: Int,
    val column: Int,
    val reasonForPlacement: String
)
