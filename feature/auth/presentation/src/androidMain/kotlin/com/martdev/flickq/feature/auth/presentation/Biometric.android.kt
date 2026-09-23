package com.martdev.flickq.feature.auth.presentation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.martdev.flickq.core.data.AndroidSettingsStorage
import com.martdev.flickq.core.data.TokenStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64

class AndroidCredentialVault(
    private val activity: FragmentActivity,
    private val storage: AndroidSettingsStorage
) : CredentialVault {
    private val keyAlias = "biometric_credential_key"

    override fun availability(): BiometricAvailability {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NoHardware

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                clear()
                BiometricAvailability.NotEnrolled
            }
            else -> BiometricAvailability.Unavailable
        }
    }

    override suspend fun hasStoredCredentials(): Boolean {
        return !storage.getEncryptedCred().isNullOrEmpty()
    }

    override suspend fun save(data: String): BiometricAuthResult {
        val cipher = getCipher().apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        return promptAndRun(
            cipher,
            title = "Biometric registration",
            "Confirm biometrics to enable biometric login"
        ) { authorized ->
            val cipherText = authorized.doFinal(
                data.encodeToByteArray()
            )
            storage.saveEncryptedCred(Base64.encode(cipherText))
            storage.saveIVParams(Base64.encode(authorized.iv))
        }
    }

    override suspend fun load(): CredentialLoadResult {
        val cipherText = storage.getEncryptedCred()?.let {
            Base64.decode(it)
        } ?: return CredentialLoadResult.NotEnrolled
        val iv = storage.getIVParam()?.let {
            Base64.decode(it)
        } ?: return CredentialLoadResult.NotEnrolled

        return try {
            val cipher = getCipher().apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), IvParameterSpec(iv))
            }
            var text: String? = null
            val authResult = promptAndRun(cipher, title = "Log in", "Log in with biometrics") {
                text = it.doFinal(cipherText).decodeToString()
            }
            when(authResult) {
                BiometricAuthResult.Success -> text?.let {
                    CredentialLoadResult.Success(it)
                } ?: CredentialLoadResult.Failed
                BiometricAuthResult.Cancelled -> CredentialLoadResult.Cancelled
                BiometricAuthResult.Error -> CredentialLoadResult.Failed
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            e.printStackTrace()
            clear()
            CredentialLoadResult.NotEnrolled
        }
    }

    override fun clear() {
        storage.clearEncryptedCredAndIVParam()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(keyAlias)
            }
        }
    }

    private fun getCipher() = Cipher.getInstance(
        "${KeyProperties.KEY_ALGORITHM_AES}/${
            KeyProperties.BLOCK_MODE_CBC
        }/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"
    )

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let {
            return it
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private suspend fun promptAndRun(
        cipher: Cipher,
        title: String,
        subtitle: String,
        onAuthorized: (Cipher) -> Unit
    ): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        onAuthorized(result.cryptoObject!!.cipher!!)
                        continuation.resume(BiometricAuthResult.Success)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        continuation.resume(BiometricAuthResult.Error)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    val cancelled = errString.contains("Cancel")
                    continuation.resume(if (cancelled) BiometricAuthResult.Cancelled else BiometricAuthResult.Error)
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .build()

        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(
            info, BiometricPrompt.CryptoObject(cipher)
        )
    }
}

@Composable
actual fun rememberCredentialVault(): CredentialVault {
    val tokenStorage = koinInject<TokenStorage>()
    val activity = LocalContext.current as FragmentActivity
    return remember { AndroidCredentialVault(activity, tokenStorage as AndroidSettingsStorage) }
}