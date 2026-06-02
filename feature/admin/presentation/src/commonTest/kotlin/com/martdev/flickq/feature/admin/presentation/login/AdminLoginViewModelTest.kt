package com.martdev.flickq.feature.admin.presentation.login

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
import com.martdev.flickq.core.common.EmptyResult
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.core.data.InMemoryTokenStorage
import com.martdev.flickq.feature.auth.domain.AuthError
import com.martdev.flickq.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalEncodingApi::class)
private fun jwt(payload: String): String {
    val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
    return "header.$encoded.signature"
}

private class FakeAuthRepository(
    private val accessToken: String = "",
    private val loginFails: Boolean = false,
) : AuthRepository {
    override suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError> =
        Result.Error(AuthError.UNKNOWN)

    override suspend fun verifyOtp(input: VerificationInput): EmptyResult<AuthError> =
        Result.Error(AuthError.UNKNOWN)

    override suspend fun login(credentials: Credentials): Result<LoginResult, AuthError> =
        if (loginFails) Result.Error(AuthError.INVALID_CREDENTIALS)
        else Result.Success(LoginResult(userId = 7, accessToken = accessToken, refreshToken = "refresh"))

    override suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError> =
        Result.Error(AuthError.UNKNOWN)

    override suspend fun logout(): EmptyResult<AuthError> = Result.Success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdminLoginViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillCredentials(vm: AdminLoginViewModel) {
        vm.onAction(AdminLoginAction.OnEmailChange("admin@flickq.com"))
        vm.onAction(AdminLoginAction.OnPasswordChange("secret1"))
    }

    @Test
    fun `an admin JWT authenticates`() = runTest {
        val repo = FakeAuthRepository(accessToken = jwt("""{"userId":"7","role":"ADMIN"}"""))
        val vm = AdminLoginViewModel(repo, InMemoryTokenStorage())

        vm.events.test {
            fillCredentials(vm)
            vm.onAction(AdminLoginAction.OnSubmit)
            assertThat(awaitItem()).isEqualTo(AdminLoginEvent.Authenticated)
        }
    }

    @Test
    fun `a non-admin JWT is rejected and the session is cleared`() = runTest {
        val storage = InMemoryTokenStorage().apply { saveTokens("stale", "stale") }
        val repo = FakeAuthRepository(accessToken = jwt("""{"userId":"7","role":"USER"}"""))
        val vm = AdminLoginViewModel(repo, storage)

        fillCredentials(vm)
        vm.onAction(AdminLoginAction.OnSubmit)

        assertThat(vm.state.value.error).isNotNull()
        assertThat(storage.getAccessToken()).isNull()
    }

    @Test
    fun `invalid credentials surface an error`() = runTest {
        val repo = FakeAuthRepository(loginFails = true)
        val vm = AdminLoginViewModel(repo, InMemoryTokenStorage())

        fillCredentials(vm)
        vm.onAction(AdminLoginAction.OnSubmit)

        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.isLoading).isEqualTo(false)
    }
}
