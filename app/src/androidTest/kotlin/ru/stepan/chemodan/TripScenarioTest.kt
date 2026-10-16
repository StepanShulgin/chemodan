package ru.stepan.chemodan

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.startsWith
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.ui.AddExpenseFragment
import ru.stepan.chemodan.ui.CreateTripFragment
import ru.stepan.chemodan.ui.TripDetailsFragment
import ru.stepan.chemodan.ui.TripsFragment

@RunWith(AndroidJUnit4::class)
class TripScenarioTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun compactNavigationSwitchesBetweenTripsAndConverter() {
        onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()))
        activityRule.scenario.onActivity { activity ->
            val navigation = activity.findViewById<android.view.View>(R.id.bottom_navigation)
            org.junit.Assert.assertEquals(
                activity.resources.getDimensionPixelSize(R.dimen.navigation_width),
                navigation.width,
            )
            org.junit.Assert.assertEquals(
                activity.resources.getDimensionPixelSize(R.dimen.navigation_height),
                navigation.height,
            )
            org.junit.Assert.assertTrue(
                navigation.width < activity.findViewById<android.view.View>(R.id.window_root).width
            )
        }
        onView(withId(R.id.nav_converter)).perform(click())
        onView(withText("Справочный курс валют")).check(matches(isDisplayed()))
        screenshot("compact-navigation")
        onView(withId(R.id.nav_trips)).perform(click())
        onView(withText("Ваши маршруты и бюджет")).check(matches(isDisplayed()))
    }

    @Test
    fun recordsMultiStepExchangeAndExpenseAtActualRubleCost() {
        onView(withId(TripsFragment.ID_CREATE_TRIP)).perform(click())
        input("Название поездки", "Зима у моря")
        chooseCountry()
        chooseDates()
        input("Бюджет поездки, ₽", "150000")
        screenshot("create-trip")
        onView(withId(CreateTripFragment.ID_SAVE_TRIP)).perform(click())
        onView(withText("Обмены")).perform(scrollTo(), click())
        onView(withId(TripDetailsFragment.ID_ADD_SOURCE)).perform(click())
        input("Название обмена", "Наличные через доллары")
        input("Шаг 0: Отдано", "9200")
        onView(withId(ru.stepan.chemodan.ui.stableId("fee_toggle_0"))).perform(scrollTo(), click())
        input("Шаг 0: Комиссия", "200")
        currency("Получаю валюту · шаг 1", "USD")
        input("Шаг 0: Получено", "100")
        onView(withText("＋ Добавить шаг")).perform(scrollTo(), click())
        onView(withId(ru.stepan.chemodan.ui.stableId("fee_toggle_1"))).perform(scrollTo(), click())
        input("Шаг 1: Комиссия", "2")
        input("Шаг 1: Получено", "3400")
        onView(withId(ru.stepan.chemodan.ui.stableId("fee_toggle_0"))).perform(scrollTo(), click())
        onView(withId(ru.stepan.chemodan.ui.stableId("fee_toggle_1"))).perform(scrollTo(), click())
        screenshot("exchange-editor")
        onView(withText("Сохранить обмен")).perform(click())
        onView(withText("Расходы")).perform(scrollTo(), click())
        onView(withId(TripDetailsFragment.ID_ADD_EXPENSE)).perform(click())
        onView(withId(AddExpenseFragment.ID_MODE_MANUAL)).perform(scrollTo(), click())
        input("На что потратили?", "Ужин")
        input("Сумма", "850")
        onView(withId(AddExpenseFragment.ID_SOURCE_SPINNER)).perform(scrollTo(), click())
        onView(withContentDescription("Поиск"))
            .perform(replaceText("Наличные"), closeSoftKeyboard())
        screenshot("source-picker")
        onView(withContentDescription("Наличные через доллары")).perform(click())
        screenshot("expense")
        onView(withId(AddExpenseFragment.ID_SAVE_EXPENSE)).perform(click())
        onView(withText("147 700 ₽")).check(matches(isDisplayed()))
        onView(withText("2 300 ₽")).perform(scrollTo()).check(matches(isDisplayed()))
        screenshot("trip-expenses")
        onView(withText("Обмены")).perform(scrollTo(), click())
        onView(withText("2 550 THB")).perform(scrollTo()).check(matches(isDisplayed()))
        screenshot("trip-exchanges")
        onView(withText("Наличные через доллары")).perform(scrollTo(), click())
        onView(withText("Изменить обмен")).perform(scrollTo(), click())
        onView(withContentDescription("Шаг 0: Отдано"))
            .perform(scrollTo())
            .check(matches(withText("9200.0")))
        input("Шаг 0: Отдано", "10200")
        activityRule.scenario.recreate()
        onView(withContentDescription("Шаг 0: Отдано"))
            .perform(scrollTo())
            .check(matches(withText("10200")))
        onView(withText("Сохранить и пересчитать")).perform(click())
        onView(withText("2 550 THB")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Расходы")).perform(scrollTo(), click())
        onView(withText("147 450 ₽")).check(matches(isDisplayed()))
        onView(withText("2 550 ₽")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun choosesExistingExchangeInAnotherCurrencyAndSplitsOneExpense() {
        onView(withId(TripsFragment.ID_CREATE_TRIP)).perform(click())
        input("Название поездки", "Два обмена")
        chooseCountry()
        chooseDates()
        input("Бюджет поездки, ₽", "100000")
        onView(withId(CreateTripFragment.ID_SAVE_TRIP)).perform(click())
        onView(withText("Обмены")).perform(scrollTo(), click())
        listOf("Первый обмен" to "9000", "Второй обмен" to "10000").forEach { (name, rubles) ->
            onView(withId(TripDetailsFragment.ID_ADD_SOURCE)).perform(click())
            input("Название обмена", name)
            input("Шаг 0: Отдано", rubles)
            currency("Получаю валюту · шаг 1", "USD")
            input("Шаг 0: Получено", "100")
            onView(withText("Сохранить обмен")).perform(click())
        }
        onView(withText("Расходы")).perform(scrollTo(), click())
        onView(withId(TripDetailsFragment.ID_ADD_EXPENSE)).perform(click())
        input("На что потратили?", "Одна покупка")
        input("Сумма", "80")
        onView(withId(AddExpenseFragment.ID_MODE_MANUAL)).perform(scrollTo(), click())
        onView(withId(AddExpenseFragment.ID_SOURCE_SPINNER)).perform(scrollTo(), click())
        onView(withContentDescription("Первый обмен")).perform(click())
        onView(withContentDescription("Валюта расхода"))
            .perform(scrollTo())
            .check(matches(withText("USD")))
        onView(withText("Разделить между двумя обменами")).perform(scrollTo(), click())
        input("Сумма из первого обмена", "30")
        onView(withContentDescription("Вторая цепочка обмена")).perform(scrollTo(), click())
        onView(withContentDescription("Второй обмен")).perform(click())
        activityRule.scenario.recreate()
        onView(withContentDescription("Сумма из первого обмена"))
            .perform(scrollTo())
            .check(matches(withText("30")))
        onView(withText("Из второго обмена: 50 USD"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        screenshot("expense-split")
        onView(withId(AddExpenseFragment.ID_SAVE_EXPENSE)).perform(click())
        onView(withText("92 300 ₽")).check(matches(isDisplayed()))
        onView(withText("Одна покупка")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("80 USD")).check(matches(isDisplayed()))
        onView(withText("7 700 ₽")).check(matches(isDisplayed()))
        screenshot("trip-split")
        onView(withText("Обмены")).perform(scrollTo(), click())
        onView(withText("70 USD")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("50 USD")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun preservesTripDraftAcrossActivityRecreation() {
        onView(withId(TripsFragment.ID_CREATE_TRIP)).perform(click())
        input("Название поездки", "Стамбул на выходные")
        chooseCountry()
        chooseDates()
        input("Бюджет поездки, ₽", "120000")
        activityRule.scenario.recreate()
        onView(withContentDescription("Название поездки"))
            .check(matches(withText("Стамбул на выходные")))
        onView(withContentDescription("Страна")).check(matches(withText(containsString("Таиланд"))))
        onView(withContentDescription("Бюджет поездки, ₽")).check(matches(withText("120000")))
        onView(withContentDescription("Даты поездки"))
            .check(matches(withText(containsString("10 окт."))))
    }

    @Test
    fun preservesExpenseDraftWhenOpeningExchangeAndRecreatingActivity() {
        onView(withId(TripsFragment.ID_CREATE_TRIP)).perform(click())
        input("Название поездки", "Поездка для проверки черновика")
        chooseCountry()
        chooseDates()
        input("Бюджет поездки, ₽", "100000")
        onView(withId(CreateTripFragment.ID_SAVE_TRIP)).perform(click())
        onView(withId(TripDetailsFragment.ID_ADD_EXPENSE)).perform(click())
        input("На что потратили?", "Такси до отеля")
        input("Сумма", "100")
        onView(withId(AddExpenseFragment.ID_MODE_MANUAL)).perform(scrollTo(), click())
        onView(withText("＋ Создать цепочку обмена")).perform(scrollTo(), click())
        androidx.test.espresso.Espresso.pressBack()
        activityRule.scenario.recreate()
        onView(withContentDescription("На что потратили?"))
            .perform(scrollTo())
            .check(matches(withText("Такси до отеля")))
        onView(withContentDescription("Сумма")).check(matches(withText("100")))
        onView(withId(AddExpenseFragment.ID_MODE_MANUAL)).check(matches(isChecked()))
    }

    private fun chooseCountry() {
        onView(withContentDescription("Страна")).perform(scrollTo(), click())
        onView(withContentDescription("Поиск")).perform(replaceText("Таиланд"), closeSoftKeyboard())
        screenshot("country-picker")
        onView(withContentDescription(containsString("Таиланд"))).perform(click())
    }

    private fun chooseDates() {
        onView(withContentDescription("Даты поездки")).perform(scrollTo(), click())
        onView(withId(com.google.android.material.R.id.mtrl_picker_header_toggle)).perform(click())
        onView(
                allOf(
                    isDescendantOfA(
                        withId(com.google.android.material.R.id.mtrl_picker_text_input_range_start)
                    ),
                    withClassName(endsWith("EditText")),
                )
            )
            .perform(replaceText("10/10/2026"), closeSoftKeyboard())
        onView(
                allOf(
                    isDescendantOfA(
                        withId(com.google.android.material.R.id.mtrl_picker_text_input_range_end)
                    ),
                    withClassName(endsWith("EditText")),
                )
            )
            .perform(replaceText("10/24/2026"), closeSoftKeyboard())
        onView(withId(com.google.android.material.R.id.confirm_button)).perform(click())
    }

    private fun currency(label: String, code: String) {
        onView(withContentDescription(label)).perform(scrollTo(), click())
        onView(withContentDescription("Поиск")).perform(replaceText(code), closeSoftKeyboard())
        screenshot("currency-picker")
        onView(withContentDescription(startsWith("$code  ·"))).perform(click())
    }

    private fun input(label: String, value: String) {
        onView(withContentDescription(label))
            .perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/chemodan-$name.png")
            .use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                    it.readBytes()
                }
            }
    }
}
