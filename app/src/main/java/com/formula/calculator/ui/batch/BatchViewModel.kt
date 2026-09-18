package com.formula.calculator.ui.batch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.domain.CalculationResult
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 配料执行 ViewModel
 * 按组显示配方，支持一键计算（统一目标量），显示实际克数
 */
class BatchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    // 所有批次组名
    val batchGroups: StateFlow<List<String>> = repository.getBatchGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前选中的组
    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup: StateFlow<String?> = _selectedGroup.asStateFlow()

    // 当前组的配方列表
    val groupFormulas: StateFlow<List<Formula>> = _selectedGroup.flatMapLatest { group ->
        if (group != null) repository.getFormulasByBatchGroup(group)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前查看的配方索引
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // 当前展开的配方索引（-1表示全部收起）
    private val _expandedIndex = MutableStateFlow(-1)
    val expandedIndex: StateFlow<Int> = _expandedIndex.asStateFlow()

    // 单个配方的单独目标量（临时覆盖）
    private val _formulaOverrides = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val formulaOverrides: StateFlow<Map<Long, Double>> = _formulaOverrides.asStateFlow()

    // 当前配方的组分
    private val _currentIngredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val currentIngredients: StateFlow<List<Ingredient>> = _currentIngredients.asStateFlow()

    // 当前配方的计算结果
    val currentResults: StateFlow<List<CalculationResult>> = combine(
        _currentIngredients, groupFormulas, _currentIndex
    ) { ingredients, formulas, idx ->
        val formula = formulas.getOrNull(idx) ?: return@combine emptyList<CalculationResult>()
        if (formula.targetAmount <= 0) return@combine emptyList()
        calculateAmounts(ingredients, formula.targetAmount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 组目标量（从当前组第一个配方读取）
    val groupTargetAmount: StateFlow<Double> = groupFormulas.map { formulas ->
        formulas.firstOrNull()?.targetAmount ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        // 自动选中第一个组
        viewModelScope.launch {
            batchGroups.collect { groups ->
                if (groups.isNotEmpty() && _selectedGroup.value == null) {
                    _selectedGroup.value = groups.first()
                }
            }
        }
        // 当展开的配方变化时，加载该项的组分
        viewModelScope.launch {
            combine(groupFormulas, _expandedIndex) { formulas, idx ->
                if (formulas.isNotEmpty() && idx >= 0 && idx < formulas.size) formulas[idx] else null
            }.collect { formula ->
                if (formula != null) {
                    _currentIngredients.value = repository.getIngredientsByFormulaSync(formula.id)
                } else {
                    _currentIngredients.value = emptyList()
                }
            }
        }
    }

    fun selectGroup(group: String) {
        _selectedGroup.value = group
        _currentIndex.value = 0
    }

    fun goToNext() {
        val formulas = groupFormulas.value
        if (_currentIndex.value < formulas.size - 1) {
            _currentIndex.value++
        }
    }

    fun goToPrevious() {
        if (_currentIndex.value > 0) {
            _currentIndex.value--
        }
    }

    fun goToIndex(index: Int) {
        val formulas = groupFormulas.value
        if (index in formulas.indices) {
            _currentIndex.value = index
        }
    }

    /**
     * 切换配方展开/收起状态
     */
    fun toggleExpanded(index: Int) {
        _expandedIndex.value = if (_expandedIndex.value == index) -1 else index
        _currentIndex.value = index
    }

    /**
     * 设置单个配方的单独目标量（覆盖全组目标量）
     */
    fun setFormulaTargetAmount(formulaId: Long, amount: Double) {
        if (amount > 0) {
            _formulaOverrides.value = _formulaOverrides.value + (formulaId to amount)
        } else {
            _formulaOverrides.value = _formulaOverrides.value - formulaId
        }
    }

    /**
     * 获取配方的有效目标量（优先使用单独设置的，否则用全组的）
     */
    fun getEffectiveTargetAmount(formula: Formula): Double {
        return _formulaOverrides.value[formula.id] ?: formula.targetAmount
    }

    fun toggleCompleted() {
        val formulas = groupFormulas.value
        val idx = _expandedIndex.value
        if (idx >= 0 && idx < formulas.size) {
            val formula = formulas[idx]
            viewModelScope.launch {
                repository.updateCompleted(formula.id, !formula.isCompleted)
            }
        }
    }

    /**
     * 一键计算：设置整组的目标量并计算
     */
    fun setGroupTargetAmount(amount: Double) {
        val group = _selectedGroup.value ?: return
        viewModelScope.launch {
            repository.updateGroupTargetAmount(group, amount)
            _snackbarMessage.value = "已设置全组目标量为 ${formatNum(amount)}g"
        }
    }

    /**
     * 整组标记完成
     */
    fun completeGroup() {
        val group = _selectedGroup.value ?: return
        viewModelScope.launch {
            repository.completeGroup(group)
            _snackbarMessage.value = "全组已标记完成"
        }
    }

    /**
     * 整组标记未完成
     */
    fun uncompleteGroup() {
        val group = _selectedGroup.value ?: return
        viewModelScope.launch {
            repository.uncompleteGroup(group)
            _snackbarMessage.value = "全组已标记未完成"
        }
    }

    fun clearBatchStatus() {
        viewModelScope.launch {
            repository.clearBatchStatus()
            _selectedGroup.value = null
            _currentIndex.value = 0
            _snackbarMessage.value = "批次状态已重置"
        }
    }

    /**
     * 核心计算逻辑：根据目标量计算各组分的实际用量
     */
    private fun calculateAmounts(ingredients: List<Ingredient>, targetAmount: Double): List<CalculationResult> {
        if (ingredients.isEmpty() || targetAmount <= 0) return emptyList()

        val totalRatio = ingredients.sumOf { it.ratio }
        if (totalRatio <= 0) return emptyList()

        return ingredients.map { ing ->
            val adjustedRatio = ing.ratio / totalRatio  // 归一化
            val amount = adjustedRatio * targetAmount
            CalculationResult(
                ingredientName = ing.name,
                originalRatio = ing.ratio,
                adjustedRatio = adjustedRatio,
                amount = amount,
                unit = "g"
            )
        }
    }

    fun clearSnackbar() { _snackbarMessage.value = null }

    companion object {
        fun formatNum(value: Double): String {
            return if (value == value.toLong().toDouble()) value.toLong().toString()
            else String.format("%.2f", value)
        }
    }
}
