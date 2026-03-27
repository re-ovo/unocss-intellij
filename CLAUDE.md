# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

这是一个为 IntelliJ 平台系列 IDE 提供 UnoCSS 支持的插件项目。该插件通过 Kotlin 编写的 IntelliJ 平台扩展与 JavaScript/TypeScript 编写的 UnoCSS 引擎进行通信，提供代码补全、语法高亮、文档提示、颜色预览等功能。

## 架构设计

### 混合语言架构

项目采用 Kotlin + JavaScript 的混合架构：

- **Kotlin 部分** (`src/main/kotlin`): 实现 IntelliJ 平台的所有扩展点（补全、高亮、文档、Line Marker 等）
- **JavaScript 部分** (`src/main/javascript`): 封装 UnoCSS 核心 API，通过 Node.js 进程提供 RPC 服务
- **通信机制**: Kotlin 通过 stdin/stdout 与 Node.js 进程进行 JSON-RPC 通信

### 核心服务

- **UnocssService**: 项目级服务，管理 UnoCSS 进程的生命周期
  - 检测项目是否安装了 unocss/\@unocss
  - 按需启动 Node.js 进程 (UnocssProcess)
  - 监听配置文件变化并自动重新加载
  - 提供统一的 API 供其他组件调用

- **UnocssProcess**: 封装与 JavaScript 服务的通信
  - 启动 Node.js 进程执行 `unojs/src/service.mjs`
  - 通过 Kotlin 协程将回调式 RPC 转换为挂起函数
  - 支持命令超时和取消

- **UnocssConfigManager**: 全局配置缓存
  - 缓存解析后的 UnoCSS 配置 (presets, transformers, theme)
  - 避免重复解析配置

### RPC 命令

所有 RPC 命令定义在 `rpc/RpcCommand.kt` 和 `src/main/javascript/src/service.ts`：

- `resolveConfig`: 解析 UnoCSS 配置文件
- `getComplete`: 获取代码补全建议
- `resolveCSS`: 解析 class 名称生成的 CSS
- `resolveCSSByOffset`: 根据光标位置解析 CSS
- `resolveAnnotations`: 解析文件中的所有 UnoCSS 标注位置
- `resolveBreakpoints`: 解析响应式断点配置
- `resolveToken`: 解析主题令牌

### 功能模块

按包组织的功能模块：

- `completion/`: 代码补全（XML/HTML、JavaScript/JSX、CSS 指令）
- `highlighting/`: 语法高亮和外部标注
- `documentation/`: 悬浮文档和快速文档
- `marker/line/`: 行内标记（颜色和图标预览）
- `marker/inlay/`: 内联提示（theme 函数颜色预览）
- `folding/`: 代码折叠
- `intent/`: 意图操作（CSS 转 UnoCSS）
- `references/`: 引用解析和跳转
- `inspection/`: 代码检查抑制器（抑制 attributify 警告）
- `action/`: 用户操作（预览选中样式）

## 构建和开发

### 环境要求

- JDK 21 (build.gradle.kts 配置为 JDK 21)
- Node.js 环境（必需，用于执行 UnoCSS）
- 插件目标平台：IntelliJ IDEA 2025.3 (sinceBuild: 252, untilBuild: 253.*)

### 首次构建

在首次运行前，必须先安装 JavaScript 依赖：

```bash
cd src/main/javascript
npm install  # 或使用 pnpm
```

**注意**: 不需要手动执行 `npm run build`，Gradle 构建时会通过 `processJavaScript` 任务自动执行。

### 常用命令

**开发和测试**:
```bash
./gradlew runIde                    # 启动 IDE 实例加载插件进行测试
./gradlew runLocalIde               # 使用本地 IDE 路径运行（需配置 local.properties）
./gradlew build                     # 构建项目
./gradlew buildPlugin               # 构建插件 ZIP 包
```

**验证和发布**:
```bash
./gradlew verifyPlugin              # 验证插件二进制兼容性
./gradlew verifyPluginStructure     # 验证插件结构和 plugin.xml
./gradlew signPlugin                # 签名插件（需要证书）
./gradlew publishPlugin             # 发布插件到 JetBrains Marketplace
```

**清理**:
```bash
./gradlew clean                     # 清理构建目录
```

### JavaScript 构建流程

`processJavaScript` 任务（在 Kotlin 编译前自动执行）：
1. 检测 `src/main/javascript/src`、配置文件等变化
2. 清理 `unojs/` 输出目录
3. 执行 `npm run build`（运行 rollup 打包）
4. 输出到 `unojs/` 目录

`prepareSandbox` 任务会将 `unojs/` 复制到插件沙盒中。

### 配置文件

UnoCSS 会自动检测以下配置文件（定义在 `Unocss.kt` 和 JavaScript service）：
- `uno.config.{js,ts}`
- `vite.config.{js,ts}`
- `svelte.config.{js,ts}`
- `iles.config.{js,ts}`
- `astro.config.{js,ts}`
- `nuxt.config.{js,ts}`

当这些文件或 `package.json`/锁文件变化时，插件会自动重新加载配置。

## 重要注意事项

### Node.js 依赖

- 插件依赖 Node.js 解释器，只支持 `NodeJsLocalInterpreter` 和 `WslNodeInterpreter`
- 启动时会检查 `unocss` 或 `@unocss` 包是否安装在项目的 node_modules 中
- 如果未检测到 Node.js 或未安装 UnoCSS，进程不会启动，功能将不可用

### 进程管理

- UnocssProcess 与项目服务生命周期绑定（通过 Disposer）
- 进程崩溃时会自动重启
- 所有 RPC 调用都有 1 秒超时（部分操作如配置更新有独立超时）
- 使用 Kotlin 协程处理异步通信，避免阻塞 IDE UI

### 依赖的插件模块

插件依赖以下 IntelliJ 平台模块（定义在 `plugin.xml`）：
- `com.intellij.modules.platform`
- `com.intellij.css`
- `JavaScript`

## 代码风格

- 使用 Kotlin 协程和挂起函数处理异步操作
- RPC 通信错误使用 `runCatching` 包装并打印堆栈
- 日志输出使用 `println` 或 `Unocss.Logger`
- 所有服务使用 `@Service` 注解并通过 `project.service<T>()` 获取
- PSI 操作在 read action 中执行
