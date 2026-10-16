package ru.stepan.chemodan

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.data.*

@RunWith(AndroidJUnit4::class)
class CatalogueTest {
    @Test
    fun constructorDoesNotReadDiskAndFreshCacheAvoidsNetwork(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preferences =
            instrumentation.targetContext.getSharedPreferences(
                "catalogue-test",
                Context.MODE_PRIVATE,
            )
        preferences.edit().clear().commit()
        val context =
            object : ContextWrapper(instrumentation.targetContext) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): android.content.SharedPreferences {
                    assertNotEquals(
                        "Catalogue disk access belongs on IO",
                        Looper.getMainLooper(),
                        Looper.myLooper(),
                    )
                    return preferences
                }
            }
        var calls = 0
        lateinit var catalogue: Catalogue
        val api =
            object : CountryApi {
                override suspend fun countries(): List<RemoteCountry> {
                    calls++
                    return catalogue.countries.value.take(200).map {
                        RemoteCountry(it.code, mapOf(it.currency to RemoteCurrency(it.currency)))
                    }
                }
            }
        try {
            instrumentation.runOnMainSync {
                catalogue = Catalogue(context, Gson(), api, Dispatchers.IO)
            }
            assertTrue(catalogue.countries.value.size >= 200)
            catalogue.refresh()
            assertEquals(200, catalogue.countries.value.size)
            catalogue.refresh()
            assertEquals(1, calls)
            assertTrue(catalogue.currencies().isNotEmpty())
            assertEquals("🇺🇸", Country("US", "США", "USD").flag)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun unavailableCatalogueKeepsOfflineCountryAndCurrencyChoices(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalogue =
            Catalogue(
                context,
                Gson(),
                object : CountryApi {
                    override suspend fun countries(): List<RemoteCountry> =
                        throw IOException("offline")
                },
                Dispatchers.IO,
            )
        catalogue.refresh()
        assertNotNull(catalogue.country("TH"))
        assertNotNull(catalogue.currency("THB"))
        assertTrue(catalogue.countries.value.isNotEmpty())
    }
}
