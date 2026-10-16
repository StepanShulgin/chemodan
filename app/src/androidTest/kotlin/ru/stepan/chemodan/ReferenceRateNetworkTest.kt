package ru.stepan.chemodan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.data.ChemodanDatabase
import ru.stepan.chemodan.data.TripRepository
import ru.stepan.chemodan.di.AppModule
import ru.stepan.chemodan.domain.ExchangeCalculator

@RunWith(AndroidJUnit4::class)
class ReferenceRateNetworkTest {
    @Test
    fun receivesAndCachesRealRubleRatesFromDevice() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyLiveRates") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository =
                TripRepository(database, AppModule.frankfurterApi(), ExchangeCalculator())
            listOf("USD", "THB").forEach { currency ->
                val (rate, cached) = repository.referenceRate("RUB", currency)
                assertFalse(cached)
                assertEquals("RUB", rate.base)
                assertEquals(currency, rate.quote)
                assertTrue(rate.rate.isFinite() && rate.rate > 0)
                assertTrue(rate.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
                assertEquals(rate, database.tripDao().getRate("RUB", currency))
            }
        } finally {
            database.close()
        }
    }
}
