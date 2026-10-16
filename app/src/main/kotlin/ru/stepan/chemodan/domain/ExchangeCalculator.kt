package ru.stepan.chemodan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import ru.stepan.chemodan.data.ExchangeSourceDraft

data class ValidatedSource(
    val currency: String,
    val amount: Double,
    val rubleCost: Double,
    val description: String,
)

class ExchangeCalculator {
    fun validate(draft: ExchangeSourceDraft): ValidatedSource {
        requireInput(draft.title.isNotBlank(), FailureReason.EMPTY_SOURCE_TITLE)
        requireInput(draft.steps.isNotEmpty(), FailureReason.EMPTY_CHAIN)
        var expectedCurrency = "RUB"
        var expectedAmount: Double? = null
        draft.steps.forEach { step ->
            val from = step.fromCurrency.trim().uppercase(Locale.ROOT)
            val to = step.toCurrency.trim().uppercase(Locale.ROOT)
            requireInput(
                from.matches(Regex("[A-Z]{3}")) && to.matches(Regex("[A-Z]{3}")),
                FailureReason.INVALID_CURRENCY,
            )
            requireInput(
                from == expectedCurrency,
                FailureReason.WRONG_STEP_CURRENCY,
                expectedCurrency,
            )
            requireInput(
                step.amountGiven.isFinite() && step.amountGiven > 0.0,
                FailureReason.INVALID_EXCHANGE_AMOUNT,
            )
            requireInput(
                step.fee.isFinite() && step.fee >= 0.0 && step.fee < step.amountGiven,
                FailureReason.INVALID_COMMISSION,
            )
            requireInput(
                step.amountReceived.isFinite() && step.amountReceived > 0.0,
                FailureReason.INVALID_RECEIVED_AMOUNT,
            )
            requireMoney(step.amountGiven, from)
            requireMoney(step.fee, from)
            requireMoney(step.amountReceived, to)
            expectedAmount?.let {
                requireInput(
                    closeEnough(step.amountGiven, it),
                    FailureReason.INCOMPLETE_STEP,
                    it,
                    from,
                )
            }
            requireInput(from != to, FailureReason.SAME_CURRENCY)
            expectedCurrency = to
            expectedAmount = step.amountReceived
        }
        val first = draft.steps.first()
        val last = draft.steps.last()
        val rubles = round(first.amountGiven)
        val amount = last.amountReceived
        requireInput(rubles > 0 && amount > 0, FailureReason.INVALID_FINAL_AMOUNTS)
        val summary =
            draft.steps.joinToString(" → ") { step ->
                val from = step.fromCurrency.trim().uppercase(Locale.ROOT)
                val to = step.toCurrency.trim().uppercase(Locale.ROOT)
                val effectiveRate = step.amountReceived / (step.amountGiven - step.fee)
                "${step.amountGiven} $from − ${step.fee} → ${step.amountReceived} $to ($effectiveRate $to/$from)"
            }
        return ValidatedSource(expectedCurrency, amount, rubles, summary)
    }

    fun allocateRubleCost(
        amount: Double,
        remainingAmount: Double,
        remainingRubles: Double,
    ): Double {
        requireInput(amount.isFinite() && amount > 0.0, FailureReason.INVALID_EXPENSE_AMOUNT)
        require(remainingAmount.isFinite() && remainingAmount >= 0.0)
        require(remainingRubles.isFinite() && remainingRubles >= 0.0)
        requireInput(
            amount.decimal() <= remainingAmount.decimal(),
            FailureReason.INSUFFICIENT_FUNDS,
        )
        if (amount.decimal().compareTo(remainingAmount.decimal()) == 0)
            return round(remainingRubles)
        return remainingRubles
            .decimal()
            .multiply(amount.decimal())
            .divide(remainingAmount.decimal(), 2, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun estimateRubleCost(amount: Double, quotePerRuble: Double): Double {
        requireInput(amount.isFinite() && amount > 0.0, FailureReason.INVALID_EXPENSE_AMOUNT)
        requireInput(
            quotePerRuble.isFinite() && quotePerRuble > 0.0,
            FailureReason.RATE_UNAVAILABLE,
        )
        return amount
            .decimal()
            .divide(quotePerRuble.decimal(), 2, RoundingMode.HALF_UP)
            .toDouble()
            .also { requireMoney(it, "RUB") }
    }

    private fun closeEnough(first: Double, second: Double) =
        first.decimal().compareTo(second.decimal()) == 0

    private fun round(value: Double): Double =
        BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
}
