package ru.stepan.chemodan.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import ru.stepan.chemodan.domain.FailureReason

class RateFailureTest {
    @Test
    fun distinguishesDnsTimeoutAndTransportErrors() {
        assertEquals(FailureReason.DNS, rateFailure(UnknownHostException(), "RUB", "USD").reason)
        assertEquals(
            FailureReason.TIMEOUT,
            rateFailure(SocketTimeoutException(), "RUB", "USD").reason,
        )
        assertEquals(FailureReason.NETWORK, rateFailure(IOException(), "RUB", "USD").reason)
    }

    @Test
    fun unsupportedCurrencyIsNotReportedAsInternetFailure() {
        val error = HttpException(Response.error<Any>(422, ResponseBody.create(null, "{}")))
        val failure = rateFailure(error, "RUB", "XYZ")
        assertEquals(FailureReason.UNSUPPORTED_RATE, failure.reason)
        assertEquals(listOf("RUB", "XYZ"), failure.arguments)
    }

    @Test
    fun reportsServerAndRateLimitErrorsSeparately() {
        assertEquals(
            FailureReason.SERVER,
            rateFailure(
                    HttpException(Response.error<Any>(503, ResponseBody.create(null, "{}"))),
                    "RUB",
                    "USD",
                )
                .reason,
        )
        assertEquals(
            FailureReason.RATE_LIMIT,
            rateFailure(
                    HttpException(Response.error<Any>(429, ResponseBody.create(null, "{}"))),
                    "RUB",
                    "USD",
                )
                .reason,
        )
    }
}
