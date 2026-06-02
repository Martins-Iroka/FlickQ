package com.martdev.flickq.feature.payment.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.martdev.flickq.core.common.DataError
import com.martdev.flickq.core.common.Result
import com.martdev.flickq.feature.payment.domain.PaymentRepository
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private class FakePaymentRepository(
    private val authorizationUrl: String? = "https://checkout.paystack.com/REF-1",
) : PaymentRepository {
    var initializeFails = false
    var initializeError: DataError = DataError.Network.SERVER_ERROR
    var initializeCount = 0
    var verifyCount = 0
    var lastReservationId: Long? = null

    /** Status returned on successive verify calls; the last entry repeats. A null entry → error. */
    var verifyStatuses: List<PaymentStatus?> = listOf(PaymentStatus.SUCCESS)

    override suspend fun initializePayment(reservationId: Long): Result<Payment, DataError> {
        initializeCount++
        lastReservationId = reservationId
        return if (initializeFails) {
            Result.Error(initializeError)
        } else {
            Result.Success(
                Payment(
                    id = 1,
                    reservationId = reservationId,
                    reference = "REF-1",
                    amount = 7000,
                    status = PaymentStatus.PENDING,
                    authorizationUrl = authorizationUrl
                )
            )
        }
    }

    override suspend fun verifyPayment(reference: String): Result<Payment, DataError> {
        val index = minOf(verifyCount, verifyStatuses.lastIndex)
        verifyCount++
        val status = verifyStatuses[index] ?: return Result.Error(DataError.Network.SERVER_ERROR)
        return Result.Success(Payment(id = 1, reference = reference, amount = 7000, status = status))
    }
}

private class RecordingUrlOpener : UrlOpener {
    val opened = mutableListOf<String>()
    override fun open(url: String) {
        opened += url
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {

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
        repo: FakePaymentRepository,
        opener: UrlOpener = RecordingUrlOpener(),
        maxPollAttempts: Int = 5,
    ) = PaymentViewModel(
        reservationId = 42,
        paymentRepository = repo,
        urlOpener = opener,
        pollDelayMillis = 0,
        maxPollAttempts = maxPollAttempts,
    )

    @Test
    fun `a real authorization url stops at ready-to-pay without opening or polling`() = runTest {
        val repo = FakePaymentRepository()
        val opener = RecordingUrlOpener()

        val vm = viewModel(repo, opener)

        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.READY_TO_PAY)
        assertThat(vm.state.value.reference).isEqualTo("REF-1")
        assertThat(opener.opened).isEmpty()
        assertThat(repo.verifyCount).isEqualTo(0)
    }

    @Test
    fun `proceeding opens the authorization url then verifies into confirmed`() = runTest {
        val repo = FakePaymentRepository()
        val opener = RecordingUrlOpener()
        val vm = viewModel(repo, opener)

        vm.onAction(PaymentAction.OnProceedToPayment)

        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.CONFIRMED)
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.amountLabel).isEqualTo("₦7,000")
        assertThat(repo.lastReservationId).isEqualTo(42L)
        assertThat(opener.opened).containsExactly("https://checkout.paystack.com/REF-1")
    }

    @Test
    fun `a null authorization url skips the browser hand-off and confirms immediately`() = runTest {
        val repo = FakePaymentRepository(authorizationUrl = null)
        val opener = RecordingUrlOpener()

        val state = viewModel(repo, opener).state.value

        assertThat(state.phase).isEqualTo(PaymentPhase.CONFIRMED)
        assertThat(opener.opened).isEmpty()
    }

    @Test
    fun `polls verify until it resolves to success`() = runTest {
        val repo = FakePaymentRepository().apply {
            verifyStatuses = listOf(PaymentStatus.PENDING, PaymentStatus.PENDING, PaymentStatus.SUCCESS)
        }
        val vm = viewModel(repo)

        vm.onAction(PaymentAction.OnProceedToPayment)

        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.CONFIRMED)
        assertThat(repo.verifyCount).isEqualTo(3)
    }

    @Test
    fun `a terminal failed status surfaces an error and stops polling`() = runTest {
        val repo = FakePaymentRepository().apply { verifyStatuses = listOf(PaymentStatus.FAILED) }
        val vm = viewModel(repo)

        vm.onAction(PaymentAction.OnProceedToPayment)

        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.AWAITING_PAYMENT)
        assertThat(repo.verifyCount).isEqualTo(1)
    }

    @Test
    fun `initialize failure surfaces an error before any hand-off`() = runTest {
        val repo = FakePaymentRepository().apply { initializeFails = true }
        val opener = RecordingUrlOpener()

        val vm = viewModel(repo, opener)

        assertThat(vm.state.value.error).isNotNull()
        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.INITIALIZING)
        assertThat(opener.opened).isEmpty()
    }

    @Test
    fun `verify that never resolves surfaces an error after exhausting attempts`() = runTest {
        val repo = FakePaymentRepository().apply { verifyStatuses = listOf(PaymentStatus.PENDING) }
        val vm = viewModel(repo, maxPollAttempts = 3)

        vm.onAction(PaymentAction.OnProceedToPayment)

        assertThat(vm.state.value.error).isNotNull()
        assertThat(repo.verifyCount).isEqualTo(3)
    }

    @Test
    fun `persistent verify errors surface after exhausting attempts`() = runTest {
        val repo = FakePaymentRepository().apply { verifyStatuses = listOf(null) }
        val vm = viewModel(repo, maxPollAttempts = 3)

        vm.onAction(PaymentAction.OnProceedToPayment)

        assertThat(vm.state.value.error).isNotNull()
        assertThat(repo.verifyCount).isEqualTo(3)
    }

    @Test
    fun `retry after an unresolved poll re-polls without re-initializing`() = runTest {
        val repo = FakePaymentRepository().apply { verifyStatuses = listOf(PaymentStatus.PENDING) }
        val vm = viewModel(repo, maxPollAttempts = 2)
        vm.onAction(PaymentAction.OnProceedToPayment)
        assertThat(vm.state.value.error).isNotNull()

        repo.verifyStatuses = listOf(PaymentStatus.SUCCESS)
        vm.onAction(PaymentAction.OnRetry)

        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.CONFIRMED)
        assertThat(repo.initializeCount).isEqualTo(1)
    }

    @Test
    fun `retry after an initialize failure re-initializes`() = runTest {
        // null authorization url so the re-initialized flow confirms without a proceed tap.
        val repo = FakePaymentRepository(authorizationUrl = null).apply { initializeFails = true }
        val vm = viewModel(repo)
        assertThat(vm.state.value.error).isNotNull()

        repo.initializeFails = false
        vm.onAction(PaymentAction.OnRetry)

        assertThat(vm.state.value.phase).isEqualTo(PaymentPhase.CONFIRMED)
        assertThat(repo.initializeCount).isEqualTo(2)
    }

    @Test
    fun `a 409 on initialize emits ReservationExpired instead of a payment error`() = runTest {
        val repo = FakePaymentRepository().apply {
            initializeFails = true
            initializeError = DataError.Network.CONFLICT
        }
        val opener = RecordingUrlOpener()
        val vm = viewModel(repo, opener)

        vm.events.test {
            assertThat(awaitItem()).isEqualTo(PaymentEvent.ReservationExpired)
        }
        // No "payment failed" error and no hand-off — the user is sent back to re-pick seats.
        assertThat(vm.state.value.error).isNull()
        assertThat(opener.opened).isEmpty()
    }

    @Test
    fun `a 400 on initialize is also treated as an expired reservation`() = runTest {
        val repo = FakePaymentRepository().apply {
            initializeFails = true
            initializeError = DataError.Network.BAD_REQUEST
        }
        val vm = viewModel(repo)

        vm.events.test {
            assertThat(awaitItem()).isEqualTo(PaymentEvent.ReservationExpired)
        }
    }

    @Test
    fun `done click emits Done event`() = runTest {
        val vm = viewModel(FakePaymentRepository())

        vm.events.test {
            vm.onAction(PaymentAction.OnDoneClick)
            assertThat(awaitItem()).isEqualTo(PaymentEvent.Done)
        }
    }
}
