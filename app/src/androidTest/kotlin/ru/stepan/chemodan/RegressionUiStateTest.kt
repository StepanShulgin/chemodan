package ru.stepan.chemodan

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.data.*
import ru.stepan.chemodan.domain.BudgetCalculator
import ru.stepan.chemodan.domain.ExchangeCalculator
import ru.stepan.chemodan.ui.*

@RunWith(AndroidJUnit4::class)
class RegressionUiStateTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val unavailable =
        object : FrankfurterApi {
            override suspend fun getRate(base: String, quote: String): FrankfurterRateDto =
                throw IOException("offline")
        }

    @Test
    fun networkFailureRemainsVisibleWhenUserEditsExpenseAmount(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailable, ExchangeCalculator())
            val trip = repository.createTrip("Review", Country("US", "США", "USD"), 1000.0, 1, 2)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.viewModelStore.put(
                        "androidx.lifecycle.ViewModelProvider.DefaultKey:ru.stepan.chemodan.ui.TripDetailsViewModel",
                        TripDetailsViewModel(repository, BudgetCalculator()),
                    )
                    it.show(AddExpenseFragment.newInstance(trip), true)
                }
                await(scenario) { activity ->
                    textViews(activity.window.decorView).any { it.text.contains("Нет соединения") }
                }
                onView(withContentDescription("Сумма")).perform(replaceText("100"))
                scenario.onActivity { activity ->
                    assertTrue(
                        "An already failed request must not be shown as loading after editing amount",
                        textViews(activity.window.decorView).any {
                            it.text.contains("Нет соединения")
                        },
                    )
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun recreationDoesNotAllowSavingSameTripTwiceWhileFirstSaveIsPending(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        val release = CompletableDeferred<Unit>()
        val acquired = CompletableDeferred<Unit>()
        try {
            val repository = TripRepository(database, unavailable, ExchangeCalculator())
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentCreated(
                                fm: FragmentManager,
                                fragment: Fragment,
                                state: Bundle?,
                            ) {
                                if (fragment is CreateTripFragment) {
                                    fragment.viewModelStore.put(
                                        "androidx.lifecycle.ViewModelProvider.DefaultKey:ru.stepan.chemodan.ui.TripsViewModel",
                                        TripsViewModel(repository),
                                    )
                                    StatefulFormFragment::class
                                        .java
                                        .getDeclaredField("draft")
                                        .apply { isAccessible = true }
                                        .set(
                                            fragment,
                                            Bundle().apply {
                                                putString("title", "Review pending save")
                                                putString("country", "US")
                                                putLong("start", 1)
                                                putLong("end", 2)
                                                putString("budget", "1000")
                                            },
                                        )
                                }
                            }
                        },
                        false,
                    )
                    activity.show(CreateTripFragment(), true)
                }
                await(scenario) {
                    it.findViewById<View>(CreateTripFragment.ID_SAVE_TRIP)?.isEnabled == true
                }
                val lock =
                    launch(Dispatchers.IO) {
                        database.withTransaction {
                            acquired.complete(Unit)
                            release.await()
                        }
                    }
                acquired.await()
                onView(withId(CreateTripFragment.ID_SAVE_TRIP)).perform(click())
                scenario.recreate()
                await(scenario) { it.window.decorView.hasWindowFocus() }
                var enabled = false
                scenario.onActivity {
                    enabled = it.findViewById<View>(CreateTripFragment.ID_SAVE_TRIP).isEnabled
                }
                assertFalse("Save stays disabled after Activity recreation", enabled)
                release.complete(Unit)
                lock.join()
                withTimeout(4000) {
                    while (database.tripDao().observeTrips().first().isEmpty()) delay(20)
                }
                assertEquals(
                    "Rotation must not permit a duplicate pending save",
                    1,
                    database.tripDao().observeTrips().first().size,
                )
            }
        } finally {
            release.complete(Unit)
            database.close()
        }
    }

    private fun textViews(view: View): List<TextView> = buildList {
        if (view is TextView) add(view)
        if (view is android.view.ViewGroup)
            repeat(view.childCount) { addAll(textViews(view.getChildAt(it))) }
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(20)
        }
        throw AssertionError("Review UI condition timed out")
    }
}
