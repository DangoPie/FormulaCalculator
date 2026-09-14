package com.formula.calculator.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CommunityFormulaDao {

    @Query("SELECT * FROM community_formulas ORDER BY importedAt DESC")
    fun getAllCommunityFormulas(): Flow<List<CommunityFormulaEntity>>

    @Query("SELECT * FROM community_formulas WHERE id = :id")
    suspend fun getById(id: Long): CommunityFormulaEntity?

    @Query("SELECT * FROM community_formulas WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<CommunityFormulaEntity?>

    @Query("""
        SELECT * FROM community_formulas 
        WHERE originalName LIKE '%' || :query || '%' 
           OR author LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
           OR ingredientsJson LIKE '%' || :query || '%'
        ORDER BY importedAt DESC
    """)
    fun search(query: String): Flow<List<CommunityFormulaEntity>>

    @Query("SELECT * FROM community_formulas WHERE isFavorite = 1 ORDER BY importedAt DESC")
    fun getFavorites(): Flow<List<CommunityFormulaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommunityFormulaEntity): Long

    @Update
    suspend fun update(entity: CommunityFormulaEntity)

    @Query("DELETE FROM community_formulas WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE community_formulas SET localName = :name WHERE id = :id")
    suspend fun updateLocalName(id: Long, name: String)

    @Query("UPDATE community_formulas SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE community_formulas SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("SELECT COUNT(*) FROM community_formulas")
    suspend fun getCount(): Int
}
