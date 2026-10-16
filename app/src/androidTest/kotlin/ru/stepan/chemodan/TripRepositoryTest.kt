package ru.stepan.chemodan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.data.*
import ru.stepan.chemodan.data.ExpenseAllocation
import ru.stepan.chemodan.domain.ExchangeCalculator

@RunWith(AndroidJUnit4::class)
class TripRepositoryTest {
    @Test
    fun threeDecimalCurrencySurvivesStorageAndCompleteSpending(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Кувейт", Country("KW", "Кувейт", "KWD"), 40000.0, 1, 2)
            val sourceId =
                repository.addSource(
                    tripId,
                    ExchangeSourceDraft(
                        "Наличные",
                        listOf(ExchangeStep("RUB", 40000.0, 500.0, "KWD", 125.555)),
                    ),
                )
            assertEquals(125.555, repository.sourceDetails(sourceId).source!!.originalAmount, 0.0)
            repository.addExpense(tripId, sourceId, "Такси", .555)
            repository.addExpense(tripId, sourceId, "Отель", 125.0)
            val remaining = requireNotNull(database.tripDao().getSource(sourceId))
            assertEquals(0.0, remaining.remainingAmount, 0.0)
            assertEquals(0.0, remaining.remainingRubles, 0.0)
            assertEquals(40000.0, repository.observeSummary(tripId).first()!!.spent, 0.0)
        } finally {
            database.close()
        }
    }

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun legacyBinaryRemaindersCanBeSpentInFull(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId = repository.createTrip("Отпуск", Country("US", "США", "USD"), 1000.0, 1, 2)
            val sourceId =
                repository.addSource(
                    tripId,
                    ExchangeSourceDraft(
                        "Обмен",
                        listOf(ExchangeStep("RUB", 300.0, 0.0, "USD", 3.0)),
                    ),
                )
            val source = requireNotNull(database.tripDao().getSource(sourceId))
            database
                .tripDao()
                .updateSource(
                    source.copy(
                        remainingAmount = 2.6999999999999997,
                        remainingRubles = 269.99999999999994,
                    )
                )
            repository.addExpense(tripId, sourceId, "Остаток", 2.7)
            val remaining = requireNotNull(database.tripDao().getSource(sourceId))
            assertEquals(0.0, remaining.remainingAmount, 0.0)
            assertEquals(0.0, remaining.remainingRubles, 0.0)
            assertEquals(
                270.0,
                database.tripDao().getSourceExpenses(sourceId).single().rubleCost,
                0.0,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun independentSourcesAndMixedCurrencyExpensesUseRubleBudget() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Отпуск", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            val first =
                repository.addSource(
                    tripId,
                    ExchangeSourceDraft(
                        "Доллары",
                        listOf(ExchangeStep("RUB", 9000.0, 0.0, "USD", 100.0)),
                    ),
                )
            val second =
                repository.addSource(
                    tripId,
                    ExchangeSourceDraft(
                        "Доллары",
                        listOf(ExchangeStep("RUB", 10000.0, 0.0, "USD", 100.0)),
                    ),
                )
            repository.addExpense(tripId, first, "Ужин", 25.0)
            repository.addExpense(tripId, second, "Музей", 25.0)
            repository.addAutomaticExpense(
                tripId,
                "Такси",
                400.0,
                ReferenceRateEntity("RUB", "THB", .4, "2026-10-08"),
            )
            repository.updateTrip(
                tripId,
                "Отпуск",
                Country("TR", "Турция", "TRY"),
                100000.0,
                1L,
                2L,
            )
            val summary = repository.observeSummary(tripId).first()!!
            assertEquals(5750.0, summary.spent, 0.001)
            assertEquals(4750.0, summary.spentRubles, 0.001)
            assertEquals(1000.0, summary.estimatedRubles, 0.001)
            assertEquals(setOf("USD", "THB"), summary.expenses.map { it.currency }.toSet())
            assertEquals(75.0, database.tripDao().getSource(first)!!.remainingAmount, 0.001)
            assertEquals(6750.0, database.tripDao().getSource(first)!!.remainingRubles, 0.001)
        } finally {
            database.close()
        }
    }

    @Test
    fun updatingChainRevaluesLinkedExpensesAndFutureSpendingOnly() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Отпуск", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            fun chain(rubles: Double, received: Double) =
                ExchangeSourceDraft(
                    "Наличные",
                    listOf(
                        ExchangeStep("RUB", rubles, 200.0, "USD", 100.0),
                        ExchangeStep("USD", 100.0, 2.0, "THB", received),
                    ),
                )
            val sourceId = repository.addSource(tripId, chain(9200.0, 3400.0))
            val other = repository.addSource(tripId, chain(9200.0, 3400.0))
            repository.addExpense(tripId, sourceId, "Ужин", 850.0)
            repository.addExpense(tripId, sourceId, "Музей", 425.0)
            repository.addExpense(tripId, other, "Другой обмен", 850.0)
            repository.addAutomaticExpense(
                tripId,
                "По курсу",
                400.0,
                ReferenceRateEntity("RUB", "THB", .4, "2026-10-08"),
            )
            val before = database.tripDao().getSourceExpenses(sourceId)
            repository.updateSource(
                tripId,
                sourceId,
                chain(10200.0, 5100.0).copy(title = "Обновлённый обмен"),
            )
            val summary = repository.observeSummary(tripId).first()!!
            assertEquals(5850.0, summary.spent, .001)
            val updated = database.tripDao().getSourceExpenses(sourceId)
            assertEquals(before.map { it.id }, updated.map { it.id })
            assertEquals(before.map { it.createdAt }, updated.map { it.createdAt })
            assertEquals(listOf(1700.0, 850.0), updated.map { it.rubleCost })
            assertEquals(2300.0, summary.expenses.single { it.sourceId == other }.rubleCost, .001)
            assertEquals(1000.0, summary.expenses.single { it.sourceId == null }.rubleCost, .001)
            val (source, steps) = repository.sourceDetails(sourceId)
            assertEquals("Обновлённый обмен", source!!.title)
            assertEquals(3825.0, source.remainingAmount, .001)
            assertEquals(7650.0, source.remainingRubles, .001)
            assertEquals(5100.0, source.originalAmount, .001)
            assertEquals(10200.0, source.originalRubles, .001)
            assertEquals(2, steps.size)
            assertEquals(10200.0, steps.first().amountGiven, .001)
            repository.addExpense(tripId, sourceId, "Остаток", 3825.0)
            assertEquals(0.0, database.tripDao().getSource(sourceId)!!.remainingRubles, .001)
            assertEquals(
                10200.0,
                database.tripDao().getSourceExpenses(sourceId).sumOf { it.rubleCost },
                .001,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun invalidChainUpdateLeavesAllStoredDataUnchanged() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Отпуск", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            fun chain(output: Double, currency: String = "THB", fee: Double = 0.0) =
                ExchangeSourceDraft(
                    "Обмен",
                    listOf(ExchangeStep("RUB", 100.0, fee, currency, output)),
                )
            val id = repository.addSource(tripId, chain(10.0))
            repository.addExpense(tripId, id, "Расход", 5.0)
            val before = repository.sourceDetails(id)
            val expenses = database.tripDao().getSourceExpenses(id)
            listOf(chain(4.0), chain(10.0, "USD"), chain(10.0, fee = 100.0)).forEach { invalid ->
                assertTrue(
                    runCatching { repository.updateSource(tripId, id, invalid) }.exceptionOrNull()
                        is IllegalArgumentException
                )
                assertEquals(before, repository.sourceDetails(id))
                assertEquals(expenses, database.tripDao().getSourceExpenses(id))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun chainUpdatePreservesRoundingAndAllowsCurrencyChangeBeforeSpending() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Отпуск", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            fun chain(rubles: Double, currency: String) =
                ExchangeSourceDraft(
                    "Обмен",
                    listOf(ExchangeStep("RUB", rubles, 0.0, currency, 3.0)),
                )
            val id = repository.addSource(tripId, chain(90.0, "USD"))
            repository.updateSource(tripId, id, chain(90.0, "THB"))
            repeat(3) { repository.addExpense(tripId, id, "Расход $it", 1.0) }
            repository.updateSource(tripId, id, chain(100.0, "THB"))
            assertEquals(
                listOf(33.33, 33.34, 33.33),
                database.tripDao().getSourceExpenses(id).map { it.rubleCost },
            )
            assertEquals(0.0, database.tripDao().getSource(id)!!.remainingAmount, .001)
            assertEquals(0.0, database.tripDao().getSource(id)!!.remainingRubles, .001)
        } finally {
            database.close()
        }
    }

    @Test
    fun referenceRateFallsBackToSnapshotAndRejectsUnavailableUncachedQuote() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            database.tripDao().insertRate(ReferenceRateEntity("RUB", "THB", .4, "2026-10-08"))
            val (snapshot, cached) = repository.referenceRate("RUB", "THB")
            assertTrue(cached)
            assertEquals(.4, snapshot.rate, .001)
            assertTrue(
                runCatching { repository.referenceRate("RUB", "USD") }.exceptionOrNull()
                    is IllegalStateException
            )
            assertEquals(1.0, repository.referenceRate("RUB", "RUB").rate.rate, .001)
        } finally {
            database.close()
        }
    }

    @Test
    fun splitExpenseUsesBothActualRatesAndRevaluesOnlyEditedAllocation() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Отпуск", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            fun chain(rubles: Double) =
                ExchangeSourceDraft(
                    "Доллары",
                    listOf(ExchangeStep("RUB", rubles, 0.0, "USD", 100.0)),
                )
            val first = repository.addSource(tripId, chain(9000.0))
            val second = repository.addSource(tripId, chain(10000.0))
            repository.addSplitExpense(
                tripId,
                "Ужин",
                80.0,
                "USD",
                listOf(ExpenseAllocation(first, 30.0), ExpenseAllocation(second, 50.0)),
            )
            val expenses = database.tripDao().observeExpenses(tripId).first()
            assertEquals(2, expenses.size)
            assertNotNull(expenses.first().groupId)
            assertEquals(1, expenses.map { it.groupId }.distinct().size)
            assertEquals(7700.0, expenses.sumOf { it.rubleCost }, .001)
            assertEquals(70.0, database.tripDao().getSource(first)!!.remainingAmount, .001)
            assertEquals(50.0, database.tripDao().getSource(second)!!.remainingAmount, .001)
            repository.updateSource(tripId, first, chain(11000.0))
            val updated = database.tripDao().observeExpenses(tripId).first()
            assertEquals(expenses.map { it.groupId }, updated.map { it.groupId })
            assertEquals(8300.0, updated.sumOf { it.rubleCost }, .001)
            assertEquals(5000.0, updated.single { it.sourceId == second }.rubleCost, .001)
        } finally {
            database.close()
        }
    }

    @Test
    fun splitExpenseValidationAndSecondSourceFailureRollBackBothDebits() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ChemodanDatabase::class.java).build()
        try {
            val repository = TripRepository(database, unavailableApi, ExchangeCalculator())
            val tripId =
                repository.createTrip("Отпуск", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            fun chain(currency: String) =
                ExchangeSourceDraft(
                    "Наличные",
                    listOf(ExchangeStep("RUB", 100.0, 0.0, currency, 10.0)),
                )
            val first = repository.addSource(tripId, chain("USD"))
            val second = repository.addSource(tripId, chain("USD"))
            val wrongCurrency = repository.addSource(tripId, chain("THB"))
            val otherTrip =
                repository.createTrip("Другой", Country("TH", "Таиланд", "THB"), 100000.0, 1L, 2L)
            val wrongTrip = repository.addSource(otherTrip, chain("USD"))
            val original = repository.sourceDetails(first)
            val attempts =
                listOf(
                    25.0 to listOf(ExpenseAllocation(first, 5.0), ExpenseAllocation(second, 20.0)),
                    10.0 to listOf(ExpenseAllocation(first, 5.0), ExpenseAllocation(first, 5.0)),
                    10.0 to listOf(ExpenseAllocation(first, 5.0), ExpenseAllocation(second, 4.0)),
                    10.0 to
                        listOf(
                            ExpenseAllocation(first, 5.0),
                            ExpenseAllocation(wrongCurrency, 5.0),
                        ),
                    10.0 to
                        listOf(ExpenseAllocation(first, 5.0), ExpenseAllocation(wrongTrip, 5.0)),
                    10.0 to listOf(ExpenseAllocation(first, 0.0), ExpenseAllocation(second, 10.0)),
                )
            attempts.forEach { (total, allocations) ->
                assertTrue(
                    runCatching {
                            repository.addSplitExpense(tripId, "Ужин", total, "USD", allocations)
                        }
                        .isFailure
                )
                assertEquals(original, repository.sourceDetails(first))
                assertEquals(10.0, database.tripDao().getSource(second)!!.remainingAmount, .001)
                assertTrue(database.tripDao().observeExpenses(tripId).first().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationPreservesExpensesAndRequiresExplicitRubleBudget() = runBlocking {
        val name = "chemodan-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL(
                "CREATE TABLE trips(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, destination TEXT NOT NULL, currency TEXT NOT NULL, budget REAL NOT NULL, createdAt INTEGER NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE exchange_sources(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, title TEXT NOT NULL, currency TEXT NOT NULL, originalAmount REAL NOT NULL, remainingAmount REAL NOT NULL, originalRubles REAL NOT NULL, remainingRubles REAL NOT NULL, chainDescription TEXT NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE exchange_steps(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, stepIndex INTEGER NOT NULL, fromCurrency TEXT NOT NULL, amountGiven REAL NOT NULL, fee REAL NOT NULL, toCurrency TEXT NOT NULL, amountReceived REAL NOT NULL, effectiveRate REAL NOT NULL)"
            )
            old.execSQL("CREATE INDEX index_exchange_steps_sourceId ON exchange_steps(sourceId)")
            old.execSQL(
                "CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, sourceId INTEGER, title TEXT NOT NULL, amount REAL NOT NULL, rubleCost REAL NOT NULL, calculationMode TEXT NOT NULL, referenceRate REAL, referenceRateDate TEXT, createdAt INTEGER NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE reference_rates(base TEXT NOT NULL, quote TEXT NOT NULL, rate REAL NOT NULL, date TEXT NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(base, quote))"
            )
            old.execSQL("INSERT INTO trips VALUES(1, 'Таиланд', 'THB', 50000, 1)")
            old.execSQL(
                "INSERT INTO exchange_sources VALUES(1, 1, 'Наличные', 'THB', 3400, 2550, 9200, 6900, 'RUB → USD → THB')"
            )
            old.execSQL(
                "INSERT INTO expenses VALUES(1, 1, 1, 'Ужин', 850, 2300, 'MANUAL_CHAIN', NULL, NULL, 1)"
            )
            old.version = 3
        }
        val migrated =
            Room.databaseBuilder(context, ChemodanDatabase::class.java, name)
                .addMigrations(ChemodanDatabase.MIGRATION_3_4, ChemodanDatabase.MIGRATION_4_5)
                .build()
        try {
            val trip = migrated.tripDao().getTrip(1)!!
            assertEquals(50000.0, trip.legacyBudget!!, 0.001)
            assertEquals(0.0, trip.budget, 0.001)
            val expense = migrated.tripDao().observeExpenses(1).first().single()
            assertEquals("THB", expense.currency)
            assertEquals(2300.0, expense.rubleCost, .001)
            assertNull(expense.groupId)
            assertEquals(6900.0, migrated.tripDao().getSource(1)!!.remainingRubles, .001)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    private val unavailableApi =
        object : FrankfurterApi {
            override suspend fun getRate(base: String, quote: String): FrankfurterRateDto =
                throw java.io.IOException("Offline")
        }
}
