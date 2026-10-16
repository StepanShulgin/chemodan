package ru.stepan.chemodan.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import ru.stepan.chemodan.di.IoDispatcher

private val russian = Locale("ru", "RU")

data class Country(val code: String, val name: String, val currency: String) {
    val flag: String
        get() =
            code
                .uppercase(Locale.ROOT)
                .map { String(Character.toChars(0x1F1E6 + it.code - 'A'.code)) }
                .joinToString("")
}

data class CurrencyOption(val code: String, val name: String, val countries: String)

data class RemoteCountry(val cca2: String, val currencies: Map<String, RemoteCurrency>?)

data class RemoteCurrency(val name: String)

interface CountryApi {
    @GET("mledoze/countries/master/countries.json") suspend fun countries(): List<RemoteCountry>
}

@Singleton
class Catalogue
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val api: CountryApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val preferences by lazy {
        context.getSharedPreferences("country_catalogue", Context.MODE_PRIVATE)
    }
    private val _countries = MutableStateFlow(fallbackCountries)
    private val currencyOptions = MutableStateFlow(buildCurrencies(_countries.value))
    private val refreshMutex = Mutex()
    val countries = _countries.asStateFlow()
    private var localLoaded = false

    fun country(code: String) = _countries.value.firstOrNull { it.code == code }

    fun currency(code: String) = currencies().firstOrNull { it.code == code }

    fun currencies(): List<CurrencyOption> = currencyOptions.value

    private fun buildCurrencies(countries: List<Country>): List<CurrencyOption> =
        countries
            .groupBy { it.currency }
            .map { (code, countries) ->
                val name =
                    runCatching { Currency.getInstance(code).getDisplayName(russian) }
                        .getOrDefault(code)
                CurrencyOption(
                    code,
                    name.replaceFirstChar { it.titlecase(russian) },
                    countries.joinToString(", ") { it.name },
                )
            }
            .sortedWith(compareBy(Collator.getInstance(russian)) { it.name })

    suspend fun refresh() =
        withContext(ioDispatcher) {
            refreshMutex.withLock {
                if (!localLoaded) {
                    val local = loadLocal()
                    currencyOptions.value = buildCurrencies(local)
                    _countries.value = local
                    localLoaded = true
                }
                if (System.currentTimeMillis() - preferences.getLong("saved_at", 0) < 86_400_000L)
                    return@withLock
                try {
                    val result =
                        api.countries()
                            .mapNotNull { remote ->
                                val currency =
                                    remote.currencies?.keys?.firstOrNull() ?: return@mapNotNull null
                                if (
                                    !remote.cca2.matches(Regex("[A-Z]{2}")) ||
                                        !currency.matches(Regex("[A-Z]{3}"))
                                )
                                    return@mapNotNull null
                                Country(
                                    remote.cca2,
                                    Locale("", remote.cca2).getDisplayCountry(russian),
                                    currency,
                                )
                            }
                            .sortedWith(compareBy(Collator.getInstance(russian)) { it.name })
                    if (result.size >= 200) {
                        preferences
                            .edit()
                            .putString("countries", gson.toJson(result))
                            .putLong("saved_at", System.currentTimeMillis())
                            .apply()
                        currencyOptions.value = buildCurrencies(result)
                        _countries.value = result
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                }
            }
        }

    private fun loadLocal(): List<Country> {
        val cached =
            runCatching {
                    gson.fromJson<List<Country>>(
                        preferences.getString("countries", null),
                        object : TypeToken<List<Country>>() {}.type,
                    )
                }
                .getOrNull()
        if (!cached.isNullOrEmpty()) return cached
        return fallbackCountries
    }

    companion object {
        private val fallbackCountries: List<Country> by lazy {
            Locale.getISOCountries()
                .mapNotNull { code ->
                    val locale = Locale("", code)
                    val currency =
                        runCatching { Currency.getInstance(locale)?.currencyCode }.getOrNull()
                            ?: return@mapNotNull null
                    Country(code, locale.getDisplayCountry(russian), currency)
                }
                .sortedWith(compareBy(Collator.getInstance(russian)) { it.name })
        }
    }
}
