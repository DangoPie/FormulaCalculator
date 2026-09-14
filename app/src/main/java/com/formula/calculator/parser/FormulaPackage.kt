package com.formula.calculator.parser

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * 配方包格式定义
 * .formula 文件 = JSON 格式的配方包，包含完整配方 + 组分 + 元数据
 */
object FormulaPackage {

    const val FORMAT_VERSION = 1
    const val FILE_EXTENSION = ".formula"
    const val MIME_TYPE = "application/json"

    /**
     * 序列化配方为 JSON 字符串
     */
    fun serialize(
        formulaName: String,
        formulaDescription: String,
        formulaCategory: String,
        ingredients: List<IngredientData>,
        author: String = "",
        appVersion: String = ""
    ): String {
        val json = JSONObject()
        json.put("formatVersion", FORMAT_VERSION)

        val formulaObj = JSONObject()
        formulaObj.put("name", formulaName)
        formulaObj.put("description", formulaDescription)
        formulaObj.put("category", formulaCategory)
        json.put("formula", formulaObj)

        val ingredientsArr = JSONArray()
        ingredients.forEach { ing ->
            val ingObj = JSONObject()
            ingObj.put("name", ing.name)
            ingObj.put("ratio", ing.ratio)
            ingObj.put("unit", ing.unit)
            if (ing.note.isNotBlank()) ingObj.put("note", ing.note)
            ingredientsArr.put(ingObj)
        }
        json.put("ingredients", ingredientsArr)

        val meta = JSONObject()
        meta.put("author", author)
        meta.put("exportedAt", System.currentTimeMillis())
        meta.put("appVersion", appVersion)
        json.put("meta", meta)

        return json.toString(2)
    }

    /**
     * 从 JSON 字符串反序列化配方包
     */
    fun deserialize(jsonStr: String): Result<ParsedPackage> {
        return try {
            val json = JSONObject(jsonStr)
            val formatVersion = json.optInt("formatVersion", 1)

            val formulaObj = json.getJSONObject("formula")
            val name = formulaObj.getString("name")
            val description = formulaObj.optString("description", "")
            val category = formulaObj.optString("category", "")

            val ingredientsArr = json.getJSONArray("ingredients")
            val ingredients = (0 until ingredientsArr.length()).map { i ->
                val ingObj = ingredientsArr.getJSONObject(i)
                IngredientData(
                    name = ingObj.getString("name"),
                    ratio = ingObj.getDouble("ratio"),
                    unit = ingObj.optString("unit", "%"),
                    note = ingObj.optString("note", "")
                )
            }

            val meta = json.optJSONObject("meta")
            val author = meta?.optString("author", "") ?: ""
            val exportedAt = meta?.optLong("exportedAt", 0L) ?: 0L
            val appVersion = meta?.optString("appVersion", "") ?: ""

            Result.success(
                ParsedPackage(
                    formatVersion = formatVersion,
                    formulaName = name,
                    formulaDescription = description,
                    formulaCategory = category,
                    ingredients = ingredients,
                    author = author,
                    exportedAt = exportedAt,
                    appVersion = appVersion
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从 URI 读取并解析配方包
     */
    fun parseFromUri(context: Context, uri: Uri): Result<ParsedPackage> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法打开文件"))
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            deserialize(jsonStr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 写入配方包到指定 URI
     */
    fun writeToUri(context: Context, uri: Uri, jsonContent: String): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(jsonContent.toByteArray(Charsets.UTF_8))
            } ?: return Result.failure(Exception("无法写入文件"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class IngredientData(
        val name: String,
        val ratio: Double,
        val unit: String = "%",
        val note: String = ""
    )

    data class ParsedPackage(
        val formatVersion: Int,
        val formulaName: String,
        val formulaDescription: String,
        val formulaCategory: String,
        val ingredients: List<IngredientData>,
        val author: String,
        val exportedAt: Long,
        val appVersion: String
    )
}
