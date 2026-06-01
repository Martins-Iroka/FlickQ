package com.martdev.flickq.feature.admin.presentation.reservations

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.martdev.flickq.feature.admin.presentation.FakeAdminPaymentRepository
import com.martdev.flickq.feature.admin.presentation.FakeAdminReservationRepository
import com.martdev.flickq.payment.model.Payment
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.reservation.model.ReservationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReservationDetailViewModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `loads the reservation and its payments`() = runTest {
        val payments = listOf(Payment(id = 1, reservationId = 5, reference = "FQ-1", status = PaymentStatus.SUCCESS))
        val vm = AdminReservationDetailViewModel(5, FakeAdminReservationRepository(), FakeAdminPaymentRepository(payments))

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.reservation).isNotNull()
        assertThat(vm.state.value.payments).hasSize(1)
        assertThat(vm.state.value.canCancel).isTrue()
    }

    @Test
    fun `cancelling flips the status and is no longer cancellable`() = runTest {
        val repo = FakeAdminReservationRepository()
        val vm = AdminReservationDetailViewModel(5, repo, FakeAdminPaymentRepository())

        vm.onAction(AdminReservationDetailAction.OnCancelClick)
        vm.onAction(AdminReservationDetailAction.OnConfirmCancel)

        assertThat(repo.cancelCount).isEqualTo(1)
        assertThat(vm.state.value.reservation?.status).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(vm.state.value.canCancel).isFalse()
    }

    @Test
    fun `a payments lookup failure keeps the reservation visible`() = runTest {
        val vm = AdminReservationDetailViewModel(5, FakeAdminReservationRepository(), FakeAdminPaymentRepository(fails = true))

        assertThat(vm.state.value.reservation).isNotNull()
        assertThat(vm.state.value.message).isNotNull()
    }
}
