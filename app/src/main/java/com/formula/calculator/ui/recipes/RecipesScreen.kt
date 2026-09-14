package com.formula.calculator.ui.recipes

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.formula.calculator.domain.Formula
import com.formula.calculator.domain.Ingredient
import com.formula.calculator.parser.ExcelParser
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    importViewModel: ImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val formulas by viewModel.filteredFormulas.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val editingFormula by viewModel.editingFormula.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    // Import wizard state
    val importStep by importViewModel.step.collectAsState()
    val importSuccess by importViewModel.importSuccess.collectAsState()
    val importSnackbar by importViewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && nameIdx >= 0) c.getString(nameIdx) else "import.xlsx"
                } ?: "import.xlsx"
            } catch (e: Exception) { "import.xlsx" }
            importViewModel.parseFile(it, name)
        }
    }

    // Snackbar
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(importSnackbar) {
        importSnackbar?.let {
            snackbarHostState.showSnackbar(it)
            importViewModel.clearSnackbar()
        }
    }

    // 导入成功后刷新列表
    LaunchedEffect(importSuccess) {
        if (importSuccess) {
            importViewModel.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配方管理") },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = "导入文件")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog() }) {
                Icon(Icons.Filled.Add, contentDescription = "新建配方")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索配方...") },
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

            // 分类筛选
            if (categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.setSelectedCategory(null) },
                        label = { Text("全部") }
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.setSelectedCategory(cat) },
                            label = { Text(cat) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 配方列表
            if (formulas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无配方",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "点击右下角 + 新建，或从 Excel 导入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
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
                        FormulaCard(
                            formula = formula,
                            onEdit = { viewModel.showEditDialog(formula) },
                            onDelete = { viewModel.deleteFormula(formula) }
                        )
                    }
                }
            }
        }
    }

    // ============ 导入向导对话框 ============
    if (importStep != ImportViewModel.ImportStep.IDLE) {
        ImportWizardDialog(importViewModel = importViewModel)
    }

    // 新建/编辑对话框
    if (showEditDialog) {
        FormulaEditDialog(
            editingFormula = editingFormula,
            onSave = { name, desc, cat, ingredients ->
                viewModel.saveFormula(name, desc, cat, ingredients)
            },
            onDismiss = { viewModel.dismissEditDialog() }
        )
    }
}

// ============ 导入向导对话框（简化版） ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardDialog(
    importViewModel: ImportViewModel
) {
    val step by importViewModel.step.collectAsState()
    val rawResult by importViewModel.rawResult.collectAsState()
    val currentSheet by importViewModel.currentSheet.collectAsState()
    val selectedSheetIndex by importViewModel.selectedSheetIndex.collectAsState()
    val nameColumnIndex by importViewModel.nameColumnIndex.collectAsState()
    val valueColumnIndices by importViewModel.valueColumnIndices.collectAsState()
    val excludedRows by importViewModel.excludedRows.collectAsState()
    val previewFormulas by importViewModel.previewFormulas.collectAsState()
    val formulaCategory by importViewModel.formulaCategory.collectAsState()
    val batchGroupName by importViewModel.batchGroupName.collectAsState()
    val importedCount by importViewModel.importedCount.collectAsState()

    AlertDialog(
        onDismissRequest = { importViewModel.cancel() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (step) {
                        ImportViewModel.ImportStep.SELECT_SHEET -> "选择工作表"
                        ImportViewModel.ImportStep.MAP_COLUMNS -> "选择列与预览"
                        ImportViewModel.ImportStep.DONE -> "导入完成"
                        else -> "导入"
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                if (step == ImportViewModel.ImportStep.MAP_COLUMNS) {
                    TextButton(onClick = { importViewModel.goBack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("返回")
                    }
                }
            }
        },
        text = {
            when (step) {
                ImportViewModel.ImportStep.SELECT_SHEET -> {
                    SheetSelectionContent(
                        sheets = rawResult?.sheets ?: emptyList(),
                        selectedIndex = selectedSheetIndex,
                        onSelect = { importViewModel.selectSheet(it) }
                    )
                }

                ImportViewModel.ImportStep.MAP_COLUMNS -> {
                    ColumnMappingAndPreviewContent(
                        sheet = currentSheet,
                        nameColumnIndex = nameColumnIndex,
                        valueColumnIndices = valueColumnIndices,
                        excludedRows = excludedRows,
                        previewData = previewFormulas,
                        formulaCategory = formulaCategory,
                        batchGroupName = batchGroupName,
                        onNameColumnChange = { importViewModel.setNameColumn(it) },
                        onToggleValueColumn = { importViewModel.toggleValueColumn(it) },
                        onToggleRow = { importViewModel.toggleRowExcluded(it) },
                        onCategoryChange = { importViewModel.setFormulaCategory(it) },
                        onBatchGroupNameChange = { importViewModel.setBatchGroupName(it) }
                    )
                }

                ImportViewModel.ImportStep.DONE -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null,
                            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("导入成功！", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("已导入 $importedCount 组配方，可在「配料」页逐条查看",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {}
            }
        },
        confirmButton = {
            when (step) {
                ImportViewModel.ImportStep.MAP_COLUMNS -> {
                    TextButton(
                        onClick = { importViewModel.confirmImport() },
                        enabled = nameColumnIndex >= 0 && valueColumnIndices.isNotEmpty() && previewFormulas.isNotEmpty()
                    ) { Text("确认导入 (${previewFormulas.size} 组)") }
                }
                ImportViewModel.ImportStep.DONE -> {
                    TextButton(onClick = { importViewModel.cancel() }) { Text("完成") }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (step != ImportViewModel.ImportStep.DONE) {
                TextButton(onClick = { importViewModel.cancel() }) { Text("取消") }
            }
        }
    )
}

// ============ Step 1: Sheet 选择 ============

@Composable
fun SheetSelectionContent(
    sheets: List<ExcelParser.SheetData>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("文件包含 ${sheets.size} 个工作表，请选择要导入的：")
        sheets.forEachIndexed { index, sheet ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (index == selectedIndex)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.TableChart,
                        contentDescription = null,
                        tint = if (index == selectedIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(sheet.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${sheet.rows.size} 行数据, ${sheet.headers.size} 列",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// ============ 选列 + 预览（合并步骤） ============

@Composable
fun ColumnMappingAndPreviewContent(
    sheet: ExcelParser.SheetData?,
    nameColumnIndex: Int,
    valueColumnIndices: List<Int>,
    excludedRows: Set<Int>,
    previewData: List<Pair<String, List<Pair<String, Double>>>>,
    formulaCategory: String,
    batchGroupName: String,
    onNameColumnChange: (Int) -> Unit,
    onToggleValueColumn: (Int) -> Unit,
    onToggleRow: (Int) -> Unit,
    onCategoryChange: (String) -> Unit,
    onBatchGroupNameChange: (String) -> Unit
) {
    if (sheet == null) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Sheet 信息
        Text("工作表: ${sheet.name}（${sheet.rows.size} 行 × ${sheet.headers.size} 列）",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        // 名称列选择
        Text("名称列（每行的标识）：", style = MaterialTheme.typography.labelSmall)
        WrapChipRow(
            items = sheet.headers.mapIndexed { idx, h -> Pair(idx, h.ifBlank { "列${idx + 1}" }) },
            selectedItems = listOf(nameColumnIndex),
            onItemClick = { onNameColumnChange(it) },
            singleSelect = true
        )

        HorizontalDivider()

        // 组分列选择
        Text("组分列（点击选择/取消，每行=一组配方）：", style = MaterialTheme.typography.labelSmall)
        WrapChipRow(
            items = sheet.headers.mapIndexed { idx, h -> Pair(idx, h.ifBlank { "列${idx + 1}" }) },
            selectedItems = valueColumnIndices,
            onItemClick = { onToggleValueColumn(it) },
            singleSelect = false,
            excludeItem = nameColumnIndex
        )

        HorizontalDivider()

        // 分类
        OutlinedTextField(
            value = formulaCategory,
            onValueChange = onCategoryChange,
            label = { Text("分类（可选）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 批次组名
        OutlinedTextField(
            value = batchGroupName,
            onValueChange = onBatchGroupNameChange,
            label = { Text("批次组名（可选，留空自动生成）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // 预览统计
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("将导入 ${previewData.size} 组配方",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("每组 ${valueColumnIndices.size} 个组分",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }

        // 数据预览列表
        Text("点击行可排除/恢复：", style = MaterialTheme.typography.labelSmall)
        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
            itemsIndexed(sheet.rows) { rowIdx, row ->
                val name = row.getOrElse(nameColumnIndex) { "" }.trim()
                val isExcluded = rowIdx in excludedRows
                val values = valueColumnIndices.mapNotNull { colIdx ->
                    row.getOrElse(colIdx) { "" }.trim().replace("%", "").toDoubleOrNull()
                }
                val isValid = name.isNotBlank() && values.isNotEmpty()
                if (isValid || isExcluded) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isExcluded) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { onToggleRow(rowIdx) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isExcluded) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(name, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        // 显示前几个组分值
                        val preview = valueColumnIndices.take(3).mapNotNull { colIdx ->
                            val v = row.getOrElse(colIdx) { "" }.trim().replace("%", "").toDoubleOrNull()
                            if (v != null && v > 0) "${formatNum2(v)}" else null
                        }.joinToString(", ")
                        Text(preview, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        if (valueColumnIndices.size > 3) {
                            Text(" +${valueColumnIndices.size - 3}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

private fun formatNum2(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
}

@Composable
fun WrapChipRow(
    items: List<Pair<Int, String>>,
    selectedItems: List<Int>,
    onItemClick: (Int) -> Unit,
    singleSelect: Boolean = false,
    excludeItem: Int = -1
) {
    val filteredItems = items.filter { it.first != excludeItem }
    // Simple flow layout using Row with wrap
    var currentRow = mutableListOf<Pair<Int, String>>()
    val rows = mutableListOf<List<Pair<Int, String>>>()
    var currentWidth = 0

    filteredItems.forEach { item ->
        val estimatedWidth = (item.second.length * 12 + 32).coerceAtLeast(60)
        if (currentWidth + estimatedWidth > 300 && currentRow.isNotEmpty()) {
            rows.add(currentRow.toList())
            currentRow.clear()
            currentWidth = 0
        }
        currentRow.add(item)
        currentWidth += estimatedWidth
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow.toList())

    rows.forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            row.forEach { (idx, label) ->
                val isSelected = idx in selectedItems
                FilterChip(
                    selected = isSelected,
                    onClick = { onItemClick(idx) },
                    label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}

// ============ 配方卡片 ============

@Composable
fun FormulaCard(
    formula: Formula,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val ingredients = remember(formula.id) {
        mutableStateListOf<Ingredient>()
    }

    LaunchedEffect(formula.id) {
        val db = com.formula.calculator.data.db.AppDatabase.getInstance(context)
        db.formulaDao().getIngredientsByFormula(formula.id).collect { list ->
            ingredients.clear()
            ingredients.addAll(list.map { entity ->
                Ingredient(
                    id = entity.id,
                    formulaId = entity.formulaId,
                    name = entity.name,
                    ratio = entity.ratio,
                    unit = entity.unit,
                    sortOrder = entity.sortOrder,
                    note = entity.note
                )
            })
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formula.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (formula.category.isNotEmpty()) {
                        Text(
                            text = formula.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row {
                    IconButton(onClick = {
                        com.formula.calculator.ui.market.FormulaExporter.shareFormula(
                            context, formula, ingredients.toList()
                        )
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (formula.description.isNotEmpty()) {
                Text(
                    text = formula.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "共 ${ingredients.size} 个组分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            if (expanded && ingredients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                ingredients.forEach { ingredient ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ingredient.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${formatNumber(ingredient.ratio)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                val total = ingredients.sumOf { it.ratio }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("合计", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatNumber(total)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (kotlin.math.abs(total - 100) < 0.01)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "创建于 ${dateFormat.format(Date(formula.createdAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除配方「${formula.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

// ============ 编辑对话框 ============

@Composable
fun FormulaEditDialog(
    editingFormula: Formula?,
    onSave: (name: String, description: String, category: String, ingredients: List<Ingredient>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(editingFormula?.name ?: "") }
    var description by remember { mutableStateOf(editingFormula?.description ?: "") }
    var category by remember { mutableStateOf(editingFormula?.category ?: "") }
    val ingredients = remember {
        mutableStateListOf<Ingredient>().apply {
            editingFormula?.ingredients?.let { addAll(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingFormula != null) "编辑配方" else "新建配方") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配方名称 *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()
                Text("组分列表", style = MaterialTheme.typography.labelLarge)

                ingredients.forEachIndexed { index, ingredient ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ingredient.name,
                            onValueChange = { newName ->
                                ingredients[index] = ingredient.copy(name = newName)
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("名称") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = if (ingredient.ratio == 0.0) "" else formatNumber(ingredient.ratio),
                            onValueChange = { newRatio ->
                                val ratio = newRatio.toDoubleOrNull() ?: 0.0
                                ingredients[index] = ingredient.copy(ratio = ratio)
                            },
                            modifier = Modifier.width(80.dp),
                            placeholder = { Text("比例") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        Text("%", style = MaterialTheme.typography.bodySmall)
                        IconButton(
                            onClick = { ingredients.removeAt(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "删除",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        ingredients.add(Ingredient(name = "", ratio = 0.0, sortOrder = ingredients.size))
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加组分")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val validIngredients = ingredients.filter { it.name.isNotBlank() && it.ratio > 0 }
                    onSave(name, description, category, validIngredients)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatNumber(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.2f", value)
    }
}
