package ru.stepan.chemodan.data

data class ReferenceRateResult(val rate: ReferenceRateEntity, val isCached: Boolean)

data class ExpenseAllocation(val sourceId: Long, val amount: Double)

data class SourceDetails(val source: ExchangeSourceEntity?, val steps: List<ExchangeStepEntity>)
