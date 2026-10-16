package ru.stepan.chemodan.ui

import android.content.Context
import ru.stepan.chemodan.R
import ru.stepan.chemodan.domain.FailureReason
import ru.stepan.chemodan.domain.UserFacingFailure

data class UiError(val reason: FailureReason, val arguments: List<Any> = emptyList())

internal fun Throwable.toUiError(): UiError =
    if (this is UserFacingFailure) UiError(reason, arguments) else UiError(FailureReason.UNEXPECTED)

internal fun Context.errorText(error: UiError): String {
    val id =
        when (error.reason) {
            FailureReason.AMOUNT_PRECISION -> R.string.error_amount_precision
            FailureReason.INVALID_DATES -> R.string.error_invalid_dates
            FailureReason.TRIP_NOT_FOUND -> R.string.error_trip_not_found
            FailureReason.EMPTY_DESTINATION -> R.string.error_empty_destination
            FailureReason.INVALID_CURRENCY -> R.string.error_invalid_currency
            FailureReason.INVALID_BUDGET -> R.string.error_invalid_budget
            FailureReason.SOURCE_NOT_FOUND -> R.string.error_source_not_found
            FailureReason.WRONG_TRIP -> R.string.error_wrong_trip
            FailureReason.USED_SOURCE_CURRENCY -> R.string.error_used_source_currency
            FailureReason.SOURCE_BELOW_SPENT -> R.string.error_source_below_spent
            FailureReason.EMPTY_EXPENSE_TITLE -> R.string.error_empty_expense_title
            FailureReason.INVALID_EXPENSE_AMOUNT -> R.string.error_invalid_expense_amount
            FailureReason.INVALID_SOURCES -> R.string.error_invalid_sources
            FailureReason.INVALID_ALLOCATIONS -> R.string.error_invalid_allocations
            FailureReason.WRONG_EXPENSE_CURRENCY -> R.string.error_wrong_expense_currency
            FailureReason.REFRESH_RUBLE_RATE -> R.string.error_refresh_ruble_rate
            FailureReason.EMPTY_SOURCE_TITLE -> R.string.error_empty_source_title
            FailureReason.EMPTY_CHAIN -> R.string.error_empty_chain
            FailureReason.WRONG_STEP_CURRENCY -> R.string.error_wrong_step_currency
            FailureReason.INVALID_EXCHANGE_AMOUNT -> R.string.error_invalid_exchange_amount
            FailureReason.INVALID_COMMISSION -> R.string.error_invalid_commission
            FailureReason.INVALID_RECEIVED_AMOUNT -> R.string.error_invalid_received_amount
            FailureReason.INCOMPLETE_STEP -> R.string.error_incomplete_step
            FailureReason.SAME_CURRENCY -> R.string.error_same_currency
            FailureReason.INVALID_FINAL_AMOUNTS -> R.string.error_invalid_final_amounts
            FailureReason.INSUFFICIENT_FUNDS -> R.string.error_insufficient_funds
            FailureReason.RATE_UNAVAILABLE -> R.string.error_rate_unavailable
            FailureReason.NEGATIVE_SPENDING -> R.string.error_negative_spending
            FailureReason.DNS -> R.string.error_dns
            FailureReason.TIMEOUT -> R.string.error_timeout
            FailureReason.TLS -> R.string.error_tls
            FailureReason.UNSUPPORTED_RATE -> R.string.error_unsupported_rate
            FailureReason.RATE_LIMIT -> R.string.error_rate_limit
            FailureReason.SERVER -> R.string.error_server
            FailureReason.HTTP -> R.string.error_http
            FailureReason.NETWORK -> R.string.error_network
            FailureReason.INVALID_RATE -> R.string.error_invalid_rate
            FailureReason.UNEXPECTED -> R.string.error_unexpected
        }
    return getString(id, *error.arguments.toTypedArray())
}

internal fun androidx.fragment.app.Fragment.errorText(error: UiError) =
    requireContext().errorText(error)
