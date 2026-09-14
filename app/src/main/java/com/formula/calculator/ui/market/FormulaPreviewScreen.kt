package com.formula.calculator.ui.market

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.formula.calculator.parser.FormulaPackage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaPreviewScreen(
    viewModel: FormulaPreviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    externalUri: Uri? = null,
    externalFileName: String = "",
    onImportSuccess: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    // 加载外部传入的 .formula 文件
    LaunchedEffect(externalUri) {
        if (externalUri != null) {
            viewModel.loadFromUri(externalUri, externalFileName)
        }
    }
    val pkg by viewModel.parsedPackage.collectAsState()
    val localName by viewModel.localName.collectAsState()
    val sourceFile by viewModel.sourceFile.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val importSuccess by viewModel.importSuccess.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(importSuccess) {
        if (importSuccess) {
            onImportSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配方预览") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (pkg == null) {
            // 加载中或解析失败
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在解析配方文件...")
                }
            }
        } else {
            val formula = pkg!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 配方信息卡片
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 名称
                        Text(
                            formula.formulaName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (formula.formulaCategory.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                formula.formulaCategory,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (formula.formulaDescription.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                formula.formulaDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // 元数据
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (formula.author.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Person, contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(formula.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            if (formula.exportedAt > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                            .format(java.util.Date(formula.exportedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        if (sourceFile.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "来源: $sourceFile",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // 组分列表
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "组分清单 (${formula.ingredients.size} 项)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        formula.ingredients.forEachIndexed { idx, ing ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${idx + 1}. ${ing.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${formatValue(ing.ratio)}${ing.unit}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (idx < formula.ingredients.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }

                        val total = formula.ingredients.sumOf { it.ratio }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("合计", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold)
                            Text(
                                "${formatValue(total)}%",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (kotlin.math.abs(total - 100) < 0.5)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // 本地别名
                OutlinedTextField(
                    value = localName,
                    onValueChange = { viewModel.setLocalName(it) },
                    label = { Text("本地别名（可选）") },
                    placeholder = { Text("留空则使用原始名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 导入按钮
                Button(
                    onClick = { viewModel.importFormula() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入到本地", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun formatValue(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value)
}
