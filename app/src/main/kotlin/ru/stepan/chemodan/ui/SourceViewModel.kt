package ru.stepan.chemodan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stepan.chemodan.data.SourceDetails
import ru.stepan.chemodan.data.TripRepository

sealed interface SourceState {
    data object Loading : SourceState

    data class Available(val details: SourceDetails) : SourceState

    data class Failed(val error: UiError) : SourceState
}

@HiltViewModel
class SourceViewModel @Inject constructor(private val repository: TripRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<SourceState>(SourceState.Loading)
    val state = mutableState.asStateFlow().asLiveData()
    private var sourceId: Long? = null

    fun load(id: Long) {
        if (sourceId == id) return
        sourceId = id
        viewModelScope.launch {
            try {
                val details = repository.sourceDetails(id)
                if (details.source == null)
                    throw ru.stepan.chemodan.domain.InputFailure(
                        ru.stepan.chemodan.domain.FailureReason.SOURCE_NOT_FOUND
                    )
                mutableState.value = SourceState.Available(details)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = SourceState.Failed(error.toUiError())
            }
        }
    }
}

@HiltViewModel
class SelectionViewModel @Inject constructor(private val repository: TripRepository) : ViewModel() {
    fun sources(tripId: Long) = repository.observeSummary(tripId)
}
