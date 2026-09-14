package com.formula.calculator.ui.calculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.data.db.CalculationHistoryEntity
import com.formula.calculator.domain.CalculationResult
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    val formulas: StateFlow<List<Formula>> = repository.getAllFormulas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 选中的配方
    private val _selectedFormulaId = MutableStateFlow<Long?>(null)
    val selectedFormulaId: StateFlow<Long?> = _selectedFormulaId.asStateFlow()

    // 配方组分（带锁定状态）
    private val _ingredients = MutableStateFlow<List<Ingredient>>(emptyList())
    val ingredients: StateFlow<List<Ingredient>> = _ingredients.asStateFlow()

    // 目标总量
    private val _targetAmount = MutableStateFlow("")
    val targetAmount: StateFlow<String> = _targetAmount.asStateFlow()

    // 目标单位
    private val _targetUnit = MutableStateFlow("g")
    val targetUnit: StateFlow<String> = _targetUnit.asStateFlow()

    // 计算结果
    private val _results = MutableStateFlow<List<CalculationResult>>(emptyList())
    val results: StateFlow<List<CalculationResult>> = _results.asStateFlow()

    // 是否已计算
    private val _hasCalculated = MutableStateFlow(false)
    val hasCalculated: StateFlow<Boolean> = _hasCalculated.asStateFlow()

    // 比例总和
    private val _ratioSum = MutableStateFlow(0.0)
    val ratioSum: StateFlow<Double> = _ratioSum.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // 选择配方
    fun selectFormula(formulaId: Long) {
        _selectedFormulaId.value = formulaId
        _hasCalculated.value = false
        _results.value = emptyList()

        viewModelScope.launch {
            val ingredients = repository.getIngredientsByFormulaSync(formulaId)
            _ingredients.value = ingredients
            _ratioSum.value = ingredients.sumOf { it.ratio }
        }
    }

    fun setTargetAmount(amount: String) {
        _targetAmount.value = amount
        _hasCalculated.value = false
    }

    fun setTargetUnit(unit: String) {
        _targetUnit.value = unit
    }

    // 切换组分锁定状态
    fun toggleLock(ingredientId: Long) {
        _ingredients.value = _ingredients.value.map {
            if (it.id == ingredientId) it.copy(isLocked = !it.isLocked) else it
        }
        _hasCalculated.value = false
    }

    // 手动调整某组分的比例
    fun updateRatio(ingredientId: Long, newRatio: Double) {
        _ingredients.value = _ingredients.value.map {
            if (it.id == ingredientId) it.copy(ratio = newRatio) else it
        }
        _ratioSum.value = _ingredients.value.sumOf { it.ratio }
        _hasCalculated.value = false
    }

    /**
     * 核心计算逻辑
     *
     * 场景1: 比例总和 = 100%（或接近）
     *   → 直接按占比计算实际用量
     *
     * 场景2: 比例总和 ≠ 100%
     *   → 先归一化（每个比例 / 总和 × 100%），再计算
     *
     * 场景3: 有锁定组分
     *   → 锁定组分按原始比例直接计算
     *   → 剩余量按未锁定组分的比例分配
     */
    fun calculate() {
        val target = _targetAmount.value.toDoubleOrNull()
        if (target == null || target <= 0) {
            _snackbarMessage.value = "请输入有效的目标总量"
            return
        }

        val ingredients = _ingredients.value
        if (ingredients.isEmpty()) {
            _snackbarMessage.value = "请先选择配方"
            return
        }

        val lockedIngredients = ingredients.filter { it.isLocked }
        val unlockedIngredients = ingredients.filter { !it.isLocked }

        val lockedTotal = lockedIngredients.sumOf { it.ratio }
        val totalRatio = ingredients.sumOf { it.ratio }

        val results = mutableListOf<CalculationResult>()

        if (lockedIngredients.isEmpty()) {
            // 无锁定组分：归一化后按比例计算
            val normalizedFactor = if (kotlin.math.abs(totalRatio - 100) < 0.01) {
                1.0 / 100.0  // 已经是百分比
            } else if (totalRatio <= 1.5) {
                1.0  // 小数形式，不需要归一化
            } else {
                1.0 / totalRatio  // 归一化
            }

            ingredients.forEach { ingredient ->
                val adjustedRatio = ingredient.ratio * normalizedFactor
                val amount = adjustedRatio * target
                results.add(
                    CalculationResult(
                        ingredientName = ingredient.name,
                        originalRatio = ingredient.ratio,
                        adjustedRatio = adjustedRatio * 100,
                        amount = amount,
                        unit = _targetUnit.value
                    )
                )
            }
        } else {
            // 有锁定组分
            // 锁定组分：按其在锁定总量中的占比，计算其占目标总量的份额
            val lockedRatioSum = lockedIngredients.sumOf { it.ratio }
            val lockedFraction = lockedRatioSum / totalRatio  // 锁定组分占总量的比例

            lockedIngredients.forEach { ingredient ->
                val fraction = ingredient.ratio / totalRatio
                val amount = fraction * target
                results.add(
                    CalculationResult(
                        ingredientName = ingredient.name,
                        originalRatio = ingredient.ratio,
                        adjustedRatio = fraction * 100,
                        amount = amount,
                        unit = _targetUnit.value
                    )
                )
            }

            // 未锁定组分：分配剩余量
            val remainingTarget = target * (1.0 - lockedFraction)
            val unlockedRatioSum = unlockedIngredients.sumOf { it.ratio }

            if (unlockedRatioSum > 0) {
                unlockedIngredients.forEach { ingredient ->
                    val fraction = ingredient.ratio / unlockedRatioSum
                    val amount = fraction * remainingTarget
                    results.add(
                        CalculationResult(
                            ingredientName = ingredient.name,
                            originalRatio = ingredient.ratio,
                            adjustedRatio = fraction * (1.0 - lockedFraction) * 100,
                            amount = amount,
                            unit = _targetUnit.value
                        )
                    )
                }
            }
        }

        _results.value = results
        _hasCalculated.value = true
        _snackbarMessage.value = "计算完成"
    }

    // 保存计算结果到历史
    fun saveToHistory() {
        val formulaId = _selectedFormulaId.value ?: return
        val target = _targetAmount.value.toDoubleOrNull() ?: return
        val results = _results.value
        if (results.isEmpty()) return

        viewModelScope.launch {
            val formula = repository.getFormulaById(formulaId) ?: return@launch

            val jsonArray = JSONArray()
            results.forEach { r ->
                val obj = JSONObject()
                obj.put("name", r.ingredientName)
                obj.put("originalRatio", r.originalRatio)
                obj.put("adjustedRatio", r.adjustedRatio)
                obj.put("amount", r.amount)
                obj.put("unit", r.unit)
                jsonArray.put(obj)
            }

            repository.saveHistory(
                CalculationHistoryEntity(
                    formulaId = formulaId,
                    formulaName = formula.name,
                    targetAmount = target,
                    targetUnit = _targetUnit.value,
                    resultsJson = jsonArray.toString()
                )
            )
            _snackbarMessage.value = "已保存到历史记录"
        }
    }

    // 重置
    fun reset() {
        _targetAmount.value = ""
        _hasCalculated.value = false
        _results.value = emptyList()
        _ingredients.value = _ingredients.value.map { it.copy(isLocked = false) }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
