package com.example.smartorganizer.core.model

import android.net.Uri

/** A real item discovered through a user-granted Storage Access Framework tree. */
data class FileRecord(
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long,
    val uri: Uri,
    val isDirectory: Boolean
)
