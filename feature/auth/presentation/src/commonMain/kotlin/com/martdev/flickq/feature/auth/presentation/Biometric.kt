package com.martdev.flickq.feature.auth.presentation

import androidx.compose.runtime.Composable

enum class BiometricAvailability {
    Available,
    NoHardware,
    NotEnrolled,
    Unavailable
}

enum class BiometricAuthResult {
    Success,
    Cancelled,
    Error
}

sealed interface CredentialLoadResult {
    data class Success(val credentials: String): CredentialLoadResult
    data object NotEnrolled : CredentialLoadResult
    data object Cancelled : CredentialLoadResult
    data object Failed : CredentialLoadResult
}

interface CredentialVault {
    fun availability(): BiometricAvailability
    suspend fun hasStoredCredentials(): Boolean
    suspend fun save(data: String): BiometricAuthResult
    suspend fun load(): CredentialLoadResult
    fun clear()
}

@Composable
expect fun rememberCredentialVault(): CredentialVault