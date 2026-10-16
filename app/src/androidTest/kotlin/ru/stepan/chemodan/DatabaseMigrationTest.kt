package ru.stepan.chemodan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.stepan.chemodan.data.ChemodanDatabase
import ru.stepan.chemodan.data.ExpenseMode

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test
    fun everyPublishedVersionUpgradesWithoutLosingFinancialHistory(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val migrations =
            listOf(
                ChemodanDatabase.MIGRATION_1_2,
                ChemodanDatabase.MIGRATION_2_3,
                ChemodanDatabase.MIGRATION_3_4,
                ChemodanDatabase.MIGRATION_4_5,
            )
        for (version in 1..4) {
            val name = "migration-from-$version.db"
            context.deleteDatabase(name)
            val helper =
                androidx.sqlite.db.framework
                    .FrameworkSQLiteOpenHelperFactory()
                    .create(
                        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                            .name(name)
                            .callback(
                                object :
                                    androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
                                    override fun onCreate(
                                        old: androidx.sqlite.db.SupportSQLiteDatabase
                                    ) {
                                        old.execSQL(
                                            "CREATE TABLE trips(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, destination TEXT NOT NULL, currency TEXT NOT NULL, budget REAL NOT NULL, createdAt INTEGER NOT NULL)"
                                        )
                                        old.execSQL(
                                            "CREATE TABLE exchange_sources(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, title TEXT NOT NULL, currency TEXT NOT NULL, originalAmount REAL NOT NULL, remainingAmount REAL NOT NULL, originalRubles REAL NOT NULL, remainingRubles REAL NOT NULL, chainDescription TEXT NOT NULL)"
                                        )
                                        old.execSQL(
                                            "CREATE TABLE expenses(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, sourceId INTEGER NOT NULL, title TEXT NOT NULL, amount REAL NOT NULL, rubleCost REAL NOT NULL, createdAt INTEGER NOT NULL)"
                                        )
                                        old.execSQL(
                                            "CREATE TABLE reference_rates(base TEXT NOT NULL, quote TEXT NOT NULL, rate REAL NOT NULL, date TEXT NOT NULL, savedAt INTEGER NOT NULL, PRIMARY KEY(base, quote))"
                                        )
                                        old.execSQL(
                                            "INSERT INTO trips VALUES(1, 'Кувейт', 'KWD', 50000, 123)"
                                        )
                                        old.execSQL(
                                            "INSERT INTO exchange_sources VALUES(1, 1, 'Наличные', 'KWD', 125.555, 100.555, 40000, 32035.36, 'RUB → KWD')"
                                        )
                                        old.execSQL(
                                            "INSERT INTO expenses VALUES(1, 1, 1, 'Ужин', 25, 7964.64, 456)"
                                        )
                                        old.execSQL(
                                            "INSERT INTO reference_rates VALUES('RUB', 'KWD', 0.003, '2026-10-15', 789)"
                                        )
                                        migrations
                                            .filter { it.endVersion <= version }
                                            .forEach { it.migrate(old) }
                                    }

                                    override fun onUpgrade(
                                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                                        oldVersion: Int,
                                        newVersion: Int,
                                    ) = error("Fixture is only created once")
                                }
                            )
                            .build()
                    )
            helper.writableDatabase
            helper.close()
            val database =
                Room.databaseBuilder(context, ChemodanDatabase::class.java, name)
                    .addMigrations(*migrations.toTypedArray())
                    .build()
            try {
                val dao = database.tripDao()
                val trip = requireNotNull(dao.getTrip(1))
                assertEquals(50000.0, requireNotNull(trip.legacyBudget), 0.0)
                assertEquals(0.0, trip.budget, 0.0)
                assertEquals(123L, trip.createdAt)
                val expense = dao.observeExpenses(1).first().single()
                assertEquals(ExpenseMode.MANUAL_CHAIN, expense.calculationMode)
                assertEquals("KWD", expense.currency)
                assertEquals(7964.64, expense.rubleCost, 0.0)
                assertEquals(456L, expense.createdAt)
                assertNull(expense.groupId)
                assertEquals(100.555, requireNotNull(dao.getSource(1)).remainingAmount, 0.0)
                assertEquals(0.003, requireNotNull(dao.getRate("RUB", "KWD")).rate, 0.0)
            } finally {
                database.close()
                context.deleteDatabase(name)
            }
        }
    }
}
