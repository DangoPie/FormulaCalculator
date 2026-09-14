package com.formula.calculator.ui.recipes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import com.formula.calculator.parser.ExcelParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 导入向导 ViewModel（简化版）
 * 流程：选Sheet → 选列（名称列+组分列）→ 预览 → 导入
 * 每行 = 一个独立配方，选中的数值列 = 该配方的各组分
 */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    enum class ImportStep { IDLE, SELECT_SHEET, MAP_COLUMNS, PREVIEW, DONE }

    private val _step = MutableStateFlow(ImportStep.IDLE)
    val step: StateFlow<ImportStep> = _step.asStateFlow()

    private val _rawResult = MutableStateFlow<ExcelParser.RawParseResult?>(null)
    val rawResult: StateFlow<ExcelParser.RawParseResult?> = _rawResult.asStateFlow()

    // Sheet
    private val _selectedSheetIndex = MutableStateFlow(0)
    val selectedSheetIndex: StateFlow<Int> = _selectedSheetIndex.asStateFlow()

    val currentSheet: StateFlow<ExcelParser.SheetData?> = combine(_rawResult, _selectedSheetIndex) { result, idx ->
        result?.sheets?.getOrNull(idx)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Column mapping
    private val _nameColumnIndex = MutableStateFlow(-1)
    val nameColumnIndex: StateFlow<Int> = _nameColumnIndex.asStateFlow()

    private val _valueColumnIndices = MutableStateFlow<List<Int>>(emptyList())
    val valueColumnIndices: StateFlow<List<Int>> = _valueColumnIndices.asStateFlow()

    // Row filtering
    private val _excludedRows = MutableStateFlow<Set<Int>>(emptySet())
    val excludedRows: StateFlow<Set<Int>> = _excludedRows.asStateFlow()

    // Formula config
    private val _formulaCategory = MutableStateFlow("")
    val formulaCategory: StateFlow<String> = _formulaCategory.asStateFlow()

    private val _batchGroupName = MutableStateFlow("")
    val batchGroupName: StateFlow<String> = _batchGroupName.asStateFlow()

    /**
     * 预览：每行是一个配方，选中的列是组分
     * 返回 List<Pair<配方名, List<Pair<组分名, 值>>>>
     */
    val previewFormulas: StateFlow<List<Pair<String, List<Pair<String, Double>>>>> = combine(
        currentSheet, _nameColumnIndex, _valueColumnIndices, _excludedRows
    ) { sheet, nameCol, valueCols, excluded ->
        if (sheet == null || nameCol < 0 || valueCols.isEmpty())
            return@combine emptyList()

        sheet.rows.mapIndexedNotNull { rowIdx, row ->
            if (rowIdx in excluded) return@mapIndexedNotNull null
            val name = row.getOrElse(nameCol) { "" }.trim()
            if (name.isBlank()) return@mapIndexedNotNull null

            val ingredients = valueCols.mapNotNull { colIdx ->
                val colName = sheet.headers.getOrElse(colIdx) { "列${colIdx + 1}" }
                val valueStr = row.getOrElse(colIdx) { "" }.trim().replace("%", "")
                val value = valueStr.toDoubleOrNull()
                if (value != null && value > 0) Pair(colName, value) else null
            }

            if (ingredients.isNotEmpty()) Pair(name, ingredients) else null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _importSuccess = MutableStateFlow(false)
    val importSuccess: StateFlow<Boolean> = _importSuccess.asStateFlow()

    private val _importedCount = MutableStateFlow(0)
    val importedCount: StateFlow<Int> = _importedCount.asStateFlow()

    // ============ 流程控制 ============

    fun parseFile(uri: Uri, fileName: String) {
        _isLoading.value = true

        viewModelScope.launch {
            val result = ExcelParser.parse(getApplication(), uri, fileName)
            result.onSuccess { rawResult ->
                _rawResult.value = rawResult
                if (rawResult.sheets.size > 1) {
                    _step.value = ImportStep.SELECT_SHEET
                } else {
                    _selectedSheetIndex.value = 0
                    autoDetectColumns()
                    _step.value = ImportStep.MAP_COLUMNS
                }
            }.onFailure { error ->
                _snackbarMessage.value = "导入失败: ${error.message}"
            }
            _isLoading.value = false
        }
    }

    fun selectSheet(index: Int) {
        _selectedSheetIndex.value = index
        autoDetectColumns()
        _step.value = ImportStep.MAP_COLUMNS
    }

    private fun autoDetectColumns() {
        val sheet = currentSheet.value ?: return

        // 找名称列：优先匹配关键词，否则取第一个文本列
        val nameKeywordCols = sheet.headers.indices.filter { idx ->
            val h = sheet.headers.getOrElse(idx) { "" }.lowercase()
            h.contains("编号") || h.contains("名称") || h.contains("组分") ||
                    h.contains("name") || h.contains("序号") || h.contains("实验号")
        }
        val textCols = sheet.headers.indices.filter { idx ->
            sheet.columnTypes.getOrElse(idx) { ExcelParser.ColumnType.EMPTY } == ExcelParser.ColumnType.TEXT
        }
        _nameColumnIndex.value = when {
            nameKeywordCols.isNotEmpty() -> nameKeywordCols.first()
            textCols.isNotEmpty() -> textCols.first()
            else -> 0
        }

        // 找数值列：所有数值/混合类型列，排除合计列和名称列
        val numericCols = sheet.headers.indices.filter { idx ->
            val type = sheet.columnTypes.getOrElse(idx) { ExcelParser.ColumnType.EMPTY }
            type == ExcelParser.ColumnType.NUMERIC || type == ExcelParser.ColumnType.MIXED
        }
        val nonTotalCols = numericCols.filter { idx ->
            val h = sheet.headers.getOrElse(idx) { "" }.lowercase()
            !h.contains("总和") && !h.contains("total") && !h.contains("sum") && !h.contains("合计")
        }
        val candidateCols = nonTotalCols.ifEmpty { numericCols }
        _valueColumnIndices.value = candidateCols.filter { it != _nameColumnIndex.value }

        autoFilterRows()
    }

    private fun autoFilterRows() {
        val sheet = currentSheet.value ?: return
        val nameCol = _nameColumnIndex.value
        if (nameCol < 0) return

        val skipKeywords = setOf("最小值", "最大值", "min", "max", "合计", "总和", "total", "sum", "平均")
        val excluded = mutableSetOf<Int>()

        sheet.rows.forEachIndexed { rowIdx, row ->
            val nameCell = row.getOrElse(nameCol) { "" }.trim().lowercase()
            if (skipKeywords.any { nameCell.contains(it) }) {
                excluded.add(rowIdx)
            }
        }
        _excludedRows.value = excluded
    }

    fun setNameColumn(index: Int) {
        _nameColumnIndex.value = index
        _valueColumnIndices.value = _valueColumnIndices.value.filter { it != index }
        autoFilterRows()
    }

    fun toggleValueColumn(index: Int) {
        _valueColumnIndices.value = if (index in _valueColumnIndices.value) {
            _valueColumnIndices.value - index
        } else {
            (_valueColumnIndices.value + index).sorted()
        }
    }

    fun toggleRowExcluded(rowIdx: Int) {
        _excludedRows.value = if (rowIdx in _excludedRows.value) {
            _excludedRows.value - rowIdx
        } else {
            _excludedRows.value + rowIdx
        }
    }

    fun setFormulaCategory(category: String) { _formulaCategory.value = category }
    fun setBatchGroupName(name: String) { _batchGroupName.value = name }

    // Navigation
    fun goToPreview() {
        if (_nameColumnIndex.value < 0 || _valueColumnIndices.value.isEmpty()) {
            _snackbarMessage.value = "请选择名称列和至少一个组分列"
            return
        }
        _step.value = ImportStep.PREVIEW
    }

    fun goBack() {
        _step.value = when (_step.value) {
            ImportStep.PREVIEW -> ImportStep.MAP_COLUMNS
            ImportStep.MAP_COLUMNS -> {
                if ((_rawResult.value?.sheets?.size ?: 0) > 1) ImportStep.SELECT_SHEET
                else ImportStep.IDLE
            }
            ImportStep.SELECT_SHEET -> ImportStep.IDLE
            else -> ImportStep.IDLE
        }
    }

    fun cancel() {
        _step.value = ImportStep.IDLE
        _rawResult.value = null
        _selectedSheetIndex.value = 0
        _nameColumnIndex.value = -1
        _valueColumnIndices.value = emptyList()
        _excludedRows.value = emptySet()
        _formulaCategory.value = ""
        _batchGroupName.value = ""
        _importSuccess.value = false
        _importedCount.value = 0
    }

    // ============ 确认导入 ============

    fun confirmImport() {
        val formulas = previewFormulas.value
        val category = _formulaCategory.value

        viewModelScope.launch {
            if (formulas.isEmpty()) {
                _snackbarMessage.value = "没有可导入的数据"
                return@launch
            }
            val batchData = formulas.map { (name, ingredients) ->
                val list = ingredients.mapIndexed { i, (n, v) ->
                    Ingredient(name = n, ratio = v, sortOrder = i)
                }
                Pair(name, list)
            }
            repository.batchImport(category, batchData, _batchGroupName.value)
            _importedCount.value = formulas.size

            _step.value = ImportStep.DONE
            _importSuccess.value = true
            _snackbarMessage.value = "导入成功，共 ${formulas.size} 组配方"
        }
    }

    fun clearSnackbar() { _snackbarMessage.value = null }
}
