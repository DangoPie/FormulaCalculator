package com.formula.calculator.ui.calculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.formula.calculator.domain.CalculationResult
import com.formula.calculator.domain.Ingredient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val formulas by viewModel.formulas.collectAsState()
    val selectedFormulaId by viewModel.selectedFormulaId.collectAsState()
    val ingredients by viewModel.ingredients.collectAsState()
    val targetAmount by viewModel.targetAmount.collectAsState()
    val targetUnit by viewModel.targetUnit.collectAsState()
    val results by viewModel.results.collectAsState()
    val hasCalculated by viewModel.hasCalculated.collectAsState()
    val ratioSum by viewModel.ratioSum.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(hasCalculated) {
        if (hasCalculated && results.isNotEmpty()) {
            listState.animateScrollToItem(4)
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("配料计算") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 选择配方
            item {
                FormulaSelector(
                    formulas = formulas,
                    selectedId = selectedFormulaId,
                    onSelect = { viewModel.selectFormula(it) }
                )
            }

            // 2. 组分列表（带锁定和比例调整）
            if (ingredients.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "组分详情",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                // 比例总和指示
                                val sumColor = when {
                                    kotlin.math.abs(ratioSum - 100) < 0.01 -> MaterialTheme.colorScheme.primary
                                    ratioSum <= 0 -> MaterialTheme.colorScheme.outline
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                                Text(
                                    "比例合计: ${formatNumber(ratioSum)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = sumColor
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            ingredients.forEachIndexed { index, ingredient ->
                                IngredientRow(
                                    ingredient = ingredient,
                                    index = index,
                                    onToggleLock = { viewModel.toggleLock(ingredient.id) },
                                    onRatioChange = { newRatio ->
                                        viewModel.updateRatio(ingredient.id, newRatio)
                                    }
                                )
                                if (index < ingredients.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 3. 目标总量输入
            if (ingredients.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "目标总量",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = targetAmount,
                                    onValueChange = { viewModel.setTargetAmount(it) },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("输入目标量") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true
                                )

                                // 单位选择
                                var showUnitMenu by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = showUnitMenu,
                                    onExpandedChange = { showUnitMenu = !showUnitMenu }
                                ) {
                                    OutlinedTextField(
                                        value = targetUnit,
                                        onValueChange = {},
                                        modifier = Modifier
                                            .width(80.dp)
                                            .menuAnchor(),
                                        readOnly = true,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showUnitMenu)
                                        }
                                    )
                                    ExposedDropdownMenu(
                                        expanded = showUnitMenu,
                                        onDismissRequest = { showUnitMenu = false }
                                    ) {
                                        listOf("g", "kg", "mg", "t", "lb", "oz").forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit) },
                                                onClick = {
                                                    viewModel.setTargetUnit(unit)
                                                    showUnitMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 计算按钮
            if (ingredients.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.calculate() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Calculate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("计算配料")
                        }
                        if (hasCalculated) {
                            OutlinedButton(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.weight(0.5f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重置")
                            }
                        }
                    }
                }
            }

            // 5. 计算结果
            if (hasCalculated && results.isNotEmpty()) {
                item {
                    CalculationResultsCard(
                        results = results,
                        targetAmount = targetAmount.toDoubleOrNull() ?: 0.0,
                        targetUnit = targetUnit,
                        onSave = { viewModel.saveToHistory() }
                    )
                }
            }

            // 空状态
            if (ingredients.isEmpty() && selectedFormulaId == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Calculate,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "请先选择一个配方",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                "在「配方」页面创建或导入配方可开始计算",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormulaSelector(
    formulas: List<com.formula.calculator.domain.Formula>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFormula = formulas.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedFormula?.name ?: "选择配方",
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            label = { Text("配方") },
            leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (formulas.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无配方，请先创建", color = MaterialTheme.colorScheme.outline) },
                    onClick = { expanded = false }
                )
            } else {
                formulas.forEach { formula ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(formula.name, fontWeight = FontWeight.Medium)
                                if (formula.category.isNotEmpty()) {
                                    Text(
                                        formula.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(formula.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun IngredientRow(
    ingredient: Ingredient,
    index: Int,
    onToggleLock: () -> Unit,
    onRatioChange: (Double) -> Unit
) {
    var editingRatio by remember { mutableStateOf(false) }
    var ratioText by remember { mutableStateOf(formatNumber(ingredient.ratio)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 锁定按钮
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (ingredient.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = if (ingredient.isLocked) "已锁定" else "未锁定",
                tint = if (ingredient.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }

        // 名称
        Text(
            text = ingredient.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (ingredient.isLocked) FontWeight.Bold else FontWeight.Normal
        )

        // 比例
        if (editingRatio) {
            OutlinedTextField(
                value = ratioText,
                onValueChange = { ratioText = it },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
        } else {
            TextButton(
                onClick = { editingRatio = true },
                modifier = Modifier.width(80.dp)
            ) {
                Text(
                    text = "${formatNumber(ingredient.ratio)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 比例条
        val barWidth = 60.dp
        val ratio = ingredient.ratio.coerceIn(0.0, 100.0)
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((barWidth * (ratio / 100.0).toFloat()).coerceAtLeast(0.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (ingredient.isLocked) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}

@Composable
fun CalculationResultsCard(
    results: List<CalculationResult>,
    targetAmount: Double,
    targetUnit: String,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "计算结果",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "目标: ${formatNumber(targetAmount)} $targetUnit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 结果表头
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("组分", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1.5f))
                Text("占比", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("用量", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 结果列表
            results.forEachIndexed { index, result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        result.ingredientName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        "${formatNumber(result.adjustedRatio)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "${formatNumber(result.amount)} ${result.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 合计
            val totalAmount = results.sumOf { it.amount }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("合计", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${formatNumber(totalAmount)} $targetUnit",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存到历史记录")
            }
        }
    }
}

private fun formatNumber(value: Double): String {
    return when {
        value == 0.0 -> "0"
        value == value.toLong().toDouble() -> value.toLong().toString()
        value < 0.01 -> String.format("%.4f", value)
        value < 1 -> String.format("%.3f", value)
        value < 100 -> String.format("%.2f", value)
        else -> String.format("%.1f", value)
    }
}
