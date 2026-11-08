package com.example.smartorganizer.features.organization

/** User-defined organization rule. AI suggestions must remain suggestions until approved. */
data class OrganizationRule(
    val id: String,
    val name: String,
    val destinationRelativePath: String,
    val extensions: Set<String> = emptySet(),
    val nameContains: String? = null,
    val enabled: Boolean = true
)

data class OrganizationSuggestion(
    val sourceUri: String,
    val sourcePath: String,
    val suggestedDestination: String,
    val reason: String,
    val requiresUserApproval: Boolean = true
)
