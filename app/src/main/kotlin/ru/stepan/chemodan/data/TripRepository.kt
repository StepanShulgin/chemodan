package ru.stepan.chemodan.data

import androidx.room.withTransaction
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.stepan.chemodan.domain.ExchangeCalculator
import ru.stepan.chemodan.domain.FailureReason
import ru.stepan.chemodan.domain.InputFailure
import ru.stepan.chemodan.domain.ValidatedSource
import ru.stepan.chemodan.domain.currencyAmount
import ru.stepan.chemodan.domain.decimal
import ru.stepan.chemodan.domain.decimalSum
import ru.stepan.chemodan.domain.requireInput
import ru.stepan.chemodan.domain.requireMoney
import ru.stepan.chemodan.domain.subtractAmount

class TripRepository
@Inject
constructor(
    private val database: ChemodanDatabase,
    private val api: FrankfurterApi,
    private val calculator: ExchangeCalculator,
) {
    private val dao = database.tripDao()

    private fun ExchangeSourceEntity.normalizedBalance() =
        copy(
            remainingAmount = remainingAmount.currencyAmount(currency),
            remainingRubles = remainingRubles.currencyAmount("RUB"),
        )

    fun observeTrips(): Flow<List<TripEntity>> = dao.observeTrips()

    suspend fun createTrip(
        destination: String,
        country: Country,
        budget: Double,
        start: Long,
        end: Long,
    ): Long {
        validateTrip(destination, country.currency, budget)
        requireInput(start > 0 && end >= start, FailureReason.INVALID_DATES)
        return dao.insertTrip(
            TripEntity(
                destination = destination.trim(),
                currency = country.currency,
                budget = budget,
                countryCode = country.code,
                startDate = start,
                endDate = end,
            )
        )
    }

    suspend fun updateTrip(
        id: Long,
        destination: String,
        country: Country,
        budget: Double,
        start: Long,
        end: Long,
    ) {
        validateTrip(destination, country.currency, budget)
        requireInput(start > 0 && end >= start, FailureReason.INVALID_DATES)
        val trip = dao.getTrip(id) ?: throw InputFailure(FailureReason.TRIP_NOT_FOUND)
        dao.updateTrip(
            trip.copy(
                destination = destination.trim(),
                currency = country.currency,
                budget = budget,
                countryCode = country.code,
                startDate = start,
                endDate = end,
                legacyBudget = null,
            )
        )
    }

    suspend fun getTrip(id: Long) = dao.getTrip(id)

    suspend fun sourceDetails(id: Long) =
        database.withTransaction {
            SourceDetails(dao.getSource(id)?.normalizedBalance(), dao.getSteps(id))
        }

    private fun validateTrip(destination: String, currency: String, budget: Double) {
        requireInput(destination.isNotBlank(), FailureReason.EMPTY_DESTINATION)
        requireInput(currency.matches(Regex("[A-Za-z]{3}")), FailureReason.INVALID_CURRENCY)
        requireInput(budget.isFinite() && budget > 0.0, FailureReason.INVALID_BUDGET)
        requireMoney(budget, "RUB")
    }

    fun observeSummary(tripId: Long): Flow<TripSummary?> =
        combine(dao.observeTrip(tripId), dao.observeSources(tripId), dao.observeExpenses(tripId)) {
            trip,
            sources,
            expenses ->
            trip?.let {
                TripSummary(
                    trip = it,
                    spent = expenses.map(ExpenseEntity::rubleCost).decimalSum(),
                    spentRubles =
                        expenses
                            .filter { expense ->
                                expense.calculationMode == ExpenseMode.MANUAL_CHAIN
                            }
                            .map(ExpenseEntity::rubleCost)
                            .decimalSum(),
                    estimatedRubles =
                        expenses
                            .filter { expense ->
                                expense.calculationMode == ExpenseMode.AUTOMATIC_RATE
                            }
                            .map(ExpenseEntity::rubleCost)
                            .decimalSum(),
                    sources = sources.map { source -> source.normalizedBalance() },
                    expenses = expenses,
                )
            }
        }

    suspend fun addSource(tripId: Long, draft: ExchangeSourceDraft): Long {
        dao.getTrip(tripId) ?: throw InputFailure(FailureReason.TRIP_NOT_FOUND)
        val source: ValidatedSource = calculator.validate(draft)
        val entity =
            ExchangeSourceEntity(
                tripId = tripId,
                title = draft.title.trim(),
                currency = source.currency,
                originalAmount = source.amount,
                remainingAmount = source.amount,
                originalRubles = source.rubleCost,
                remainingRubles = source.rubleCost,
                chainDescription = source.description,
            )
        return database.withTransaction { dao.addSourceWithSteps(entity, sourceSteps(draft, 0)) }
    }

    suspend fun updateSource(tripId: Long, sourceId: Long, draft: ExchangeSourceDraft) {
        val validated = calculator.validate(draft)
        database.withTransaction {
            val current =
                dao.getSource(sourceId)?.normalizedBalance()
                    ?: throw InputFailure(FailureReason.SOURCE_NOT_FOUND)
            requireInput(current.tripId == tripId, FailureReason.WRONG_TRIP)
            val expenses = dao.getSourceExpenses(sourceId)
            requireInput(
                expenses.isEmpty() || validated.currency == current.currency,
                FailureReason.USED_SOURCE_CURRENCY,
            )
            requireInput(
                expenses.map { it.amount }.decimalSum() <= validated.amount,
                FailureReason.SOURCE_BELOW_SPENT,
            )
            var remainingAmount = validated.amount
            var remainingRubles = validated.rubleCost
            val recalculated =
                expenses.map { expense ->
                    val cost =
                        calculator.allocateRubleCost(
                            expense.amount,
                            remainingAmount,
                            remainingRubles,
                        )
                    remainingAmount = remainingAmount.subtractAmount(expense.amount)
                    remainingRubles = remainingRubles.subtractAmount(cost)
                    expense.copy(rubleCost = cost)
                }
            dao.updateSource(
                current.copy(
                    title = draft.title.trim(),
                    currency = validated.currency,
                    originalAmount = validated.amount,
                    originalRubles = validated.rubleCost,
                    remainingAmount = remainingAmount,
                    remainingRubles = remainingRubles,
                    chainDescription = validated.description,
                )
            )
            dao.updateExpenses(recalculated)
            dao.deleteSteps(sourceId)
            dao.insertExchangeSteps(sourceSteps(draft, sourceId))
        }
    }

    private fun sourceSteps(draft: ExchangeSourceDraft, sourceId: Long) =
        draft.steps.mapIndexed { index, step ->
            ExchangeStepEntity(
                sourceId = sourceId,
                stepIndex = index,
                fromCurrency = step.fromCurrency.trim().uppercase(Locale.ROOT),
                amountGiven = step.amountGiven,
                fee = step.fee,
                toCurrency = step.toCurrency.trim().uppercase(Locale.ROOT),
                amountReceived = step.amountReceived,
                effectiveRate = step.amountReceived / (step.amountGiven - step.fee),
            )
        }

    suspend fun addExpense(tripId: Long, sourceId: Long, title: String, amount: Double) {
        database.withTransaction {
            val source =
                dao.getSource(sourceId)?.normalizedBalance()
                    ?: throw InputFailure(FailureReason.SOURCE_NOT_FOUND)
            addSplitExpense(
                tripId,
                title,
                amount,
                source.currency,
                listOf(ExpenseAllocation(sourceId, amount)),
            )
        }
    }

    suspend fun addSplitExpense(
        tripId: Long,
        title: String,
        total: Double,
        currency: String,
        allocations: List<ExpenseAllocation>,
    ) {
        requireInput(title.isNotBlank(), FailureReason.EMPTY_EXPENSE_TITLE)
        requireInput(total.isFinite() && total > 0, FailureReason.INVALID_EXPENSE_AMOUNT)
        requireMoney(total, currency)
        requireInput(
            allocations.size in 1..2 &&
                allocations.map { it.sourceId }.distinct().size == allocations.size,
            FailureReason.INVALID_SOURCES,
        )
        requireInput(
            allocations.all { it.amount.isFinite() && it.amount > 0 } &&
                allocations.map { it.amount }.decimalSum().decimal().compareTo(total.decimal()) ==
                    0,
            FailureReason.INVALID_ALLOCATIONS,
        )
        database.withTransaction {
            dao.getTrip(tripId) ?: throw InputFailure(FailureReason.TRIP_NOT_FOUND)
            val groupId = if (allocations.size > 1) java.util.UUID.randomUUID().toString() else null
            val createdAt = System.currentTimeMillis()
            allocations.forEach { (sourceId, amount) ->
                requireMoney(amount, currency)
                val source =
                    dao.getSource(sourceId)?.normalizedBalance()
                        ?: throw InputFailure(FailureReason.SOURCE_NOT_FOUND)
                requireInput(source.tripId == tripId, FailureReason.WRONG_TRIP)
                requireInput(source.currency == currency, FailureReason.WRONG_EXPENSE_CURRENCY)
                val cost =
                    calculator.allocateRubleCost(
                        amount,
                        source.remainingAmount,
                        source.remainingRubles,
                    )
                dao.addExpenseFromSource(
                    ExpenseEntity(
                        tripId = tripId,
                        sourceId = sourceId,
                        title = title.trim(),
                        amount = amount,
                        rubleCost = cost,
                        currency = currency,
                        calculationMode = ExpenseMode.MANUAL_CHAIN,
                        referenceRate = null,
                        referenceRateDate = null,
                        createdAt = createdAt,
                        groupId = groupId,
                    ),
                    source.copy(
                        remainingAmount = source.remainingAmount.subtractAmount(amount),
                        remainingRubles = source.remainingRubles.subtractAmount(cost),
                    ),
                )
            }
        }
    }

    suspend fun addAutomaticExpense(
        tripId: Long,
        title: String,
        amount: Double,
        referenceRate: ReferenceRateEntity,
    ) {
        requireInput(title.isNotBlank(), FailureReason.EMPTY_EXPENSE_TITLE)
        dao.getTrip(tripId) ?: throw InputFailure(FailureReason.TRIP_NOT_FOUND)
        requireInput(referenceRate.base == "RUB", FailureReason.REFRESH_RUBLE_RATE)
        requireMoney(amount, referenceRate.quote)
        val rubleCost = calculator.estimateRubleCost(amount, referenceRate.rate)
        dao.insertExpense(
            ExpenseEntity(
                tripId = tripId,
                sourceId = null,
                title = title.trim(),
                amount = amount,
                currency = referenceRate.quote,
                rubleCost = rubleCost,
                calculationMode = ExpenseMode.AUTOMATIC_RATE,
                referenceRate = referenceRate.rate,
                referenceRateDate = referenceRate.date,
            )
        )
    }

    suspend fun referenceRate(base: String, quote: String): ReferenceRateResult {
        val normalizedBase = base.trim().uppercase(Locale.ROOT)
        val normalizedQuote = quote.trim().uppercase(Locale.ROOT)
        requireInput(
            normalizedBase.matches(Regex("[A-Z]{3}")) && normalizedQuote.matches(Regex("[A-Z]{3}")),
            FailureReason.INVALID_CURRENCY,
        )
        if (normalizedBase == normalizedQuote)
            return ReferenceRateResult(
                ReferenceRateEntity(
                    normalizedBase,
                    normalizedQuote,
                    1.0,
                    java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date()),
                ),
                false,
            )
        return try {
            val remote =
                api.getRate(
                    normalizedBase.lowercase(Locale.ROOT),
                    normalizedQuote.lowercase(Locale.ROOT),
                )
            coroutineContext.ensureActive()
            require(
                remote.rate.isFinite() &&
                    remote.rate > 0 &&
                    remote.base.equals(normalizedBase, true) &&
                    remote.quote.equals(normalizedQuote, true)
            )
            val rate =
                ReferenceRateEntity(
                    remote.base.uppercase(Locale.ROOT),
                    remote.quote.uppercase(Locale.ROOT),
                    remote.rate,
                    remote.date,
                )
            dao.insertRate(rate)
            ReferenceRateResult(rate, false)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            coroutineContext.ensureActive()
            val cached =
                dao.getRate(normalizedBase, normalizedQuote)
                    ?: throw rateFailure(error, normalizedBase, normalizedQuote)
            ReferenceRateResult(cached, true)
        }
    }
}
