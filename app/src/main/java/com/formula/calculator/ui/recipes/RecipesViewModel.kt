package com.formula.calculator.ui.recipes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecipesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    val formulas: StateFlow<List<Formula>> = repository.getAllFormulas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI State
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _showEditDialog = MutableStateFlow(false)
    val showEditDialog: StateFlow<Boolean> = _showEditDialog.asStateFlow()

    private val _editingFormula = MutableStateFlow<Formula?>(null)
    val editingFormula: StateFlow<Formula?> = _editingFormula.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // 搜索
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredFormulas: StateFlow<List<Formula>> = combine(formulas, selectedCategory, searchQuery) { all, category, query ->
        var result = all
        if (category != null) {
            result = result.filter { it.category == category }
        }
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.category.contains(query, ignoreCase = true)
            }
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    // ============ 新建/编辑 ============

    fun showCreateDialog() {
        _editingFormula.value = null
        _showEditDialog.value = true
    }

    fun showEditDialog(formula: Formula) {
        viewModelScope.launch {
            val ingredients = repository.getIngredientsByFormulaSync(formula.id)
            _editingFormula.value = formula.copy(ingredients = ingredients)
            _showEditDialog.value = true
        }
    }

    fun dismissEditDialog() {
        _showEditDialog.value = false
        _editingFormula.value = null
    }

    fun saveFormula(name: String, description: String, category: String, ingredients: List<Ingredient>) {
        if (name.isBlank()) {
            _snackbarMessage.value = "配方名称不能为空"
            return
        }
        if (ingredients.isEmpty()) {
            _snackbarMessage.value = "至少需要一个组分"
            return
        }

        viewModelScope.launch {
            val existing = _editingFormula.value
            val formula = if (existing != null) {
                existing.copy(name = name, description = description, category = category)
            } else {
                Formula(name = name, description = description, category = category)
            }
            repository.saveFormula(formula, ingredients)
            _showEditDialog.value = false
            _snackbarMessage.value = if (existing != null) "配方已更新" else "配方「$name」已创建"
        }
    }

    // ============ 删除 ============

    fun deleteFormula(formula: Formula) {
        viewModelScope.launch {
            repository.deleteFormula(formula.id)
            _snackbarMessage.value = "配方「${formula.name}」已删除"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
