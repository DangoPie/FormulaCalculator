package com.formula.calculator.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.formula.calculator.ui.batch.BatchScreen
import com.formula.calculator.ui.calculator.CalculatorScreen
import com.formula.calculator.ui.history.HistoryScreen
import com.formula.calculator.ui.market.FormulaPreviewScreen
import com.formula.calculator.ui.market.MarketScreen
import com.formula.calculator.ui.recipes.RecipesScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Recipes : Screen("recipes", "配方", Icons.Filled.MenuBook)
    data object Calculator : Screen("calculator", "计算", Icons.Filled.Calculate)
    data object Batch : Screen("batch", "配料", Icons.Filled.Assignment)
    data object Market : Screen("market", "市场", Icons.Filled.Store)
    data object History : Screen("history", "历史", Icons.Filled.History)
}

val screens = listOf(Screen.Recipes, Screen.Calculator, Screen.Batch, Screen.Market, Screen.History)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pendingFormulaUri: Uri? = null,
    pendingFormulaFileName: String = "",
    onConsumePendingUri: () -> Unit = {}
) {
    val navController = rememberNavController()

    // 处理外部打开的 .formula 文件
    LaunchedEffect(pendingFormulaUri) {
        if (pendingFormulaUri != null) {
            navController.navigate("market_preview")
            // 不立即清除，等 FormulaPreviewScreen 取走
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                // 隐藏底部栏在预览页
                val showBottomBar = currentDestination?.route != "market_preview"

                if (showBottomBar) {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Recipes.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Recipes.route) { RecipesScreen() }
            composable(Screen.Calculator.route) { CalculatorScreen() }
            composable(Screen.Batch.route) { BatchScreen() }
            composable(Screen.Market.route) {
                MarketScreen(
                    onNavigateToCalculator = {
                        navController.navigate(Screen.Calculator.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.History.route) { HistoryScreen() }
            composable("market_preview") {
                FormulaPreviewScreen(
                    externalUri = pendingFormulaUri,
                    externalFileName = pendingFormulaFileName,
                    onImportSuccess = {
                        onConsumePendingUri()
                        navController.navigate(Screen.Market.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCancel = {
                        onConsumePendingUri()
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
