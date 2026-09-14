package com.formula.calculator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "community_formulas")
data class CommunityFormulaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalName: String,       // 原始配方名
    val localName: String,          // 本地别名（导入后可改）
    val description: String = "",
    val category: String = "",
    val author: String = "",        // 原作者
    val sourceFile: String = "",    // 来源文件名
    val ingredientsJson: String,    // 组分列表 JSON
    val importedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val usageCount: Int = 0
)
