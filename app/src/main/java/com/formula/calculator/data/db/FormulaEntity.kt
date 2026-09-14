package com.formula.calculator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "formulas")
data class FormulaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val category: String = "",        // 分类标签，方便筛选
    val isCompleted: Boolean = false, // 批次模式：是否已完成
    val batchOrder: Int = -1,         // 批次模式中的排序，-1 表示不参与批次
    val batchGroup: String = "",      // 批次组名（同组配方统一操作）
    val targetAmount: Double = 0.0,   // 目标量（克），0 表示未设置
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
