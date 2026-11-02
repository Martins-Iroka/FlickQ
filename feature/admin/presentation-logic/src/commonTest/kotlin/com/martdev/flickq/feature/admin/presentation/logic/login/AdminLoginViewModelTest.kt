package com.martdev.flickq.feature.admin.presentation.logic.login

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
import com.martdev.flickq.feature.auth.domain.AuthError
import com.martdev.flickq.feature.auth.domain.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Admin login goes through [AuthRepository.newLogin] against the admin-only endpoint
 * (`isAdmin = true`); role gating is enforced server-side, so the fake only records the call
 * and returns the configured outcome.
 */
private class FakeAuthRepository(
    private val loginError: AuthError? = null,
) : AuthRepository {
    var lastCredentials: Credentials? = null
    var lastIsAdmin: Boolean? = null

    override suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError> =
        Result.Error(AuthError.UNKNOWN)

    override suspend fun verifyOtp(input: VerificationInput): EmptyResult<AuthError> =
        Result.Error(AuthError.UNKNOWN)

    override suspend fun login(
        credentials: Credentials,
        isAdmin: Boolean
    ): Result<LoginResult, AuthError> = Result.Error(AuthError.UNKNOWN)

    override suspend fun newLogin(
        credentials: Credentials,
        isAdmin: Boolean
    ): Result<Unit, AuthError> {
        lastCredentials = credentials
        lastIsAdmin = isAdmin
        return loginError?.let { Result.Error(it) } ?: Result.Success(Unit)
    }

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
        vm.onAction(AdminLoginAction.OnPasswordChange("Secret123"))
    }

    @Test
    fun `a successful admin login authenticates`() = runTest {
        val repo = FakeAuthRepository()
        val vm = AdminLoginViewModel(repo)

        vm.events.test {
            fillCredentials(vm)
            vm.onAction(AdminLoginAction.OnSubmit)
            assertThat(awaitItem()).isEqualTo(AdminLoginEvent.Authenticated)
        }
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.isLoading).isEqualTo(false)
    }

    @Test
    fun `submit logs in against the admin endpoint with trimmed credentials`() = runTest {
        val repo = FakeAuthRepository()
        val vm = AdminLoginViewModel(repo)

        vm.onAction(AdminLoginAction.OnEmailChange("  admin@flickq.com  "))
        vm.onAction(AdminLoginAction.OnPasswordChange("Secret123"))
        vm.onAction(AdminLoginAction.OnSubmit)

        assertThat(repo.lastIsAdmin).isEqualTo(true)
        assertThat(repo.lastCredentials).isEqualTo(Credentials("admin@flickq.com", "Secret123"))
    }

    @Test
    fun `invalid credentials surface an error`() = runTest {
        val repo = FakeAuthRepository(loginError = AuthError.INVALID_CREDENTIALS)
        val vm = AdminLoginViewModel(repo)

        fillCredentials(vm)
        vm.onAction(AdminLoginAction.OnSubmit)

        assertThat(repo.lastIsAdmin).isEqualTo(true)
        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.isLoading).isEqualTo(false)
    }
}
