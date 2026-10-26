package com.martdev.flickq.feature.notification.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.martdev.flickq.feature.notification.domain.NotificationPromptStore
import com.martdev.flickq.feature.notification.presentation.NotificationPermissionStatus.BLOCKED
import com.martdev.flickq.feature.notification.presentation.NotificationPermissionStatus.GRANTED
import com.martdev.flickq.feature.notification.presentation.NotificationPermissionStatus.REQUESTABLE
import com.martdev.flickq.feature.notification.presentation.NotificationPermissionStatus.UNDETERMINED
import com.martdev.flickq.feature.notification.presentation.NotificationPermissionStatus.UNSUPPORTED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private class MutableClock2(var now: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)) : Clock {
    override fun now(): Instant = now
    fun advance(by: Duration) {
        now += by
    }
}

private class FakePromptStore : NotificationPromptStore {
    var snoozedUntil: Instant? = null
    var requested = false
    override fun snoozedUntil() = snoozedUntil
    override fun snoozeUntil(until: Instant) {
        snoozedUntil = until
    }
    override fun hasRequestedPermission() = requested
    override fun markPermissionRequested() {
        requested = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationPromptViewModelTest {

    private val clock = MutableClock2()
    private val store = FakePromptStore()
    private lateinit var viewModel: NotificationPromptViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = NotificationPromptViewModel(store, clock)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun check(status: NotificationPermissionStatus) =
        viewModel.onAction(NotificationPromptAction.OnPermissionChecked(status))

    @Test
    fun `granted and unsupported hide the card`() {
        check(GRANTED)
        assertThat(viewModel.state.value.prompt).isNull()
        check(UNSUPPORTED)
        assertThat(viewModel.state.value.prompt).isNull()
    }

    @Test
    fun `requestable asks to enable`() {
        check(REQUESTABLE)
        assertThat(viewModel.state.value.prompt).isEqualTo(NotificationPrompt.ENABLE)
    }

    @Test
    fun `undetermined before any request asks to enable`() {
        check(UNDETERMINED)
        assertThat(viewModel.state.value.prompt).isEqualTo(NotificationPrompt.ENABLE)
    }

    @Test
    fun `undetermined after a request means permanently denied`() {
        store.requested = true
        check(UNDETERMINED)
        assertThat(viewModel.state.value.prompt).isEqualTo(NotificationPrompt.OPEN_SETTINGS)
    }

    @Test
    fun `blocked offers settings`() {
        check(BLOCKED)
        assertThat(viewModel.state.value.prompt).isEqualTo(NotificationPrompt.OPEN_SETTINGS)
    }

    @Test
    fun `enable records the request and asks for the permission`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationPromptAction.OnEnableClick)
            assertThat(awaitItem()).isEqualTo(NotificationPromptEvent.RequestPermission)
        }
        assertThat(store.requested).isTrue()
    }

    @Test
    fun `open settings emits the settings event`() = runTest {
        viewModel.events.test {
            viewModel.onAction(NotificationPromptAction.OnOpenSettingsClick)
            assertThat(awaitItem()).isEqualTo(NotificationPromptEvent.OpenSettings)
        }
    }

    @Test
    fun `not now hides the card for 14 days`() {
        check(REQUESTABLE)
        viewModel.onAction(NotificationPromptAction.OnNotNowClick)
        assertThat(viewModel.state.value.prompt).isNull()
        assertThat(store.snoozedUntil).isEqualTo(clock.now + 14.days)

        clock.advance(13.days)
        check(REQUESTABLE)
        assertThat(viewModel.state.value.prompt).isNull()

        clock.advance(1.days)
        check(REQUESTABLE)
        assertThat(viewModel.state.value.prompt).isEqualTo(NotificationPrompt.ENABLE)
    }

    @Test
    fun `a snooze too far in the future is ignored`() {
        store.snoozedUntil = clock.now + 30.days
        check(REQUESTABLE)
        assertThat(viewModel.state.value.prompt).isEqualTo(NotificationPrompt.ENABLE)
    }
}