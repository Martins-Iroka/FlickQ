package com.martdev.flickq.features.payment.domain.model

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import com.martdev.flickq.payment.model.PaymentStatus
import com.martdev.flickq.payment.model.PaymentStatus.ABANDONED
import com.martdev.flickq.payment.model.PaymentStatus.FAILED
import com.martdev.flickq.payment.model.PaymentStatus.INITIATED
import com.martdev.flickq.payment.model.PaymentStatus.PENDING
import com.martdev.flickq.payment.model.PaymentStatus.REFUNDED
import com.martdev.flickq.payment.model.PaymentStatus.REFUND_FAILED
import com.martdev.flickq.payment.model.PaymentStatus.REFUND_PENDING
import com.martdev.flickq.payment.model.PaymentStatus.SUCCESS
import org.junit.jupiter.api.Test

class PaymentTransitionsTest {

    /** The spec. Written out independently of the implementation, so editing either one alone fails the test. */
    private val allowed = setOf(
        INITIATED to PENDING, INITIATED to SUCCESS, INITIATED to FAILED, INITIATED to ABANDONED,
        PENDING to SUCCESS, PENDING to FAILED, PENDING to ABANDONED,
        ABANDONED to PENDING, ABANDONED to SUCCESS, ABANDONED to FAILED,
        SUCCESS to REFUND_PENDING, SUCCESS to REFUNDED,
        REFUND_PENDING to REFUNDED, REFUND_PENDING to REFUND_FAILED,
        REFUND_FAILED to REFUND_PENDING, REFUND_FAILED to REFUNDED,
    )

    @Test
    fun `every from-to pair matches the spec`() {
        val mismatches = PaymentStatus.entries.flatMap { from ->
            println("FlatMap entry is $from")
            PaymentStatus.entries.mapNotNull { to ->
                println("MapNotNull entry is $to")
                val expected = (from to to) in allowed
                if (PaymentTransitions.canTransition(from, to) != expected) "$from → $to (expected $expected)" else null
            }
        }
        assertThat(mismatches).isEmpty()      // a failure names every wrong pair
    }

    @Test
    fun `the replayed-charge bug is closed - nothing in the refund lifecycle goes back to SUCCESS`() {
        listOf(REFUND_PENDING, REFUND_FAILED, REFUNDED).forEach {
            assertThat(PaymentTransitions.canTransition(it, SUCCESS), "from $it").isFalse()
        }
    }

    @Test
    fun `REFUNDED and FAILED are final`() {
        PaymentStatus.entries.forEach { to ->
            assertThat(PaymentTransitions.canTransition(REFUNDED, to), "REFUNDED → $to").isFalse()
            assertThat(PaymentTransitions.canTransition(FAILED, to), "FAILED → $to").isFalse()
        }
    }

    @Test
    fun `no status is its own transition`() {
        PaymentStatus.entries.forEach {
            assertThat(PaymentTransitions.canTransition(it, it), "$it → $it").isFalse()
        }
    }

    @Test
    fun `chargeable and refundable sets`() {
        assertThat(PaymentTransitions.CHARGEABLE).containsExactlyInAnyOrder(INITIATED, PENDING, ABANDONED)
        assertThat(PaymentTransitions.REFUNDABLE).containsExactlyInAnyOrder(SUCCESS, REFUND_FAILED)
    }
}