package me.rerere.unocssintellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.*
import me.rerere.unocssintellij.rpc.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    "nuxt.config"
)

@Service(Service.Level.PROJECT)
class UnocssService(private val project: Project) : Disposable {
    private var unocssProcess: UnocssProcess? = null
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
                            scope.launch {
                                updateConfig(it).onFailure {
                                    println("(!) Failed to update unocss config: $it")
                                }
                            }
                        }
                    }
                }
            }
        }, this)
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun getProcess(ctx: VirtualFile): UnocssProcess? {
        if (unocssProcess == null && Unocss.isUnocssInstalled(project, ctx)) {
            initProcess(ctx)
            scope.launch {
                updateConfig(ctx).onFailure {
                    println("(!) Failed to update unocss config: $it")
                }
            }
        }
        return unocssProcess
    }

    fun onFileOpened(file: VirtualFile) {
        this.getProcess(file)
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
    }

    // 更新Unocss配置
    private suspend fun updateConfig(ctx: VirtualFile) = runCatching {
        val process = getProcess(ctx) ?: return@runCatching
        println("Updating unocss config...")
        process.sendCommand<Any?, Any?>(RpcAction.ResolveConfig, null)
        println("Unocss config updated!")
    }

    fun getCompletion(ctx: VirtualFile, prefix: String, cursor: Int): List<SuggestionItem> {
        val process = getProcess(ctx) ?: return emptyList()
        val response: SuggestionItemList = runBlocking {
            process.sendCommand(
                RpcAction.GetComplete,
                GetCompleteCommandData(
                    content = prefix,
                    cursor = cursor
                )
            )
        }
        return response
    }

    fun resolveCssByOffset(file: PsiFile, offset: Int): ResolveCSSResult? {
        val process = getProcess(file.virtualFile) ?: return null
        val text = file.text
        val response: ResolveCSSResult = runBlocking {
            process.sendCommand(
                RpcAction.ResolveCssByOffset,
                ResolveCSSByOffsetCommandData(
                    content = text,
                    cursor = offset
                )
            )
        }
        return response
    }

    fun resolveCss(file: VirtualFile, content: String): ResolveCSSResult? {
        val process = getProcess(file) ?: return null
        val response: ResolveCSSResult = runBlocking {
            process.sendCommand(
                RpcAction.ResolveCss,
                ResolveCSSCommandData(
                    content = content
                )
            )
        }
        return response
    }
}
