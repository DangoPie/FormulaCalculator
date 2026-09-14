package com.formula.calculator.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM calculation_history ORDER BY createdAt DESC")
    fun getAllHistory(): Flow<List<CalculationHistoryEntity>>

    @Query("SELECT * FROM calculation_history WHERE formulaId = :formulaId ORDER BY createdAt DESC")
    fun getHistoryByFormula(formulaId: Long): Flow<List<CalculationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: CalculationHistoryEntity): Long

    @Delete
    suspend fun deleteHistory(history: CalculationHistoryEntity)

    @Query("DELETE FROM calculation_history")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM calculation_history ORDER BY createdAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<CalculationHistoryEntity>>
}
