package ru.stepan.chemodan.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import ru.stepan.chemodan.data.Country
import ru.stepan.chemodan.data.TripEntity
import ru.stepan.chemodan.data.TripRepository

@HiltViewModel
class TripsViewModel @Inject constructor(private val repository: TripRepository) : ViewModel() {
    val trips = repository.observeTrips()
    private val saveOperation = SaveOperation<Long>(viewModelScope)
    val saveState = saveOperation.state
    private val mutableTrip = MutableLiveData<TripEntity?>()
    val trip: LiveData<TripEntity?> = mutableTrip

    fun acknowledgeSave() = saveOperation.acknowledge()

    fun createTrip(destination: String, country: Country, budget: String, start: Long, end: Long) =
        saveOperation.submit {
            repository.createTrip(destination, country, number(budget), start, end)
        }

    fun updateTrip(
        id: Long,
        destination: String,
        country: Country,
        budget: String,
        start: Long,
        end: Long,
    ) =
        saveOperation.submit {
            repository.updateTrip(id, destination, country, number(budget), start, end)
            id
        }

    fun loadTrip(id: Long) = viewModelScope.launch { mutableTrip.value = repository.getTrip(id) }
}
