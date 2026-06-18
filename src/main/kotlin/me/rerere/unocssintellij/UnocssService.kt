package me.rerere.unocssintellij

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.javascript.nodejs.packageJson.NodeInstalledPackageFinder
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.rpc.GetCompleteCommandData
import me.rerere.unocssintellij.rpc.ResolveAnnotationsCommandData
import me.rerere.unocssintellij.rpc.ResolveAnnotationsResult
import me.rerere.unocssintellij.rpc.ResolveBreakpointsResult
import me.rerere.unocssintellij.rpc.ResolveCSSByOffsetCommandData
import me.rerere.unocssintellij.rpc.ResolveCSSCommandData
import me.rerere.unocssintellij.rpc.ResolveCSSResult
import me.rerere.unocssintellij.rpc.ResolveConfigResult
import me.rerere.unocssintellij.rpc.ResolveTokenResult
import me.rerere.unocssintellij.rpc.ResolveTokenResultCommandData
import me.rerere.unocssintellij.rpc.RpcAction
import me.rerere.unocssintellij.rpc.SuggestionItem
import me.rerere.unocssintellij.rpc.SuggestionItemList
import me.rerere.unocssintellij.rpc.UpdateSettingsCommandData
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.status.UnocssStatusBarFactory
import me.rerere.unocssintellij.util.toLocalVirtualFile

private val SENSITIVE_FILES = listOf(
    "package.json",
    "package-lock.json",
    "yarn.lock",
    "pnpm-lock.yaml",
    "pnpm-lock.json",
    "vite.config",
    "svelte.config",
    "iles.config",
    "astro.config",
    "nuxt.config",
    "uno.config",
)

// 所有 RPC 调用的默认超时（毫秒）
private const val RPC_TIMEOUT = 1000L

@Service(Service.Level.PROJECT)
class UnocssService(private val project: Project, val scope: CoroutineScope) : Disposable {

    private var unocssProcess: UnocssProcess? = null

    // 串行化进程初始化，避免多个文件同时打开时并发创建多个进程
    private val processMutex = Mutex()

    private val nodeEnvInstalled by lazy {
        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
        interpreter != null && (interpreter is NodeJsLocalInterpreter || interpreter is WslNodeInterpreter)
    }

    init {
        // 监听敏感配置文件（package.json / *.config 等）的保存，变更后重载配置
        VirtualFileManager.getInstance().addAsyncFileListener({ events ->
            val changed = events.any { event ->
                val file = event.file ?: return@any false
                file.isValid && event.requestor != this && event.isFromSave &&
                    SENSITIVE_FILES.any { file.name.contains(it) }
            }
            if (!changed) return@addAsyncFileListener null

            object : ChangeApplier {
                override fun afterVfsChange() = updateConfigIfRunning()
            }
        }, this)
    }

    override fun dispose() {
        scope.cancel()
    }

    /**
     * 获取（必要时创建/重启）UnoCSS 进程。
     *
     * 命名为 ensure 是因为它带有创建副作用：进程不存在时按需启动，崩溃时自动重启。
     * 通过 [processMutex] 串行化创建/重启，避免多个文件同时打开时并发重复启动。
     */
    private suspend fun ensureProcess(ctx: VirtualFile?): UnocssProcess? {
        if (!nodeEnvInstalled) return null

        // 已存在存活的进程时无需加锁，快速返回
        unocssProcess?.let { if (it.process.isAlive) return it }

        processMutex.withLock {
            if (unocssProcess == null && ctx != null && isUnocssInstalled(ctx)) {
                initProcess(ctx)
                // 直接对刚创建的进程更新配置，避免重入 ensureProcess
                unocssProcess?.let { updateConfig(it) }
            }

            if (unocssProcess?.process?.isAlive == false) {
                println("Unocss process is dead, restarting...")
                unocssProcess?.let { Disposer.dispose(it) }
                unocssProcess = null
                ctx?.let { initProcess(it) }
                unocssProcess?.let { updateConfig(it) }
            }
        }

        return unocssProcess
    }

    /**
     * 检查上下文文件所在项目是否安装了 unocss / @unocss
     */
    private suspend fun isUnocssInstalled(context: VirtualFile) = coroutineScope s@{
        val packageJson = PackageJsonUtil.findUpPackageJson(context.toLocalVirtualFile())
            ?: return@s false

        val unocssPackage = async {
            NodeInstalledPackageFinder(project, packageJson)
                .findInstalledPackage("unocss")
        }
        val unocssPackageAt = async {
            NodeInstalledPackageFinder(project, packageJson)
                .findInstalledPackage("@unocss")
        }

        unocssPackage.await() != null || unocssPackageAt.await() != null
    }

    fun onFileOpened(file: VirtualFile) {
        scope.launch {
            ensureProcess(file)
        }
    }

    fun isProcessRunning(): Boolean {
        return unocssProcess != null
    }

    // 初始化Unocss进程
    // (!) 初始化之前请检查是否是Node项目，以及安装了unocss
    private fun initProcess(ctx: VirtualFile) = runCatching {
        if (unocssProcess != null) return@runCatching
        println("Starting unocss process with ctx: $ctx")
        unocssProcess = UnocssProcess(project, ctx).also {
            // Auto dispose with project service
            Disposer.register(this, it)
        }
        println("Unocss process started!")

        // Update status bar

        // StatusBarWidgetsManager is annotated by @Service(Service.Level.PROJECT)
        // why DevKit warned me that it's an application-level service?
        @Suppress("IncorrectServiceRetrieving")
        project.service<StatusBarWidgetsManager>()
            .updateWidget(UnocssStatusBarFactory::class.java)
    }.onFailure {
        it.printStackTrace()
    }

    fun updateConfigIfRunning() {
        val process = unocssProcess ?: return
        scope.launch {
            updateConfig(process)
        }
    }

    private suspend fun updateConfig(process: UnocssProcess) {
        withBackgroundProgress(project, "Updating unocss config") {
            println("Updating unocss config...")

            runCatching {
                UnocssConfigManager.updateConfig(
                    process.sendCommand<Nothing, ResolveConfigResult>(RpcAction.ResolveConfig, null)
                )
            }.onFailure {
                it.printStackTrace()
                println("(!) Failed to resolve unocss config: $it")
            }

            println("Unocss config updated!")
        }
    }

    fun updateSettings() {
        val process = unocssProcess ?: return
        scope.launch {
            val settingsState = UnocssSettingsState.of(project)
            withTimeout(1000) {
                process.sendCommand(
                    RpcAction.UpdateSettings,
                    UpdateSettingsCommandData(
                        matchType = settingsState.matchType.name.lowercase(),
                    )
                )
            }
        }
    }

    /**
     * 统一的 RPC 调用入口：确保进程存在、套用超时与异常兜底。
     * 进程不可用或调用失败时返回 null，调用方按需提供默认值。
     */
    private suspend fun <T> withProcess(
        file: VirtualFile?,
        timeoutMillis: Long = RPC_TIMEOUT,
        action: suspend (UnocssProcess) -> T,
    ): T? {
        val process = ensureProcess(file) ?: return null
        return runCatching {
            withTimeout(timeoutMillis) { action(process) }
        }.onFailure {
            println("(!) UnoCSS RPC failed: $it")
        }.getOrNull()
    }

    suspend fun getCompletion(
        ctx: VirtualFile,
        prefix: String,
        cursor: Int = prefix.length,
        maxItems: Int
    ): List<SuggestionItem> = withProcess(ctx) { process ->
        process.sendCommand<GetCompleteCommandData, SuggestionItemList>(
            RpcAction.GetComplete,
            GetCompleteCommandData(content = prefix, cursor = cursor, maxItems = maxItems)
        )
    } ?: emptyList()

    suspend fun resolveCssByOffset(file: PsiFile, offset: Int): ResolveCSSResult? =
        withProcess(file.virtualFile) { process ->
            process.sendCommand(
                RpcAction.ResolveCssByOffset,
                ResolveCSSByOffsetCommandData(content = file.text, cursor = offset)
            )
        }

    suspend fun resolveCss(file: VirtualFile?, content: String): ResolveCSSResult? =
        withProcess(file) { it.sendCommand(RpcAction.ResolveCss, ResolveCSSCommandData(content)) }

    suspend fun resolveAnnotations(file: VirtualFile?, content: String): ResolveAnnotationsResult? =
        withProcess(file) { process ->
            process.sendCommand(
                RpcAction.ResolveAnnotations,
                ResolveAnnotationsCommandData(id = file?.path ?: "", content = content)
            )
        }

    suspend fun resolveBreakpoints(file: VirtualFile?): ResolveBreakpointsResult? =
        withProcess(file) { it.sendCommand(RpcAction.ResolveBreakpoints, null) }

    suspend fun resolveToken(file: VirtualFile?, raw: String, alias: String? = null): ResolveTokenResult? =
        withProcess(file) { it.sendCommand(RpcAction.ResolveToken, ResolveTokenResultCommandData(raw, alias)) }
}
