package com.formula.calculator.ui.market

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.domain.CommunityFormula
import com.formula.calculator.domain.Ingredient
import com.formula.calculator.parser.FormulaPackage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MarketViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    // 搜索
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 过滤：全部 / 收藏
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    // 配方列表
    val communityFormulas: StateFlow<List<CommunityFormula>> = combine(
        _searchQuery, _showFavoritesOnly
    ) { query, favOnly ->
        Pair(query, favOnly)
    }.flatMapLatest { (query, favOnly) ->
        when {
            favOnly -> repository.getFavoriteCommunityFormulas()
            query.isNotBlank() -> repository.searchCommunityFormulas(query)
            else -> repository.getAllCommunityFormulas()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // 编辑对话框
    private val _editingFormula = MutableStateFlow<CommunityFormula?>(null)
    val editingFormula: StateFlow<CommunityFormula?> = _editingFormula.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun toggleFavoritesFilter() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    fun showEditDialog(formula: CommunityFormula) { _editingFormula.value = formula }
    fun dismissEditDialog() { _editingFormula.value = null }

    fun updateName(id: Long, name: String) {
        viewModelScope.launch {
            repository.updateCommunityFormulaName(id, name)
            _editingFormula.value = null
            _snackbarMessage.value = "已更新别名"
        }
    }

    fun toggleFavorite(formula: CommunityFormula) {
        viewModelScope.launch {
            repository.toggleCommunityFormulaFavorite(formula.id, !formula.isFavorite)
        }
    }

    fun deleteFormula(formula: CommunityFormula) {
        viewModelScope.launch {
            repository.deleteCommunityFormula(formula.id)
            _snackbarMessage.value = "已删除「${formula.displayName}」"
        }
    }

    /**
     * 从 .formula 文件导入社区配方
     */
    fun importFromPackage(
        pkg: FormulaPackage.ParsedPackage,
        localName: String,
        sourceFile: String = ""
    ) {
        viewModelScope.launch {
            val ingredients = pkg.ingredients.mapIndexed { idx, ing ->
                Ingredient(
                    name = ing.name,
                    ratio = ing.ratio,
                    unit = ing.unit,
                    note = ing.note,
                    sortOrder = idx
                )
            }
            repository.importCommunityFormula(
                originalName = pkg.formulaName,
                localName = localName.ifBlank { pkg.formulaName },
                description = pkg.formulaDescription,
                category = pkg.formulaCategory,
                author = pkg.author,
                sourceFile = sourceFile,
                ingredients = ingredients
            )
            _snackbarMessage.value = "已导入「${localName.ifBlank { pkg.formulaName }}」"
        }
    }

    fun incrementUsage(formula: CommunityFormula) {
        viewModelScope.launch {
            repository.incrementCommunityFormulaUsage(formula.id)
        }
    }

    fun clearSnackbar() { _snackbarMessage.value = null }
}
