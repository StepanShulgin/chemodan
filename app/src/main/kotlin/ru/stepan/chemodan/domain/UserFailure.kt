package ru.stepan.chemodan.domain

enum class FailureReason {
    INVALID_DATES,
    TRIP_NOT_FOUND,
    EMPTY_DESTINATION,
    INVALID_CURRENCY,
    INVALID_BUDGET,
    SOURCE_NOT_FOUND,
    WRONG_TRIP,
    USED_SOURCE_CURRENCY,
    SOURCE_BELOW_SPENT,
    EMPTY_EXPENSE_TITLE,
    INVALID_EXPENSE_AMOUNT,
    INVALID_SOURCES,
    INVALID_ALLOCATIONS,
    WRONG_EXPENSE_CURRENCY,
    REFRESH_RUBLE_RATE,
    EMPTY_SOURCE_TITLE,
    EMPTY_CHAIN,
    WRONG_STEP_CURRENCY,
    INVALID_EXCHANGE_AMOUNT,
    INVALID_COMMISSION,
    INVALID_RECEIVED_AMOUNT,
    INCOMPLETE_STEP,
    SAME_CURRENCY,
    INVALID_FINAL_AMOUNTS,
    INSUFFICIENT_FUNDS,
    RATE_UNAVAILABLE,
    NEGATIVE_SPENDING,
    DNS,
    TIMEOUT,
    TLS,
    UNSUPPORTED_RATE,
    RATE_LIMIT,
    SERVER,
    HTTP,
    NETWORK,
    INVALID_RATE,
    UNEXPECTED,
    AMOUNT_PRECISION,
}

interface UserFacingFailure {
    val reason: FailureReason
    val arguments: List<Any>
}

class InputFailure(
    override val reason: FailureReason,
    override val arguments: List<Any> = emptyList(),
) : IllegalArgumentException(reason.name), UserFacingFailure

internal fun requireInput(condition: Boolean, reason: FailureReason, vararg arguments: Any) {
    if (!condition) throw InputFailure(reason, arguments.toList())
}

class RateRequestFailure(
    override val reason: FailureReason,
    override val arguments: List<Any>,
    cause: Exception,
) : IllegalStateException(reason.name, cause), UserFacingFailure
