package com.martdev.flickq.feature.payment.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
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

private class FakePaymentRepository : PaymentRepository {
    var initializeFails = false
    var verifyFails = false
    var lastReservationId: Long? = null

    override suspend fun initializePayment(reservationId: Long): Result<Payment, DataError> {
        lastReservationId = reservationId
        return if (initializeFails) Result.Error(DataError.Network.SERVER_ERROR)
        else Result.Success(
            Payment(
                id = 1,
                reservationId = reservationId,
                reference = "REF-1",
                amount = 7000,
                status = PaymentStatus.PENDING,
                authorizationUrl = "https://checkout.paystack.com/REF-1"
            )
        )
    }

    override suspend fun verifyPayment(reference: String): Result<Payment, DataError> {
        return if (verifyFails) Result.Error(DataError.Network.SERVER_ERROR)
        else Result.Success(
            Payment(id = 1, reference = reference, amount = 7000, status = PaymentStatus.SUCCESS)
        )
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

    @Test
    fun `happy path initializes then verifies into a confirmed state`() = runTest {
        val repo = FakePaymentRepository()
        val viewModel = PaymentViewModel(reservationId = 42, paymentRepository = repo)

        val state = viewModel.state.value
        assertThat(state.phase).isEqualTo(PaymentPhase.CONFIRMED)
        assertThat(state.error).isNull()
        assertThat(state.reference).isEqualTo("REF-1")
        assertThat(state.amountLabel).isEqualTo("₦7,000")
        assertThat(repo.lastReservationId).isEqualTo(42L)
    }

    @Test
    fun `initialize failure surfaces an error`() = runTest {
        val repo = FakePaymentRepository().apply { initializeFails = true }
        val viewModel = PaymentViewModel(reservationId = 1, paymentRepository = repo)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.phase).isEqualTo(PaymentPhase.INITIALIZING)
    }

    @Test
    fun `verify failure surfaces an error after initialize`() = runTest {
        val repo = FakePaymentRepository().apply { verifyFails = true }
        val viewModel = PaymentViewModel(reservationId = 1, paymentRepository = repo)

        assertThat(viewModel.state.value.error).isNotNull()
        assertThat(viewModel.state.value.phase).isEqualTo(PaymentPhase.PROCESSING)
    }

    @Test
    fun `done click emits Done event`() = runTest {
        val viewModel = PaymentViewModel(reservationId = 1, paymentRepository = FakePaymentRepository())

        viewModel.events.test {
            viewModel.onAction(PaymentAction.OnDoneClick)
            assertThat(awaitItem()).isEqualTo(PaymentEvent.Done)
        }
    }

    @Test
    fun `retry after failure reaches confirmed`() = runTest {
        val repo = FakePaymentRepository().apply { initializeFails = true }
        val viewModel = PaymentViewModel(reservationId = 1, paymentRepository = repo)
        assertThat(viewModel.state.value.error).isNotNull()

        repo.initializeFails = false
        viewModel.onAction(PaymentAction.OnRetry)

        assertThat(viewModel.state.value.error).isNull()
        assertThat(viewModel.state.value.phase).isEqualTo(PaymentPhase.CONFIRMED)
    }
}
