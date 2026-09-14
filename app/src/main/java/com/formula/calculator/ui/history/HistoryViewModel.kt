package com.formula.calculator.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.formula.calculator.FormulaApp
import com.formula.calculator.data.db.CalculationHistoryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FormulaApp).repository

    val history: StateFlow<List<CalculationHistoryEntity>> = repository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun deleteHistory(item: CalculationHistoryEntity) {
        viewModelScope.launch {
            repository.deleteHistory(item)
            _snackbarMessage.value = "已删除"
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearHistory()
            _snackbarMessage.value = "历史记录已清空"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
