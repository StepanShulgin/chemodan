package ru.stepan.chemodan.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ru.stepan.chemodan.data.TripRepository

@HiltViewModel
class RateViewModel @Inject constructor(repository: TripRepository) : ViewModel() {
    private val loader = RateLoader(viewModelScope, repository::referenceRate)
    val state = loader.state

    fun load(base: String, quote: String) = loader.load(base.trim(), quote.trim())

    fun loadIfNeeded(base: String, quote: String) = loader.loadIfNeeded(base.trim(), quote.trim())
}
