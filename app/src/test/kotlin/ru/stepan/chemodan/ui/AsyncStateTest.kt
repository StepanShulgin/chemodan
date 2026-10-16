package ru.stepan.chemodan.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.stepan.chemodan.data.ReferenceRateEntity
import ru.stepan.chemodan.data.ReferenceRateResult
import ru.stepan.chemodan.domain.FailureReason

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AsyncStateTest {
    @get:Rule val instantExecutor = InstantTaskExecutorRule()

    @Test
    fun `restoring the same screen does not restart a pending or completed request`() = runTest {
        var calls = 0
        val completion = CompletableDeferred<ReferenceRateResult>()
        val loader =
            RateLoader(this) { _, _ ->
                calls++
                completion.await()
            }
        loader.loadIfNeeded("RUB", "USD")
        runCurrent()
        loader.loadIfNeeded("RUB", "USD")
        runCurrent()
        assertEquals(1, calls)
        completion.complete(
            ReferenceRateResult(ReferenceRateEntity("RUB", "USD", .01, "2026-10-15"), false)
        )
        runCurrent()
        loader.loadIfNeeded("RUB", "USD")
        runCurrent()
        assertEquals(1, calls)
        loader.load("RUB", "USD")
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `pending and unacknowledged saves cannot be submitted twice`() = runTest {
        val operation = SaveOperation<Long>(this)
        val completion = CompletableDeferred<Long>()
        var calls = 0
        val save: suspend () -> Long = {
            calls++
            completion.await()
        }
        operation.submit(save)
        operation.submit(save)
        runCurrent()
        assertEquals(1, calls)
        assertEquals(SaveState.Saving, operation.state.value)
        completion.complete(42)
        runCurrent()
        assertEquals(SaveState.Saved(42L), operation.state.value)
        operation.submit(save)
        assertEquals(1, calls)
        operation.acknowledge()
        operation.submit(save)
        runCurrent()
        assertEquals(2, calls)
    }

    @Test
    fun `failed save can be retried`() = runTest {
        val operation = SaveOperation<Unit>(this)
        operation.submit { throw IOException("offline") }
        runCurrent()
        assertEquals(SaveState.Failed(UiError(FailureReason.UNEXPECTED)), operation.state.value)
        operation.submit {}
        runCurrent()
        assertEquals(SaveState.Saved(Unit), operation.state.value)
    }

    @Test
    fun `late error from cancelled request cannot overwrite current rate`() = runTest {
        val release = CompletableDeferred<Unit>()
        val expected =
            ReferenceRateResult(ReferenceRateEntity("RUB", "THB", 0.4, "2026-10-15"), false)
        val loader =
            RateLoader(this) { _, quote ->
                if (quote == "USD")
                    withContext(NonCancellable) {
                        release.await()
                        throw IOException("late")
                    }
                expected
            }
        loader.load("RUB", "USD")
        runCurrent()
        loader.load("RUB", "THB")
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals(RateUiState.Available(expected), loader.state.value)
    }

    @Test
    fun `late success from cancelled request cannot overwrite current error`() = runTest {
        val release = CompletableDeferred<Unit>()
        val loader =
            RateLoader(this) { _, quote ->
                if (quote == "USD")
                    withContext(NonCancellable) {
                        release.await()
                        ReferenceRateResult(
                            ReferenceRateEntity("RUB", "USD", .01, "2026-10-15"),
                            false,
                        )
                    }
                else throw IOException("offline")
            }
        loader.load("RUB", "USD")
        runCurrent()
        loader.load("RUB", "THB")
        runCurrent()
        release.complete(Unit)
        runCurrent()
        assertEquals(
            RateUiState.Unavailable("RUB", "THB", UiError(FailureReason.UNEXPECTED)),
            loader.state.value,
        )
    }

    @Test
    fun `network error remains until an explicit retry`() = runTest {
        val loader = RateLoader(this) { _, _ -> throw IOException("offline") }
        loader.load("RUB", "USD")
        assertTrue(loader.state.value is RateUiState.Loading)
        runCurrent()
        assertEquals(
            RateUiState.Unavailable("RUB", "USD", UiError(FailureReason.UNEXPECTED)),
            loader.state.value,
        )
        loader.load("RUB", "USD")
        assertTrue(loader.state.value is RateUiState.Loading)
        runCurrent()
    }
}
