package com.formula.calculator.domain

/**
 * 配方领域模型 - UI 层使用的数据类
 */
data class Formula(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val category: String = "",
    val isCompleted: Boolean = false,
    val batchOrder: Int = -1,
    val batchGroup: String = "",
    val targetAmount: Double = 0.0,
    val ingredients: List<Ingredient> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Ingredient(
    val id: Long = 0,
    val formulaId: Long = 0,
    val name: String,
    val ratio: Double,          // 原始占比值
    val unit: String = "%",     // 单位
    val sortOrder: Int = 0,
    val note: String = "",
    val isLocked: Boolean = false  // 锁定状态（计算时锁定不参与调整）
)

/**
 * 计算结果
 */
data class CalculationResult(
    val ingredientName: String,
    val originalRatio: Double,      // 原始占比
    val adjustedRatio: Double,      // 调整后的占比（归一化后）
    val amount: Double,             // 实际用量
    val unit: String                // 单位
)

/**
 * 社区配方（市场导入的配方）
 */
data class CommunityFormula(
    val id: Long = 0,
    val originalName: String,
    val localName: String,
    val description: String = "",
    val category: String = "",
    val author: String = "",
    val sourceFile: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val importedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val usageCount: Int = 0
) {
    val displayName: String get() = localName.ifBlank { originalName }
}
