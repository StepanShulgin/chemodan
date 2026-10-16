package ru.stepan.chemodan.data

import retrofit2.http.GET
import retrofit2.http.Path

data class FrankfurterRateDto(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double,
)

interface FrankfurterApi {
    @GET("v2/rate/{base}/{quote}")
    suspend fun getRate(
        @Path("base") base: String,
        @Path("quote") quote: String,
    ): FrankfurterRateDto
}
