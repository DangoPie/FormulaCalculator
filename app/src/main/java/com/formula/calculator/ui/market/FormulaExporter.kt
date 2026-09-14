package com.formula.calculator.ui.market

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import com.formula.calculator.parser.FormulaPackage
import java.io.File

/**
 * 配方导出与分享工具
 */
object FormulaExporter {

    /**
     * 将配方导出为 .formula 文件并通过系统分享
     */
    fun shareFormula(
        context: Context,
        formula: Formula,
        ingredients: List<Ingredient>,
        author: String = ""
    ) {
        val ingredientData = ingredients.map { ing ->
            FormulaPackage.IngredientData(
                name = ing.name,
                ratio = ing.ratio,
                unit = ing.unit,
                note = ing.note
            )
        }

        val json = FormulaPackage.serialize(
            formulaName = formula.name,
            formulaDescription = formula.description,
            formulaCategory = formula.category,
            ingredients = ingredientData,
            author = author.ifBlank { "本地用户" },
            appVersion = getAppVersion(context)
        )

        // 写入缓存文件
        val cacheDir = File(context.cacheDir, "shared_formulas")
        cacheDir.mkdirs()
        val fileName = "${formula.name}${FormulaPackage.FILE_EXTENSION}"
        val file = File(cacheDir, fileName)
        file.writeText(json, Charsets.UTF_8)

        // 通过 FileProvider 获取 URI 并分享
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "配方分享: ${formula.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "分享配方「${formula.name}」")
        )
    }

    /**
     * 生成配方包 JSON 字符串（供外部使用）
     */
    fun toJson(
        formula: Formula,
        ingredients: List<Ingredient>,
        author: String = ""
    ): String {
        val ingredientData = ingredients.map { ing ->
            FormulaPackage.IngredientData(
                name = ing.name,
                ratio = ing.ratio,
                unit = ing.unit,
                note = ing.note
            )
        }
        return FormulaPackage.serialize(
            formulaName = formula.name,
            formulaDescription = formula.description,
            formulaCategory = formula.category,
            ingredients = ingredientData,
            author = author,
            appVersion = ""
        )
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
