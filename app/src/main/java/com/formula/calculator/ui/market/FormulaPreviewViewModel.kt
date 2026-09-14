package com.formula.calculator.ui.market

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.domain.Ingredient
import com.formula.calculator.parser.FormulaPackage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FormulaPreviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    private val _parsedPackage = MutableStateFlow<FormulaPackage.ParsedPackage?>(null)
    val parsedPackage: StateFlow<FormulaPackage.ParsedPackage?> = _parsedPackage.asStateFlow()

    private val _localName = MutableStateFlow("")
    val localName: StateFlow<String> = _localName.asStateFlow()

    private val _sourceFile = MutableStateFlow("")
    val sourceFile: StateFlow<String> = _sourceFile.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _importSuccess = MutableStateFlow(false)
    val importSuccess: StateFlow<Boolean> = _importSuccess.asStateFlow()

    fun loadFromUri(uri: Uri, fileName: String) {
        _sourceFile.value = fileName
        viewModelScope.launch {
            FormulaPackage.parseFromUri(getApplication(), uri)
                .onSuccess { pkg ->
                    _parsedPackage.value = pkg
                    _localName.value = pkg.formulaName
                }
                .onFailure { error ->
                    _snackbarMessage.value = "解析失败: ${error.message}"
                }
        }
    }

    fun loadFromPackage(pkg: FormulaPackage.ParsedPackage, fileName: String = "") {
        _parsedPackage.value = pkg
        _localName.value = pkg.formulaName
        _sourceFile.value = fileName
    }

    fun setLocalName(name: String) { _localName.value = name }

    fun importFormula() {
        val pkg = _parsedPackage.value ?: return
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
                localName = _localName.value.ifBlank { pkg.formulaName },
                description = pkg.formulaDescription,
                category = pkg.formulaCategory,
                author = pkg.author,
                sourceFile = _sourceFile.value,
                ingredients = ingredients
            )
            _importSuccess.value = true
            _snackbarMessage.value = "已导入「${_localName.value.ifBlank { pkg.formulaName }}」"
        }
    }

    fun clearSnackbar() { _snackbarMessage.value = null }
}
