package com.formula.calculator.ui.market

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.formula.calculator.domain.CommunityFormula
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    viewModel: MarketViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToCalculator: () -> Unit = {}
) {
    val formulas by viewModel.communityFormulas.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val editingFormula by viewModel.editingFormula.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配方市场") },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavoritesFilter() }) {
                        Icon(
                            if (showFavoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏筛选",
                            tint = if (showFavoritesOnly) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索配方名、作者、组分...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true
            )

            // 统计信息
            if (formulas.isNotEmpty()) {
                Text(
                    "共 ${formulas.size} 个社区配方",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (formulas.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Store,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (showFavoritesOnly) "暂无收藏的社区配方"
                            else if (searchQuery.isNotBlank()) "未找到匹配的配方"
                            else "暂无社区配方",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "从「配方」页导出分享 .formula 文件\n或打开他人分享的 .formula 文件即可导入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(formulas, key = { it.id }) { formula ->
                        CommunityFormulaCard(
                            formula = formula,
                            onUse = {
                                viewModel.incrementUsage(formula)
                                onNavigateToCalculator()
                            },
                            onEdit = { viewModel.showEditDialog(formula) },
                            onToggleFavorite = { viewModel.toggleFavorite(formula) },
                            onDelete = { viewModel.deleteFormula(formula) },
                            onShare = { /* handled by parent */ }
                        )
                    }
                }
            }
        }
    }

    // 编辑别名对话框
    editingFormula?.let { formula ->
        RenameDialog(
            currentName = formula.localName.ifBlank { formula.originalName },
            onDismiss = { viewModel.dismissEditDialog() },
            onConfirm = { newName -> viewModel.updateName(formula.id, newName) }
        )
    }
}

@Composable
fun CommunityFormulaCard(
    formula: CommunityFormula,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formula.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (formula.isFavorite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (formula.category.isNotEmpty()) {
                        Text(
                            formula.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (formula.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (formula.isFavorite) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // 作者和来源
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (formula.author.isNotEmpty()) {
                    Text(
                        "作者: ${formula.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "导入: ${dateFormat.format(Date(formula.importedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 摘要
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${formula.ingredients.size} 个组分 · 使用 ${formula.usageCount} 次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                // 使用按钮
                FilledTonalButton(
                    onClick = onUse,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("使用", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 展开显示组分
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                formula.ingredients.forEach { ing ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(ing.name, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        Text(
                            "${formatNum(ing.ratio)}${ing.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // 删除确认
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除配方") },
            text = { Text("确定要删除社区配方「${formula.displayName}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑别名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("本地别名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatNum(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value)
}
