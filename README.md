# 配方计算器 (Formula Calculator)

> **声明：本项目从需求分析、架构设计、代码编写到构建发布，全程由 AI（Qoder）独立完成。**
>
> 用户仅通过自然语言描述需求，AI 负责理解意图、设计方案、编写代码、调试错误、构建 APK 并交付可用产品。

---

## 项目简介

一款面向材料科学和化工领域的 **Android 配料计算器应用**，支持从 Excel 批量导入配方、按比例/克数计算组分用量、批量配料执行管理，以及通过本地文件分享配方。

典型应用场景：高炉渣粘度试验方案管理、陶瓷配方计算、水泥配料控制等。

## 核心功能

### 1. 配方管理
- 手动创建或 Excel 批量导入配方
- 支持多 Sheet 选择、自定义列映射（名称列 + 多组分列）
- 每行 Excel 数据自动映射为一组独立配方
- 配方分类、搜索、收藏

### 2. 智能计算
- 输入目标量（如 200g），自动归一化计算各组分的实际用量
- 支持组分锁定（锁定组分不参与比例调整）
- 实时显示占比与实际克数

### 3. 批量配料执行
- 导入的配方自动成组（batchGroup），整组统一操作
- 一键设置全组目标量，所有配方立即显示实际克数
- 逐条标记完成/未完成，进度可视化
- 快速跳转列表，支持上百条配方的高效操作

### 4. 配方市场（本地分享）
- 将配方导出为 `.formula` 文件
- 通过微信/QQ/蓝牙等分享给他人
- 接收方点击文件即可预览并一键导入
- 社区配方管理：搜索、收藏、编辑别名

### 5. 计算历史
- 自动记录每次计算结果
- 支持历史查看与回溯

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM (ViewModel + Repository) |
| 数据库 | Room Database (SQLite) |
| Excel 解析 | Apache POI |
| 响应式数据 | Kotlin Flow / StateFlow |
| 构建工具 | Gradle 8.14 + AGP 8.5.2 |
| 最低 API | Android 8.0 (API 26) |
| 目标 API | Android 16 (API 36) |

## 项目结构

```
app/src/main/java/com/formula/calculator/
├── data/
│   ├── db/          # Room 实体与 DAO（FormulaEntity, IngredientEntity, ...）
│   └── repository/  # 数据仓库层（FormulaRepository）
├── domain/          # 领域模型（Formula, Ingredient, CalculationResult, ...）
├── parser/          # 解析器（ExcelParser, FormulaPackage）
├── ui/
│   ├── recipes/     # 配方列表 + 导入向导
│   ├── calculator/  # 单配方计算页
│   ├── batch/       # 批量配料执行页
│   ├── market/      # 配方市场 + 导出分享
│   └── history/     # 计算历史
└── util/            # 工具类
```

## 版本历史

| 版本 | 更新内容 |
|------|---------|
| v1.0.0 | 基础配方管理 + 计算功能 |
| v1.1.0 | 交互式 Excel 导入向导（多 Sheet + 列映射） |
| v1.2.0 | 批量配料执行界面（逐条标记完成） |
| v1.3.0 | 配方市场（.formula 文件分享 + 社区管理） |
| v1.4.0 | 导入流程简化（选列 + 预览合并为一步） |
| v1.5.0 | 配料分组 + 一键计算 + 实际克数显示 |
| v1.5.1 | 配料页面重构：手风琴展开式交互，支持单独设置每组配料的目标量 |

## 构建方式

```bash
# 克隆项目
git clone https://github.com/zsh02/FormulaCalculator.git
cd FormulaCalculator

# 使用 Gradle 构建 debug APK
gradlew assembleDebug

# APK 输出路径
# app/build/outputs/apk/debug/app-debug.apk
```

## 环境要求

- JDK 17+
- Android SDK (compileSdk 36)
- Gradle 8.14（项目自带 wrapper）

## 开源协议

本项目仅供学习交流。
