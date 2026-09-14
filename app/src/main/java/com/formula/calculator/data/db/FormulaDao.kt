package com.formula.calculator.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FormulaDao {

    @Query("SELECT * FROM formulas ORDER BY updatedAt DESC")
    fun getAllFormulas(): Flow<List<FormulaEntity>>

    @Query("SELECT * FROM formulas WHERE id = :id")
    suspend fun getFormulaById(id: Long): FormulaEntity?

    @Query("SELECT * FROM formulas WHERE category = :category ORDER BY updatedAt DESC")
    fun getFormulasByCategory(category: String): Flow<List<FormulaEntity>>

    @Query("SELECT DISTINCT category FROM formulas ORDER BY category")
    fun getAllCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFormula(formula: FormulaEntity): Long

    @Update
    suspend fun updateFormula(formula: FormulaEntity)

    @Delete
    suspend fun deleteFormula(formula: FormulaEntity)

    @Query("DELETE FROM formulas WHERE id = :id")
    suspend fun deleteFormulaById(id: Long)

    // Ingredients
    @Query("SELECT * FROM ingredients WHERE formulaId = :formulaId ORDER BY sortOrder ASC")
    fun getIngredientsByFormula(formulaId: Long): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredients WHERE formulaId = :formulaId ORDER BY sortOrder ASC")
    suspend fun getIngredientsByFormulaSync(formulaId: Long): List<IngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: IngredientEntity): Long

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Update
    suspend fun updateIngredient(ingredient: IngredientEntity)

    @Delete
    suspend fun deleteIngredient(ingredient: IngredientEntity)

    @Query("DELETE FROM ingredients WHERE formulaId = :formulaId")
    suspend fun deleteIngredientsByFormula(formulaId: Long)

    @Query("SELECT COUNT(*) FROM formulas")
    suspend fun getFormulaCount(): Int

    // Batch operations
    @Query("SELECT * FROM formulas WHERE batchOrder >= 0 ORDER BY batchGroup, batchOrder ASC")
    fun getBatchFormulas(): Flow<List<FormulaEntity>>

    @Query("SELECT DISTINCT batchGroup FROM formulas WHERE batchGroup != '' ORDER BY batchGroup")
    fun getBatchGroups(): Flow<List<String>>

    @Query("SELECT * FROM formulas WHERE batchGroup = :group ORDER BY batchOrder ASC")
    fun getFormulasByBatchGroup(group: String): Flow<List<FormulaEntity>>

    @Query("UPDATE formulas SET isCompleted = :completed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: Long, completed: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE formulas SET targetAmount = :amount, updatedAt = :updatedAt WHERE batchGroup = :group")
    suspend fun updateGroupTargetAmount(group: String, amount: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE formulas SET isCompleted = 1, updatedAt = :updatedAt WHERE batchGroup = :group")
    suspend fun completeGroup(group: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE formulas SET isCompleted = 0, updatedAt = :updatedAt WHERE batchGroup = :group")
    suspend fun uncompleteGroup(group: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE formulas SET isCompleted = 0, batchOrder = -1, batchGroup = '', targetAmount = 0")
    suspend fun clearBatchStatus()
}
