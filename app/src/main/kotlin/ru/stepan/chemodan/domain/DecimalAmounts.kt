package ru.stepan.chemodan.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

internal fun Double.decimal(): BigDecimal = BigDecimal.valueOf(this)

internal fun Iterable<Double>.decimalSum(): Double =
    fold(BigDecimal.ZERO) { sum, value -> sum + value.decimal() }.toDouble()

internal fun Double.subtractAmount(other: Double): Double =
    (decimal() - other.decimal()).max(BigDecimal.ZERO).toDouble()

internal fun BigDecimal.rubles(): Double = setScale(2, RoundingMode.HALF_UP).toDouble()

internal fun currencyDigits(code: String): Int =
    runCatching { Currency.getInstance(code).defaultFractionDigits.takeIf { it >= 0 } ?: 2 }
        .getOrDefault(2)

internal fun Double.currencyAmount(currency: String): Double =
    decimal().setScale(currencyDigits(currency), RoundingMode.HALF_UP).toDouble()

internal fun requireMoney(value: Double, currency: String) {
    requireInput(
        value.isFinite() &&
            value >= 0 &&
            value <= 1_000_000_000_000.0 &&
            value.decimal().stripTrailingZeros().scale() <= currencyDigits(currency),
        FailureReason.AMOUNT_PRECISION,
        currency,
        currencyDigits(currency),
    )
}
