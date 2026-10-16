package ru.stepan.chemodan.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destination: String,
    val currency: String,
    val budget: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val countryCode: String = "",
    val startDate: Long = 0,
    val endDate: Long = 0,
    val legacyBudget: Double? = null,
)

@Entity(tableName = "exchange_sources")
data class ExchangeSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val currency: String,
    val originalAmount: Double,
    val remainingAmount: Double,
    val originalRubles: Double,
    val remainingRubles: Double,
    val chainDescription: String,
)

@Entity(tableName = "exchange_steps", indices = [Index("sourceId")])
data class ExchangeStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val stepIndex: Int,
    val fromCurrency: String,
    val amountGiven: Double,
    val fee: Double,
    val toCurrency: String,
    val amountReceived: Double,
    val effectiveRate: Double,
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val sourceId: Long?,
    val title: String,
    val amount: Double,
    val currency: String,
    val rubleCost: Double,
    val calculationMode: String,
    val referenceRate: Double?,
    val referenceRateDate: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val groupId: String? = null,
)

object ExpenseMode {
    const val MANUAL_CHAIN = "MANUAL_CHAIN"
    const val AUTOMATIC_RATE = "AUTOMATIC_RATE"
}

@Entity(tableName = "reference_rates", primaryKeys = ["base", "quote"])
data class ReferenceRateEntity(
    val base: String,
    val quote: String,
    val rate: Double,
    val date: String,
    val savedAt: Long = System.currentTimeMillis(),
)

data class ExchangeStep(
    val fromCurrency: String,
    val amountGiven: Double,
    val fee: Double,
    val toCurrency: String,
    val amountReceived: Double,
)

data class ExchangeSourceDraft(val title: String, val steps: List<ExchangeStep>)

data class TripSummary(
    val trip: TripEntity,
    val spent: Double,
    val spentRubles: Double,
    val estimatedRubles: Double,
    val sources: List<ExchangeSourceEntity>,
    val expenses: List<ExpenseEntity>,
)
