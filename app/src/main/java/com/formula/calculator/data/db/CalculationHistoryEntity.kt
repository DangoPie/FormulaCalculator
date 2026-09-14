package com.formula.calculator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_history")
data class CalculationHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formulaId: Long,
    val formulaName: String,
    val targetAmount: Double,
    val targetUnit: String = "g",
    val resultsJson: String,    // JSON 格式存储计算结果
    val createdAt: Long = System.currentTimeMillis()
)
