package me.rerere.unocssintellij

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.graph.option.Editor
import com.intellij.openapi.graph.option.EditorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.psi.PsiFile
import com.intellij.util.application
import com.intellij.util.ui.JBUI.CurrentTheme.Popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
class UnocssService(private val project: Project) : Disposable {
    private var unocssProcess: UnocssProcess? = null
    private val nodeEnvInstalled by lazy {
        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
        interpreter != null && (interpreter is NodeJsLocalInterpreter || interpreter is WslNodeInterpreter)
    }

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        if (unocssProcess == null && ctx != null && Unocss.isUnocssInstalled(project, ctx)) {
            initProcess(ctx)
            updateConfig(ctx).onFailure {
                println("(!) Failed to update unocss config: $it")
            }
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
    }

    fun updateConfigIfRunning() {
        scope.launch {
            val process = unocssProcess ?: return@launch
            updateConfig(process)
        }
    }

    private fun updateConfig(process: UnocssProcess) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating unocss config") {
            lateinit var job: Job

            override fun run(indicator: ProgressIndicator) {
                println("Updating unocss config...")

                job = scope.launch {
                    runCatching {
                        UnocssConfigManager.updateConfig(
                            process.sendCommand<Any?, ResolveConfigResult>(RpcAction.ResolveConfig, null)
                        )
                    }.onFailure {
                        it.printStackTrace()
                        println("(!) Failed to resolve unocss config: $it")
                    }
                }

                runBlocking {
                    while (!job.isCompleted) {
                        delay(50)

                        // Check if canceled
                        indicator.checkCanceled()
                    }
                }

                println("Unocss config updated!")
            }
        })
    }

    fun updateSettings() {
        val process = unocssProcess ?: return
        scope.launch {
            withTimeout(1000) {
                process.sendCommand(
                    RpcAction.UpdateSettings,
                    UpdateSettingsCommandData(
                        matchType = UnocssSettingsState.instance.matchType.name.lowercase(),
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

        return try {
            val response: SuggestionItemList = withTimeout(1000) {
                process.sendCommand(
                    RpcAction.GetComplete,
                    GetCompleteCommandData(
                        content = prefix,
                        cursor = cursor,
                        maxItems = maxItems
                    )
                )
            }

            response
        } catch (e: Exception) {
            println("(!) Failed to get completion: $e")
            emptyList()
        }
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
