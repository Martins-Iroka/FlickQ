@file:OptIn(ExperimentalForeignApi::class)

package com.martdev.flickq.feature.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecAuthFailed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.errSecUserCanceled
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecUseOperationPrompt
import platform.Security.kSecValueData
import kotlin.coroutines.resume

@ExperimentalForeignApi
class IosCredentialVault : CredentialVault {
    private val service = "com.martdev.flickq.credentialvault"
    private val account = "biometric_credentials"

    override fun availability(): BiometricAvailability {
        val canEvaluate = LAContext().canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            null
        )
        return if (canEvaluate) BiometricAvailability.Available else BiometricAvailability.NotEnrolled
    }

    override suspend fun hasStoredCredentials(): Boolean {
        return SecItemCopyMatching(baseQuery() as CFDictionaryRef, null) == errSecSuccess
    }

    override suspend fun save(data: String): BiometricAuthResult {
        val confirmed = evaluateBiometrics("Confirm biometrics to enable biometric login")
        if (!confirmed) return BiometricAuthResult.Cancelled

        clear()

        val accessControl = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAccessControlBiometryCurrentSet,
            null
        ) ?: return BiometricAuthResult.Error

        val attributes = baseQuery().apply {
            setObject(data.toNSData(), kSecValueData as Any as NSCopyingProtocol)
            setObject(accessControl, kSecAttrAccessControl as Any as NSCopyingProtocol)
        }

        val status = SecItemAdd(attributes as CFDictionaryRef, null)
        return if (status == errSecSuccess) BiometricAuthResult.Success else BiometricAuthResult.Error
    }

    override suspend fun load(): CredentialLoadResult {
        val context = LAContext()
        val query = baseQuery().apply {
            setObject(true, kSecReturnData as Any as NSCopyingProtocol)
            setObject(context, kSecUseAuthenticationContext as Any as NSCopyingProtocol)
            setObject("Log in with biometrics", kSecUseOperationPrompt as Any as NSCopyingProtocol)
        }

        return memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            when(status) {
                errSecSuccess -> {
                    val data = result.value as? NSData
                    data?.toKString()?.let {
                        CredentialLoadResult.Success(it)
                    } ?: CredentialLoadResult.Failed
                }

                errSecItemNotFound -> CredentialLoadResult.NotEnrolled
                errSecUserCanceled, errSecAuthFailed -> CredentialLoadResult.Cancelled
                else -> CredentialLoadResult.Failed
            }
        }
    }

    override fun clear() {
        SecItemDelete(baseQuery() as CFDictionaryRef)
    }

    private fun baseQuery() = NSMutableDictionary().apply {
        setObject(kSecClassGenericPassword, kSecClass as NSCopyingProtocol)
        setObject(service, forKey = kSecAttrService as NSCopyingProtocol)
        setObject(account, forKey = kSecAttrAccount as NSCopyingProtocol)
    }
    private suspend fun evaluateBiometrics(reason: String) = suspendCancellableCoroutine { continuation ->
        val context = LAContext()
        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason
        ) { success, _ ->
            continuation.resume(success)
        }
    }

    private fun String.toNSData() = (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)

    private fun NSData.toKString(): String? = NSString.create(this, NSUTF8StringEncoding) as? String
}

@Composable
actual fun rememberCredentialVault(): CredentialVault {
    return remember {
        IosCredentialVault()
    }
}