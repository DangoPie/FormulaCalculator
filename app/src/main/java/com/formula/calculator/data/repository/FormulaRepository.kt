package com.formula.calculator.data.repository

import com.formula.calculator.data.db.*
import com.formula.calculator.domain.CommunityFormula
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class FormulaRepository(
    private val formulaDao: FormulaDao,
    private val historyDao: HistoryDao,
    private val communityFormulaDao: CommunityFormulaDao
) {
    // ============ Formula ============

    fun getAllFormulas(): Flow<List<Formula>> {
        return formulaDao.getAllFormulas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getFormulaById(id: Long): Formula? {
        return formulaDao.getFormulaById(id)?.toDomain()
    }

    fun getFormulasByCategory(category: String): Flow<List<Formula>> {
        return formulaDao.getFormulasByCategory(category).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getAllCategories(): Flow<List<String>> = formulaDao.getAllCategories()

    suspend fun saveFormula(formula: Formula, ingredients: List<Ingredient>): Long {
        val entity = formula.toEntity()
        val formulaId = if (formula.id == 0L) {
            formulaDao.insertFormula(entity)
        } else {
            formulaDao.updateFormula(entity.copy(updatedAt = System.currentTimeMillis()))
            formula.id
        }

        // 删除旧组分，插入新组分
        formulaDao.deleteIngredientsByFormula(formulaId)
        val ingredientEntities = ingredients.mapIndexed { index, ingredient ->
            ingredient.toEntity(formulaId, index)
        }
        formulaDao.insertIngredients(ingredientEntities)

        return formulaId
    }

    suspend fun deleteFormula(id: Long) {
        formulaDao.deleteFormulaById(id)
    }

    suspend fun updateFormulaName(id: Long, name: String) {
        formulaDao.getFormulaById(id)?.let {
            formulaDao.updateFormula(it.copy(name = name, updatedAt = System.currentTimeMillis()))
        }
    }

    // ============ Ingredients ============

    fun getIngredientsByFormula(formulaId: Long): Flow<List<Ingredient>> {
        return formulaDao.getIngredientsByFormula(formulaId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getIngredientsByFormulaSync(formulaId: Long): List<Ingredient> {
        return formulaDao.getIngredientsByFormulaSync(formulaId).map { it.toDomain() }
    }

    // ============ History ============

    fun getAllHistory() = historyDao.getAllHistory()

    fun getRecentHistory() = historyDao.getRecentHistory()

    fun getHistoryByFormula(formulaId: Long) = historyDao.getHistoryByFormula(formulaId)

    suspend fun saveHistory(history: CalculationHistoryEntity): Long {
        return historyDao.insertHistory(history)
    }

    suspend fun deleteHistory(history: CalculationHistoryEntity) {
        historyDao.deleteHistory(history)
    }

    suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }

    // ============ Batch ============

    fun getBatchFormulas(): Flow<List<Formula>> {
        return formulaDao.getBatchFormulas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun updateCompleted(id: Long, completed: Boolean) {
        formulaDao.updateCompleted(id, completed)
    }

    suspend fun clearBatchStatus() {
        formulaDao.clearBatchStatus()
    }

    /**
     * 批量导入：每行一个配方，多个组分来自不同列
     * 自动分配 batchGroup
     */
    suspend fun batchImport(
        category: String,
        formulas: List<Pair<String, List<Ingredient>>>,  // (name, ingredients)
        batchGroup: String = ""
    ): Int {
        val group = batchGroup.ifBlank { "批次_${System.currentTimeMillis()}" }
        var count = 0
        formulas.forEachIndexed { index, (name, ingredients) ->
            val formula = Formula(
                name = name,
                category = category,
                batchOrder = index,
                batchGroup = group
            )
            saveFormula(formula, ingredients)
            count++
        }
        return count
    }

    // ============ Batch Groups ============

    fun getBatchGroups(): Flow<List<String>> = formulaDao.getBatchGroups()

    fun getFormulasByBatchGroup(group: String): Flow<List<Formula>> {
        return formulaDao.getFormulasByBatchGroup(group).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun updateGroupTargetAmount(group: String, amount: Double) {
        formulaDao.updateGroupTargetAmount(group, amount)
    }

    suspend fun completeGroup(group: String) {
        formulaDao.completeGroup(group)
    }

    suspend fun uncompleteGroup(group: String) {
        formulaDao.uncompleteGroup(group)
    }

    // ============ Mappers ============

    private fun FormulaEntity.toDomain(): Formula {
        return Formula(
            id = id,
            name = name,
            description = description,
            category = category,
            isCompleted = isCompleted,
            batchOrder = batchOrder,
            batchGroup = batchGroup,
            targetAmount = targetAmount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun Formula.toEntity(): FormulaEntity {
        return FormulaEntity(
            id = if (id == 0L) 0 else id,
            name = name,
            description = description,
            category = category,
            isCompleted = isCompleted,
            batchOrder = batchOrder,
            batchGroup = batchGroup,
            targetAmount = targetAmount,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun IngredientEntity.toDomain(): Ingredient {
        return Ingredient(
            id = id,
            formulaId = formulaId,
            name = name,
            ratio = ratio,
            unit = unit,
            sortOrder = sortOrder,
            note = note
        )
    }

    private fun Ingredient.toEntity(formulaId: Long, index: Int): IngredientEntity {
        return IngredientEntity(
            id = if (id == 0L) 0 else id,
            formulaId = formulaId,
            name = name,
            ratio = ratio,
            unit = unit,
            sortOrder = index,
            note = note
        )
    }

    // ============ Community Formulas ============

    fun getAllCommunityFormulas(): Flow<List<CommunityFormula>> {
        return communityFormulaDao.getAllCommunityFormulas().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun searchCommunityFormulas(query: String): Flow<List<CommunityFormula>> {
        return communityFormulaDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getFavoriteCommunityFormulas(): Flow<List<CommunityFormula>> {
        return communityFormulaDao.getFavorites().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getCommunityFormulaById(id: Long): CommunityFormula? {
        return communityFormulaDao.getById(id)?.toDomain()
    }

    /**
     * 导入社区配方：同时存入 community_formulas 表和 formulas 表
     */
    suspend fun importCommunityFormula(
        originalName: String,
        localName: String,
        description: String,
        category: String,
        author: String,
        sourceFile: String,
        ingredients: List<Ingredient>
    ): Long {
        // 序列化组分列表为 JSON
        val ingredientsJson = ingredientsToJson(ingredients)

        // 存入 community_formulas 表
        val entity = CommunityFormulaEntity(
            originalName = originalName,
            localName = localName,
            description = description,
            category = category,
            author = author,
            sourceFile = sourceFile,
            ingredientsJson = ingredientsJson
        )
        val communityId = communityFormulaDao.insert(entity)

        // 同时存入 formulas 表（可直接在计算器中使用）
        val formula = Formula(
            name = localName.ifBlank { originalName },
            description = description,
            category = category
        )
        saveFormula(formula, ingredients)

        return communityId
    }

    suspend fun updateCommunityFormulaName(id: Long, name: String) {
        communityFormulaDao.updateLocalName(id, name)
    }

    suspend fun toggleCommunityFormulaFavorite(id: Long, favorite: Boolean) {
        communityFormulaDao.updateFavorite(id, favorite)
    }

    suspend fun incrementCommunityFormulaUsage(id: Long) {
        communityFormulaDao.incrementUsage(id)
    }

    suspend fun deleteCommunityFormula(id: Long) {
        communityFormulaDao.deleteById(id)
    }

    private fun CommunityFormulaEntity.toDomain(): CommunityFormula {
        return CommunityFormula(
            id = id,
            originalName = originalName,
            localName = localName,
            description = description,
            category = category,
            author = author,
            sourceFile = sourceFile,
            ingredients = jsonToIngredients(ingredientsJson),
            importedAt = importedAt,
            isFavorite = isFavorite,
            usageCount = usageCount
        )
    }

    private fun ingredientsToJson(ingredients: List<Ingredient>): String {
        val arr = JSONArray()
        ingredients.forEach { ing ->
            val obj = JSONObject()
            obj.put("name", ing.name)
            obj.put("ratio", ing.ratio)
            obj.put("unit", ing.unit)
            obj.put("note", ing.note)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun jsonToIngredients(jsonStr: String): List<Ingredient> {
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Ingredient(
                    name = obj.getString("name"),
                    ratio = obj.getDouble("ratio"),
                    unit = obj.optString("unit", "%"),
                    note = obj.optString("note", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
