package com.martdev.flickq.feature.auth.presentation.otp

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isZero
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
    var verifyResult: Result<Unit, AuthError> = Result.Success(Unit)
    var resendResult: Result<OtpResendResult, AuthError> =
        Result.Success(OtpResendResult(emailId = "email-1", verificationToken = "resent-token"))

    var lastVerificationInput: VerificationInput? = null
    var verifyCallCount: Int = 0
    var resendCallCount: Int = 0

    override suspend fun register(credentials: Credentials): Result<RegistrationResult, AuthError> =
        Result.Success(RegistrationResult(credentials.email, "token"))

    override suspend fun verifyOtp(input: VerificationInput): Result<Unit, AuthError> {
        verifyCallCount++
        lastVerificationInput = input
        return verifyResult
    }

    override suspend fun login(
        credentials: Credentials,
        isAdmin: Boolean
    ): Result<LoginResult, AuthError> =
        Result.Success(LoginResult(userId = 1, accessToken = "a", refreshToken = "r"))

    override suspend fun resendOtp(email: String): Result<OtpResendResult, AuthError> {
        resendCallCount++
        return resendResult
    }

    override suspend fun logout(): Result<Unit, AuthError> = Result.Success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class OtpVerifyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        repo: AuthRepository,
        email: String = "fan@flickq.com",
        emailId: String = "email-1",
        registrationToken: String = "reg-token"
    ) = OtpVerifyViewModel(
        email = email,
        emailId = emailId,
        registrationToken = registrationToken,
        authRepository = repo
    )

    @Test
    fun `valid code emits Verified event`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())
        viewModel.onAction(OtpVerifyAction.OnCodeChange("123456"))

        viewModel.events.test {
            viewModel.onAction(OtpVerifyAction.OnVerifyClick)
            assertThat(awaitItem()).isEqualTo(OtpVerifyEvent.Verified)
        }
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `verify forwards code emailId and token to the repository`() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = viewModel(repo, emailId = "email-9", registrationToken = "reg-9")
        viewModel.onAction(OtpVerifyAction.OnCodeChange("123456"))

        viewModel.onAction(OtpVerifyAction.OnVerifyClick)

        assertThat(repo.lastVerificationInput).isEqualTo(
            VerificationInput(code = "123456", emailId = "email-9", registrationToken = "reg-9")
        )
    }

    @Test
    fun `failed verification surfaces an error in state`() = runTest {
        val repo = FakeAuthRepository().apply {
            verifyResult = Result.Error(AuthError.INVALID_CREDENTIALS)
        }
        val viewModel = viewModel(repo)
        viewModel.onAction(OtpVerifyAction.OnCodeChange("123456"))

        viewModel.onAction(OtpVerifyAction.OnVerifyClick)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `verify is a no-op when the code is incomplete`() = runTest {
        val repo = FakeAuthRepository()
        val viewModel = viewModel(repo)
        viewModel.onAction(OtpVerifyAction.OnCodeChange("123"))

        viewModel.onAction(OtpVerifyAction.OnVerifyClick)

        assertThat(repo.verifyCallCount).isZero()
    }

    @Test
    fun `code change keeps only digits and caps at OTP length`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())

        viewModel.onAction(OtpVerifyAction.OnCodeChange("12ab34-567890"))

        assertThat(viewModel.state.value.code).isEqualTo("123456")
    }

    @Test
    fun `code change clears a previous error`() = runTest {
        val repo = FakeAuthRepository().apply {
            verifyResult = Result.Error(AuthError.INVALID_CREDENTIALS)
        }
        val viewModel = viewModel(repo)
        viewModel.onAction(OtpVerifyAction.OnCodeChange("123456"))
        viewModel.onAction(OtpVerifyAction.OnVerifyClick)
        assertThat(viewModel.state.value.error).isNotNull()

        viewModel.onAction(OtpVerifyAction.OnCodeChange("1234"))

        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun `resend success sets an info message and clears error`() = runTest {
        val viewModel = viewModel(FakeAuthRepository())

        viewModel.onAction(OtpVerifyAction.OnResendClick)

        assertThat(viewModel.state.value.info).isNotNull()
        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `resend failure surfaces an error in state`() = runTest {
        val repo = FakeAuthRepository().apply {
            resendResult = Result.Error(AuthError.UNKNOWN)
        }
        val viewModel = viewModel(repo)

        viewModel.onAction(OtpVerifyAction.OnResendClick)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }

    @Test
    fun `verify after a resend uses the refreshed verification token`() = runTest {
        val repo = FakeAuthRepository().apply {
            resendResult = Result.Success(
                OtpResendResult(emailId = "email-1", verificationToken = "fresh-token")
            )
        }
        val viewModel = viewModel(repo, registrationToken = "stale-token")

        viewModel.onAction(OtpVerifyAction.OnResendClick)
        viewModel.onAction(OtpVerifyAction.OnCodeChange("123456"))
        viewModel.onAction(OtpVerifyAction.OnVerifyClick)

        assertThat(repo.lastVerificationInput?.registrationToken).isEqualTo("fresh-token")
    }

    @Test
    fun `resends automatically when emailId and token are both empty`() = runTest {
        val repo = FakeAuthRepository()

        viewModel(repo, emailId = "", registrationToken = "")

        assertThat(repo.resendCallCount).isEqualTo(1)
    }

    @Test
    fun `does not auto-resend when a token is present`() = runTest {
        val repo = FakeAuthRepository()

        viewModel(repo, emailId = "", registrationToken = "reg-token")

        assertThat(repo.resendCallCount).isZero()
    }
}
