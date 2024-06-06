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
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.rpc.GetCompleteCommandData
import me.rerere.unocssintellij.rpc.ResolveAnnotationsCommandData
import me.rerere.unocssintellij.rpc.ResolveAnnotationsResult
import me.rerere.unocssintellij.rpc.ResolveBreakpointsResult
import me.rerere.unocssintellij.rpc.ResolveCSSByOffsetCommandData
import me.rerere.unocssintellij.rpc.ResolveCSSCommandData
import me.rerere.unocssintellij.rpc.ResolveCSSResult
import me.rerere.unocssintellij.rpc.ResolveConfigResult
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

@Service(Service.Level.PROJECT)
class UnocssService(private val project: Project, private val scope: CoroutineScope) : Disposable {

    private var unocssProcess: UnocssProcess? = null

    private val nodeEnvInstalled by lazy {
        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
        interpreter != null && (interpreter is NodeJsLocalInterpreter || interpreter is WslNodeInterpreter)
    }

    init {
        // 监听文件变化
        VirtualFileManager.getInstance().addAsyncFileListener({ events ->
            var detected = false
            var ctx: VirtualFile? = null
            events.forEach { event ->
                val file = event.file
                if (file == null || !file.isValid) return@forEach
                if (event.requestor == this) return@forEach
                if (event.isFromSave) {
                    if (SENSITIVE_FILES.any { file.name.contains(it) }) {
                        println("Detected sensitive file change: ${file.name}")
                        detected = true
                        ctx = file
                        return@forEach
                    }
                }
            }
            object : ChangeApplier {
                override fun afterVfsChange() {
                    if (detected && unocssProcess != null) {
                        ctx?.let {
                            // reload config when sensitive file changed
                            scope.launch { updateConfig(it) }
                        }
                    }
                }
            }
        }, this)
    }

    override fun dispose() {
        scope.cancel()
    }

    private suspend fun getProcess(ctx: VirtualFile?): UnocssProcess? {
        if (!nodeEnvInstalled) return null
        if (unocssProcess == null && ctx != null && isUnocssInstalled(project, ctx)) {
            initProcess(ctx)
            updateConfig(ctx)
        }

        if (unocssProcess?.process?.isAlive == false) {
            println("Unocss process is dead, restarting...")

            unocssProcess?.let { Disposer.dispose(it) }
            unocssProcess = null

            ctx?.let { initProcess(it) }
            updateConfigIfRunning()
        }

        return unocssProcess
    }

    /**
     * Check if unocss is installed in the project
     *
     * @param project The project
     * @param context The context file
     */
    private suspend fun isUnocssInstalled(project: Project, context: VirtualFile) = coroutineScope s@{
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
            this@UnocssService.getProcess(file)
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

    // 更新Unocss配置
    private suspend fun updateConfig(ctx: VirtualFile) = runCatching {
        val process = getProcess(ctx) ?: return@runCatching
        updateConfig(process)
    }.onFailure {
        println("(!) Failed to update unocss config: $it")
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

    suspend fun getCompletion(
        ctx: VirtualFile,
        prefix: String,
        cursor: Int = prefix.length,
        maxItems: Int
    ): List<SuggestionItem> {
        val process = getProcess(ctx) ?: return emptyList()

        return runCatching {
            withTimeout<SuggestionItemList>(1000) {
                process.sendCommand(
                    RpcAction.GetComplete,
                    GetCompleteCommandData(
                        content = prefix,
                        cursor = cursor,
                        maxItems = maxItems
                    )
                )
            }
        }.onFailure {
            println("(!) Failed to get completion: $it")
        }.getOrDefault(emptyList())
    }

    suspend fun resolveCssByOffset(file: PsiFile, offset: Int): ResolveCSSResult? {
        val process = getProcess(file.virtualFile) ?: return null
        val text = file.text
        return withTimeout(1000) {
            process.sendCommand(
                RpcAction.ResolveCssByOffset,
                ResolveCSSByOffsetCommandData(
                    content = text,
                    cursor = offset
                )
            )
        }
    }

    suspend fun resolveCss(file: VirtualFile?, content: String): ResolveCSSResult? {
        val process = getProcess(file) ?: return null
        return process.sendCommand<ResolveCSSCommandData, ResolveCSSResult>(
            RpcAction.ResolveCss,
            ResolveCSSCommandData(
                content = content
            )
        )
    }

    suspend fun resolveAnnotations(file: VirtualFile?, content: String): ResolveAnnotationsResult? {
        val process = getProcess(file) ?: return null
        return process.sendCommand<ResolveAnnotationsCommandData, ResolveAnnotationsResult>(
            RpcAction.ResolveAnnotations,
            ResolveAnnotationsCommandData(
                id = file?.path ?: "",
                content = content
            )
        )
    }

    suspend fun resolveBreakpoints(file: VirtualFile?): ResolveBreakpointsResult? {
        val process = getProcess(file) ?: return null
        return process.sendCommand<Unit, ResolveBreakpointsResult>(
            RpcAction.ResolveBreakpoints,
            null
        )
    }
}
