package ru.stepan.chemodan.domain

data class BudgetStatus(val spent: Double, val remaining: Double, val progress: Float)

class BudgetCalculator {
    fun calculate(budget: Double, spent: Double): BudgetStatus {
        requireInput(budget.isFinite() && budget > 0.0, FailureReason.INVALID_BUDGET)
        requireInput(spent.isFinite() && spent >= 0.0, FailureReason.NEGATIVE_SPENDING)
        val remaining = (budget.decimal() - spent.decimal()).rubles()
        return BudgetStatus(spent, remaining, (spent / budget).coerceIn(0.0, 1.0).toFloat())
    }
}
