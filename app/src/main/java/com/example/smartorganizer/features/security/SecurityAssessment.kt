package com.example.smartorganizer.features.security

enum class SecurityRisk {
    NO_KNOWN_INDICATORS, DEVELOPMENT_OR_TEST, NEEDS_REVIEW,
    POTENTIALLY_RISKY, UNABLE_TO_ANALYZE
}

data class SecurityEvidence(
    val code: String,
    val description: String,
    val source: String,
    val confidence: Float? = null
)

data class SecurityAssessment(
    val risk: SecurityRisk,
    val evidence: List<SecurityEvidence>,
    val limitations: List<String>,
    val analyzedAtEpochMillis: Long
)
