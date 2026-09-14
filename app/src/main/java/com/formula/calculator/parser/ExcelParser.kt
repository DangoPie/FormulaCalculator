package com.formula.calculator.parser

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Excel/CSV 解析器
 * 返回原始数据，由 UI 层决定列映射
 */
object ExcelParser {

    /**
     * 原始解析结果 - 包含所有 Sheet 的原始数据
     */
    data class RawParseResult(
        val sheets: List<SheetData>,
        val warnings: List<String> = emptyList()
    )

    data class SheetData(
        val name: String,
        val headers: List<String>,       // 表头行内容
        val headerRowIndex: Int,         // 表头行索引
        val rows: List<List<String>>,    // 数据行（全部转为字符串）
        val columnTypes: List<ColumnType> // 每列的类型推断
    )

    enum class ColumnType {
        TEXT,       // 纯文本
        NUMERIC,    // 数值
        MIXED,      // 混合
        EMPTY       // 空列
    }

    /**
     * 根据文件扩展名自动选择解析方式
     */
    fun parse(context: Context, uri: Uri, fileName: String): Result<RawParseResult> {
        return try {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            when (extension) {
                "xlsx", "xls" -> parseExcel(context, uri)
                "csv" -> parseCsv(context, uri)
                else -> Result.failure(IllegalArgumentException("不支持的文件格式: $extension"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ Excel 解析 ============

    private fun parseExcel(context: Context, uri: Uri): Result<RawParseResult> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("无法打开文件"))
            val workbook = WorkbookFactory.create(inputStream)
            val sheets = mutableListOf<SheetData>()
            val warnings = mutableListOf<String>()

            for (sheetIdx in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIdx)
                val sheetData = parseSheet(sheet)
                if (sheetData != null) {
                    sheets.add(sheetData)
                }
            }

            workbook.close()

            if (sheets.isEmpty()) {
                warnings.add("未能从文件中解析到有效数据")
            }

            Result.success(RawParseResult(sheets = sheets, warnings = warnings))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSheet(sheet: Sheet): SheetData? {
        val maxRows = minOf(sheet.lastRowNum + 1, 500)
        if (maxRows <= 0) return null

        // 找到最大列数
        var maxCols = 0
        for (rowIdx in 0 until maxRows) {
            val row = sheet.getRow(rowIdx) ?: continue
            maxCols = maxOf(maxCols, row.lastCellNum.toInt())
        }
        if (maxCols <= 0) return null

        // 读取所有行的原始数据
        val allRows = mutableListOf<List<String>>()
        for (rowIdx in 0 until maxRows) {
            val row = sheet.getRow(rowIdx)
            val rowData = mutableListOf<String>()
            for (colIdx in 0 until maxCols) {
                val cell = row?.getCell(colIdx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                rowData.add(getCellText(cell))
            }
            allRows.add(rowData)
        }

        if (allRows.isEmpty()) return null

        // 智能检测表头行
        val headerInfo = detectHeaderRow(allRows, maxCols)
        val headerRowIndex = headerInfo.first
        val headers = headerInfo.second

        // 提取数据行（表头之后的行）
        val dataRows = allRows.drop(headerRowIndex + 1).filter { row ->
            row.any { it.isNotBlank() }  // 过滤空行
        }

        // 推断每列的类型
        val columnTypes = inferColumnTypes(dataRows, maxCols)

        return SheetData(
            name = sheet.sheetName,
            headers = headers,
            headerRowIndex = headerRowIndex,
            rows = dataRows,
            columnTypes = columnTypes
        )
    }

    /**
     * 智能检测表头行
     * 策略1: 寻找包含关键词的行
     * 策略2: 寻找第一行既有文本又有数字的行
     * 策略3: 默认第一行
     */
    private fun detectHeaderRow(allRows: List<List<String>>, maxCols: Int): Pair<Int, List<String>> {
        // 策略1: 关键词匹配
        val headerKeywords = setOf(
            "名称", "组分", "成分", "材料", "原料", "物料", "name",
            "比例", "含量", "占比", "配比", "质量", "ratio", "proportion", "percentage",
            "编号", "序号", "no", "index", "总和", "total"
        )

        for ((rowIdx, row) in allRows.withIndex()) {
            if (rowIdx > 20) break
            val matchCount = row.count { cell ->
                val text = cell.trim().lowercase()
                headerKeywords.any { keyword -> text.contains(keyword) }
            }
            if (matchCount >= 2) {
                return Pair(rowIdx, row)
            }
        }

        // 策略2: 寻找有文本列和数值列的行
        for ((rowIdx, row) in allRows.withIndex()) {
            if (rowIdx > 20) break
            val textCols = row.count { it.isNotBlank() && it.toDoubleOrNull() == null }
            val numCols = row.count { it.toDoubleOrNull() != null }
            if (textCols >= 1 && numCols >= 1) {
                return Pair(rowIdx, row)
            }
        }

        // 策略3: 默认第一行
        return Pair(0, allRows.firstOrNull() ?: listOf(""))
    }

    /**
     * 推断每列的数据类型
     */
    private fun inferColumnTypes(rows: List<List<String>>, maxCols: Int): List<ColumnType> {
        return (0 until maxCols).map { colIdx ->
            var textCount = 0
            var numCount = 0
            var emptyCount = 0

            rows.forEach { row ->
                val cell = row.getOrElse(colIdx) { "" }.trim()
                when {
                    cell.isEmpty() -> emptyCount++
                    cell.toDoubleOrNull() != null -> numCount++
                    else -> textCount++
                }
            }

            when {
                textCount == 0 && numCount == 0 -> ColumnType.EMPTY
                textCount > 0 && numCount > 0 -> ColumnType.MIXED
                numCount > 0 -> ColumnType.NUMERIC
                else -> ColumnType.TEXT
            }
        }
    }

    // ============ CSV 解析 ============

    private fun parseCsv(context: Context, uri: Uri): Result<RawParseResult> {
        return try {
            val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(uri), "UTF-8"))
            val lines = reader.readLines()
            reader.close()

            if (lines.isEmpty()) {
                return Result.failure(Exception("CSV 文件为空"))
            }

            val allRows = lines.map { parseCsvLine(it) }
            val maxCols = allRows.maxOf { it.size }
            val paddedRows = allRows.map { row ->
                row + List(maxCols - row.size) { "" }
            }

            val headerInfo = detectHeaderRow(paddedRows, maxCols)
            val dataRows = paddedRows.drop(headerInfo.first + 1).filter { row ->
                row.any { it.isNotBlank() }
            }
            val columnTypes = inferColumnTypes(dataRows, maxCols)

            Result.success(
                RawParseResult(
                    sheets = listOf(
                        SheetData(
                            name = "Sheet1",
                            headers = headerInfo.second,
                            headerRowIndex = headerInfo.first,
                            rows = dataRows,
                            columnTypes = columnTypes
                        )
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============ 工具方法 ============

    private fun getCellText(cell: Cell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue.trim()
                CellType.NUMERIC -> {
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) value.toLong().toString()
                    else {
                        // 保留合理精度
                        val formatted = String.format("%.6f", value).trimEnd('0').trimEnd('.')
                        formatted
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    try { cell.stringCellValue.trim() } catch (e: Exception) {
                        try {
                            val v = cell.numericCellValue
                            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                        } catch (e2: Exception) { "" }
                    }
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
