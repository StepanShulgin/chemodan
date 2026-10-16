package ru.stepan.chemodan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC") fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id") suspend fun getTrip(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id") fun observeTrip(id: Long): Flow<TripEntity?>

    @Insert suspend fun insertTrip(trip: TripEntity): Long

    @Update suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM exchange_sources WHERE tripId = :tripId ORDER BY id DESC")
    fun observeSources(tripId: Long): Flow<List<ExchangeSourceEntity>>

    @Query("SELECT * FROM exchange_sources WHERE id = :id")
    suspend fun getSource(id: Long): ExchangeSourceEntity?

    @Query("SELECT * FROM exchange_steps WHERE sourceId = :sourceId ORDER BY stepIndex")
    suspend fun getSteps(sourceId: Long): List<ExchangeStepEntity>

    @Query("DELETE FROM exchange_steps WHERE sourceId = :sourceId")
    suspend fun deleteSteps(sourceId: Long)

    @Query("SELECT * FROM expenses WHERE sourceId = :sourceId ORDER BY createdAt, id")
    suspend fun getSourceExpenses(sourceId: Long): List<ExpenseEntity>

    @Update suspend fun updateExpenses(expenses: List<ExpenseEntity>)

    @Insert suspend fun insertSource(source: ExchangeSourceEntity): Long

    @Insert suspend fun insertExchangeSteps(steps: List<ExchangeStepEntity>)

    @Update suspend fun updateSource(source: ExchangeSourceEntity)

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun observeExpenses(tripId: Long): Flow<List<ExpenseEntity>>

    @Insert suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: ReferenceRateEntity)

    @Query("SELECT * FROM reference_rates WHERE base = :base AND quote = :quote")
    suspend fun getRate(base: String, quote: String): ReferenceRateEntity?

    @Transaction
    suspend fun addExpenseFromSource(expense: ExpenseEntity, source: ExchangeSourceEntity) {
        updateSource(source)
        insertExpense(expense)
    }

    @Transaction
    suspend fun addSourceWithSteps(
        source: ExchangeSourceEntity,
        steps: List<ExchangeStepEntity>,
    ): Long {
        val id = insertSource(source)
        insertExchangeSteps(steps.map { it.copy(sourceId = id) })
        return id
    }
}
