package ru.stepan.chemodan.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import ru.stepan.chemodan.data.ReferenceRateResult

sealed interface SaveState<out T> {
    data object Idle : SaveState<Nothing>

    data object Saving : SaveState<Nothing>

    data class Saved<T>(val value: T) : SaveState<T>

    data class Failed(val error: UiError) : SaveState<Nothing>
}

internal class SaveOperation<T>(private val scope: CoroutineScope) {
    private val mutableState = MutableLiveData<SaveState<T>>(SaveState.Idle)
    val state: LiveData<SaveState<T>> = mutableState

    fun submit(action: suspend () -> T) {
        if (mutableState.value == SaveState.Saving || mutableState.value is SaveState.Saved) return
        mutableState.value = SaveState.Saving
        scope.launch {
            try {
                val result = action()
                coroutineContext.ensureActive()
                mutableState.value = SaveState.Saved(result)
            } catch (cancelled: CancellationException) {
                mutableState.value = SaveState.Idle
                throw cancelled
            } catch (error: Exception) {
                coroutineContext.ensureActive()
                mutableState.value = SaveState.Failed(error.toUiError())
            }
        }
    }

    fun acknowledge() {
        if (mutableState.value != SaveState.Saving) mutableState.value = SaveState.Idle
    }
}

sealed interface RateUiState {
    data object Idle : RateUiState

    data class Loading(val base: String, val quote: String) : RateUiState

    data class Available(val result: ReferenceRateResult) : RateUiState

    data class Unavailable(val base: String, val quote: String, val error: UiError) : RateUiState
}

internal class RateLoader(
    private val scope: CoroutineScope,
    private val request: suspend (String, String) -> ReferenceRateResult,
) {
    private val mutableState = MutableLiveData<RateUiState>(RateUiState.Idle)
    val state: LiveData<RateUiState> = mutableState
    private var job: Job? = null

    fun loadIfNeeded(base: String, quote: String) {
        val matches =
            when (val current = mutableState.value) {
                is RateUiState.Loading -> current.base == base && current.quote == quote
                is RateUiState.Available ->
                    current.result.rate.let { it.base == base && it.quote == quote }
                is RateUiState.Unavailable -> current.base == base && current.quote == quote
                else -> false
            }
        if (!matches) load(base, quote)
    }

    fun load(base: String, quote: String) {
        job?.cancel()
        mutableState.value = RateUiState.Loading(base, quote)
        job =
            scope.launch {
                try {
                    val result = request(base, quote)
                    coroutineContext.ensureActive()
                    mutableState.value = RateUiState.Available(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    coroutineContext.ensureActive()
                    mutableState.value = RateUiState.Unavailable(base, quote, error.toUiError())
                }
            }
    }
}
