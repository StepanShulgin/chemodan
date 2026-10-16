package ru.stepan.chemodan.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.stepan.chemodan.data.ExchangeSourceDraft
import ru.stepan.chemodan.data.ExchangeStep
import ru.stepan.chemodan.data.ExpenseAllocation
import ru.stepan.chemodan.data.ReferenceRateEntity
import ru.stepan.chemodan.data.TripRepository
import ru.stepan.chemodan.data.TripSummary
import ru.stepan.chemodan.domain.BudgetCalculator

@HiltViewModel
class TripDetailsViewModel
@Inject
constructor(
    private val repository: TripRepository,
    private val budgetCalculator: BudgetCalculator,
) : ViewModel() {
    private val mutableSummary = MutableLiveData<TripSummary?>()
    val summary: LiveData<TripSummary?> = mutableSummary
    private val sourceOperation = SaveOperation<Unit>(viewModelScope)
    val sourceSaveState = sourceOperation.state
    private val expenseOperation = SaveOperation<Unit>(viewModelScope)
    val expenseSaveState = expenseOperation.state
    private val rateLoader = RateLoader(viewModelScope, repository::referenceRate)
    val automaticRate = rateLoader.state
    private var loadedTripId: Long? = null
    private var summaryJob: Job? = null

    fun acknowledgeSourceSave() = sourceOperation.acknowledge()

    fun acknowledgeExpenseSave() = expenseOperation.acknowledge()

    fun budgetStatus(budget: Double, spent: Double) = budgetCalculator.calculate(budget, spent)

    fun load(tripId: Long) {
        if (loadedTripId == tripId && summaryJob?.isActive == true) return
        loadedTripId = tripId
        summaryJob?.cancel()
        mutableSummary.value = null
        summaryJob =
            viewModelScope.launch {
                repository.observeSummary(tripId).collect { mutableSummary.value = it }
            }
    }

    fun addSource(tripId: Long, title: String, steps: List<ExchangeStep>) =
        sourceOperation.submit {
            repository.addSource(tripId, ExchangeSourceDraft(title, steps))
            Unit
        }

    fun updateSource(tripId: Long, sourceId: Long, title: String, steps: List<ExchangeStep>) =
        sourceOperation.submit {
            repository.updateSource(tripId, sourceId, ExchangeSourceDraft(title, steps))
        }

    fun addExpense(tripId: Long, sourceId: Long, title: String, amount: String) =
        expenseOperation.submit { repository.addExpense(tripId, sourceId, title, number(amount)) }

    fun addSplitExpense(
        tripId: Long,
        title: String,
        total: Double,
        currency: String,
        allocations: List<ExpenseAllocation>,
    ) =
        expenseOperation.submit {
            repository.addSplitExpense(tripId, title, total, currency, allocations)
        }

    fun loadAutomaticRate(currency: String, force: Boolean = false) {
        if (force) rateLoader.load("RUB", currency) else rateLoader.loadIfNeeded("RUB", currency)
    }

    fun addAutomaticExpense(
        tripId: Long,
        title: String,
        amount: String,
        rate: ReferenceRateEntity,
    ) =
        expenseOperation.submit {
            repository.addAutomaticExpense(tripId, title, number(amount), rate)
        }
}
