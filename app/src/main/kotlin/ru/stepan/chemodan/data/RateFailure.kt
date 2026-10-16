package ru.stepan.chemodan.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import retrofit2.HttpException
import ru.stepan.chemodan.domain.FailureReason
import ru.stepan.chemodan.domain.RateRequestFailure

internal fun rateFailure(error: Exception, base: String, quote: String): RateRequestFailure {
    val reason =
        when (error) {
            is UnknownHostException -> FailureReason.DNS
            is SocketTimeoutException -> FailureReason.TIMEOUT
            is SSLException -> FailureReason.TLS
            is HttpException ->
                when (error.code()) {
                    404,
                    422 -> FailureReason.UNSUPPORTED_RATE
                    429 -> FailureReason.RATE_LIMIT
                    in 500..599 -> FailureReason.SERVER
                    else -> FailureReason.HTTP
                }
            is IOException -> FailureReason.NETWORK
            else -> FailureReason.INVALID_RATE
        }
    val arguments =
        when (reason) {
            FailureReason.HTTP -> listOf((error as HttpException).code())
            FailureReason.UNSUPPORTED_RATE,
            FailureReason.INVALID_RATE -> listOf(base, quote)
            else -> emptyList()
        }
    return RateRequestFailure(reason, arguments, error)
}
