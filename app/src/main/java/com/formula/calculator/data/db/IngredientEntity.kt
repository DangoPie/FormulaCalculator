package com.formula.calculator.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["id"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("formulaId")]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val formulaId: Long,
    val name: String,
    val ratio: Double,          // 占比 (0~1 或 0~100，由 FormulaEntity 的 unit 决定)
    val unit: String = "%",     // 单位
    val sortOrder: Int = 0,     // 排序
    val note: String = ""       // 备注
)
