package com.example.smartorganizer.features.apps

enum class InstalledAppCategory {
    COMMUNICATION, SOCIAL, PRODUCTIVITY, FINANCE, MEDIA,
    SHOPPING, TRAVEL, SYSTEM, DEVELOPMENT, OTHER
}

data class AppCategorySuggestion(
    val packageName: String,
    val category: InstalledAppCategory,
    val reason: String,
    val requiresUserConfirmation: Boolean = true
)
