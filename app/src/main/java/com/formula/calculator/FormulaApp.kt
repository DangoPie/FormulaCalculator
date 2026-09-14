package com.formula.calculator

import android.app.Application
import com.formula.calculator.data.db.AppDatabase
import com.formula.calculator.data.repository.FormulaRepository

class FormulaApp : Application() {

    lateinit var database: AppDatabase
    lateinit var repository: FormulaRepository

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = FormulaRepository(
            database.formulaDao(),
            database.historyDao(),
            database.communityFormulaDao()
        )
    }
}
