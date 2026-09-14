package com.formula.calculator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.formula.calculator.ui.MainScreen
import com.formula.calculator.ui.theme.FormulaCalculatorTheme

class MainActivity : ComponentActivity() {

    private var pendingUri: Uri? by mutableStateOf(null)
    private var pendingFileName: String by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 处理启动时的 Intent
        handleIntent(intent)

        setContent {
            FormulaCalculatorTheme {
                MainScreen(
                    pendingFormulaUri = pendingUri,
                    pendingFormulaFileName = pendingFileName,
                    onConsumePendingUri = {
                        pendingUri = null
                        pendingFileName = ""
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val fileName = getFileNameFromUri(uri) ?: "unknown"
                    if (fileName.endsWith(".formula", ignoreCase = true) ||
                        intent.type == "application/json") {
                        pendingUri = uri
                        pendingFileName = fileName
                    }
                }
            }
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    val fileName = getFileNameFromUri(uri) ?: "unknown"
                    if (fileName.endsWith(".formula", ignoreCase = true)) {
                        pendingUri = uri
                        pendingFileName = fileName
                    }
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && nameIdx >= 0) c.getString(nameIdx) else null
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
}
