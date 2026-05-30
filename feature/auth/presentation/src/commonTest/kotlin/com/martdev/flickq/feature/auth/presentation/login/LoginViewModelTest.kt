package com.martdev.flickq.feature.auth.presentation.login

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.auth.model.Credentials
import com.martdev.flickq.auth.model.LoginResult
import com.martdev.flickq.auth.model.OtpResendResult
import com.martdev.flickq.auth.model.RegistrationResult
import com.martdev.flickq.auth.model.VerificationInput
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

private class FakeAuthRepository : AuthRepository {
    var loginResult: Result<LoginResult, AuthError> =
        Result.Success(LoginResult(userId = 1, accessToken = "a", refreshToken = "r"))

    override suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError> =
        Result.Success(RegistrationResult(credentials.email, "token"))

    override suspend fun verifyOtp(input: VerificationInput): Result<LoginResult, AuthError> =
        loginResult

    override suspend fun login(credentials: Credentials): Result<LoginResult, AuthError> =
        loginResult

    override suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError> =
        Result.Success(OtpResendResult(email, "token"))
}

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid credentials emit Authenticated event`() = runTest {
        val viewModel = LoginViewModel(FakeAuthRepository())
        viewModel.onAction(LoginAction.OnEmailChange("fan@flickq.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("secret1"))

        viewModel.events.test {
            viewModel.onAction(LoginAction.OnLoginClick)
            assertThat(awaitItem()).isEqualTo(LoginEvent.Authenticated)
        }
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `invalid credentials surface an error in state`() = runTest {
        val repo = FakeAuthRepository().apply {
            loginResult = Result.Error(AuthError.INVALID_CREDENTIALS)
        }
        val viewModel = LoginViewModel(repo)
        viewModel.onAction(LoginAction.OnEmailChange("fan@flickq.com"))
        viewModel.onAction(LoginAction.OnPasswordChange("secret1"))

        viewModel.onAction(LoginAction.OnLoginClick)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `malformed email is flagged without calling the repository`() = runTest {
        val viewModel = LoginViewModel(FakeAuthRepository())
        viewModel.onAction(LoginAction.OnEmailChange("not-an-email"))
        viewModel.onAction(LoginAction.OnPasswordChange("secret1"))

        viewModel.onAction(LoginAction.OnLoginClick)

        assertThat(viewModel.state.value.emailError).isTrue()
    }

    @Test
    fun `register click emits NavigateToRegister event`() = runTest {
        val viewModel = LoginViewModel(FakeAuthRepository())

        viewModel.events.test {
            viewModel.onAction(LoginAction.OnRegisterClick)
            assertThat(awaitItem()).isEqualTo(LoginEvent.NavigateToRegister)
        }
    }
}
