package ru.stepan.chemodan.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities =
        [
            TripEntity::class,
            ExchangeSourceEntity::class,
            ExchangeStepEntity::class,
            ExpenseEntity::class,
            ReferenceRateEntity::class,
        ],
    version = 5,
    exportSchema = true,
)
abstract class ChemodanDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE expenses ADD COLUMN groupId TEXT")
                }
            }
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "ALTER TABLE trips ADD COLUMN countryCode TEXT NOT NULL DEFAULT ''"
                    )
                    database.execSQL(
                        "ALTER TABLE trips ADD COLUMN startDate INTEGER NOT NULL DEFAULT 0"
                    )
                    database.execSQL(
                        "ALTER TABLE trips ADD COLUMN endDate INTEGER NOT NULL DEFAULT 0"
                    )
                    database.execSQL("ALTER TABLE trips ADD COLUMN legacyBudget REAL")
                    database.execSQL("UPDATE trips SET legacyBudget = budget, budget = 0")
                    database.execSQL(
                        "ALTER TABLE expenses ADD COLUMN currency TEXT NOT NULL DEFAULT ''"
                    )
                    database.execSQL(
                        "UPDATE expenses SET currency = COALESCE((SELECT currency FROM trips WHERE trips.id = expenses.tripId), 'RUB')"
                    )
                }
            }
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS exchange_steps (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "sourceId INTEGER NOT NULL, stepIndex INTEGER NOT NULL, " +
                            "fromCurrency TEXT NOT NULL, amountGiven REAL NOT NULL, fee REAL NOT NULL, " +
                            "toCurrency TEXT NOT NULL, amountReceived REAL NOT NULL, effectiveRate REAL NOT NULL)"
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_exchange_steps_sourceId ON exchange_steps(sourceId)"
                    )
                }
            }
        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE expenses_new (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "tripId INTEGER NOT NULL, sourceId INTEGER, title TEXT NOT NULL, " +
                            "amount REAL NOT NULL, rubleCost REAL NOT NULL, calculationMode TEXT NOT NULL, " +
                            "referenceRate REAL, referenceRateDate TEXT, createdAt INTEGER NOT NULL)"
                    )
                    database.execSQL(
                        "INSERT INTO expenses_new (id, tripId, sourceId, title, amount, rubleCost, calculationMode, referenceRate, referenceRateDate, createdAt) " +
                            "SELECT id, tripId, sourceId, title, amount, rubleCost, 'MANUAL_CHAIN', NULL, NULL, createdAt FROM expenses"
                    )
                    database.execSQL("DROP TABLE expenses")
                    database.execSQL("ALTER TABLE expenses_new RENAME TO expenses")
                }
            }
    }
}
