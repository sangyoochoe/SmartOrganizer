package com.example.smartorganizer.features.operations

enum class FileOperationType { MOVE, RENAME, DELETE_TO_TRASH, RESTORE }

data class FileOperationRecord(
    val id: String,
    val type: FileOperationType,
    val originalPath: String,
    val resultingPath: String?,
    val timestampEpochMillis: Long,
    val completed: Boolean,
    val detail: String? = null
)
