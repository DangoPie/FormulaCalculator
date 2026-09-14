package com.formula.calculator.ui.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.formula.calculator.domain.Formula

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScreen(
    viewModel: BatchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val groups by viewModel.batchGroups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val formulas by viewModel.groupFormulas.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val ingredients by viewModel.currentIngredients.collectAsState()
    val results by viewModel.currentResults.collectAsState()
    val targetAmount by viewModel.groupTargetAmount.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val currentFormula = formulas.getOrNull(currentIndex)
    val total = formulas.size
    val completedCount = formulas.count { it.isCompleted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配料执行") },
                actions = {
                    if (total > 0) {
                        Text(
                            "$completedCount/$total",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        var showReset by remember { mutableStateOf(false) }
                        IconButton(onClick = { showReset = true }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "重置")
                        }
                        if (showReset) {
                            AlertDialog(
                                onDismissRequest = { showReset = false },
                                title = { Text("重置批次") },
                                text = { Text("确定要清除所有配方的完成标记和目标量吗？") },
                                confirmButton = {
                                    TextButton(onClick = { showReset = false; viewModel.clearBatchStatus() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("重置") }
                                },
                                dismissButton = { TextButton(onClick = { showReset = false }) { Text("取消") } }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (groups.isEmpty()) {
            // 空状态
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Assignment, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("暂无批次数据", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline)
                    Text("在「配方」页导入配方后，可在此逐条执行",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 组选择器
                if (groups.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups) { group ->
                            FilterChip(
                                selected = group == selectedGroup,
                                onClick = { viewModel.selectGroup(group) },
                                label = { Text(group, maxLines = 1) }
                            )
                        }
                    }
                }

                // 目标量设置
                TargetAmountBar(
                    targetAmount = targetAmount,
                    formulaCount = total,
                    onSetAmount = { viewModel.setGroupTargetAmount(it) },
                    onCompleteAll = { viewModel.completeGroup() },
                    onUncompleteAll = { viewModel.uncompleteGroup() },
                    allCompleted = completedCount == total && total > 0
                )

                // 进度条
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { completedCount.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                    )
                }

                // 当前配方内容
                if (currentFormula != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 配方标题
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "#${currentIndex + 1} ${currentFormula.name}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    if (currentFormula.category.isNotEmpty()) {
                                        Text(currentFormula.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Icon(
                                    if (currentFormula.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = if (currentFormula.isCompleted) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // 组分 + 计算结果
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (targetAmount > 0) {
                                        Text("计算结果（目标 ${BatchViewModel.formatNum(targetAmount)}g）",
                                            style = MaterialTheme.typography.titleSmall)
                                    } else {
                                        Text("组分清单（未设置目标量）",
                                            style = MaterialTheme.typography.titleSmall)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    ingredients.forEachIndexed { idx, ingredient ->
                                        val result = results.getOrNull(idx)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                ingredient.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (result != null) {
                                                // 显示实际克数
                                                Text(
                                                    "${BatchViewModel.formatNum(result.amount)}g",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "(${BatchViewModel.formatNum(result.adjustedRatio * 100)}%)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            } else {
                                                // 只显示占比
                                                Text(
                                                    "${BatchViewModel.formatNum(ingredient.ratio)}%",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                        if (idx < ingredients.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                        }
                                    }

                                    // 合计
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("合计", style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold)
                                        if (results.isNotEmpty()) {
                                            val totalAmount = results.sumOf { it.amount }
                                            Text("${BatchViewModel.formatNum(totalAmount)}g",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            val ratioTotal = ingredients.sumOf { it.ratio }
                                            Text("${BatchViewModel.formatNum(ratioTotal)}%",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (kotlin.math.abs(ratioTotal - 100) < 0.5)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }
                        }

                        // 操作按钮
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.toggleCompleted() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (currentFormula.isCompleted)
                                            MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        if (currentFormula.isCompleted) Icons.Filled.Undo else Icons.Filled.Check,
                                        contentDescription = null, modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (currentFormula.isCompleted) "标记未完成" else "标记已完成")
                                }
                            }
                        }

                        // 导航按钮
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.goToPrevious() },
                                    enabled = currentIndex > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.NavigateBefore, contentDescription = null)
                                    Text("上一条")
                                }
                                Text(
                                    "${currentIndex + 1} / $total",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Button(
                                    onClick = { viewModel.goToNext() },
                                    enabled = currentIndex < total - 1,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("下一条")
                                    Icon(Icons.Filled.NavigateNext, contentDescription = null)
                                }
                            }
                        }

                        // 快速跳转列表
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("快速跳转：", style = MaterialTheme.typography.labelMedium)
                        }

                        itemsIndexed(formulas) { idx, formula ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            idx == currentIndex -> MaterialTheme.colorScheme.primaryContainer
                                            formula.isCompleted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    if (formula.isCompleted) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        "#${idx + 1} ${formula.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (idx == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (idx != currentIndex) {
                                    TextButton(onClick = { viewModel.goToIndex(idx) }) { Text("查看") }
                                } else {
                                    Text("当前", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TargetAmountBar(
    targetAmount: Double,
    formulaCount: Int,
    onSetAmount: (Double) -> Unit,
    onCompleteAll: () -> Unit,
    onUncompleteAll: () -> Unit,
    allCompleted: Boolean
) {
    var amountText by remember(targetAmount) {
        mutableStateOf(if (targetAmount > 0) BatchViewModel.formatNum(targetAmount) else "")
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (targetAmount > 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("目标量（全组统一）", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f))
                Text("$formulaCount 个配方", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("克数") },
                    placeholder = { Text("如 200") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Button(
                    onClick = {
                        val amount = amountText.replace(",", "").toDoubleOrNull()
                        if (amount != null && amount > 0) onSetAmount(amount)
                    },
                    enabled = amountText.replace(",", "").toDoubleOrNull()?.let { it > 0 } == true
                ) {
                    Text("应用")
                }
            }
            if (targetAmount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = if (allCompleted) onUncompleteAll else onCompleteAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (allCompleted) Icons.Filled.Undo else Icons.Filled.DoneAll,
                            contentDescription = null, modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (allCompleted) "全部撤销完成" else "全组标记完成")
                    }
                }
            }
        }
    }
}
