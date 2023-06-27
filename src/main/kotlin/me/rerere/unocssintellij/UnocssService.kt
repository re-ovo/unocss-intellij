package me.rerere.unocssintellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import me.rerere.unocssintellij.rpc.*
import java.util.concurrent.Executors

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
    private val communicationThread = Executors.newSingleThreadExecutor()
    private var unocssProcess: UnocssProcess? = null

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
                    if(detected && unocssProcess != null) {
                        ctx?.let {
                            updateConfig(it).onFailure {
                                println("(!) Failed to update unocss config: $it")
                            }
                        }
                    }
                }
            }
        }, this)
    }

    private fun getProcess(ctx: VirtualFile): UnocssProcess? {
        if (unocssProcess == null && Unocss.isUnocssInstalled(project, ctx)) {
            communicationThread.submit {
                initProcess(ctx)
                updateConfig(ctx).onFailure {
                    println("(!) Failed to update unocss config: $it")
                }
            }
        }
        return unocssProcess
    }

    // 初始化Unocss进程
    // (!) 初始化之前请检查是否是Node项目，以及安装了unocss
    private fun initProcess(ctx: VirtualFile) = runCatching {
        val process = UnocssProcess(project)
        println("Starting unocss process...")
        process.start(ctx)
        println("Unocss process started!")
        unocssProcess = process
    }

    // 更新Unocss配置
    private fun updateConfig(ctx: VirtualFile) = runCatching {
        val process = getProcess(ctx) ?: return@runCatching
        println("Updating unocss config...")
        process.sendCommand<ResolveConfig, RpcResponseUnit>(
            ResolveConfig()
        )
        println("Unocss config updated!")
    }

    // 卸载Unocss进程
    private fun stopProcess() = runCatching {
        unocssProcess?.stop()
        unocssProcess = null
    }

    fun getCompletion(ctx: VirtualFile, prefix: String, cursor: Int): List<SuggestionItem> {
        val process = getProcess(ctx) ?: return emptyList()
        val response: SuggestionResponse = process.sendCommand(
            SuggestionCommand(
                data = SuggestionCommandData(
                    content = prefix,
                    cursor = cursor
                )
            )
        )
        return response.result
    }

    fun resolveCssByOffset(file: PsiFile, offset: Int): ResolveCSSResult? {
        val process = getProcess(file.virtualFile) ?: return null
        val response: ResolveCSSResponse = process.sendCommand(
            ResolveCSSByOffsetCommand(
                data = ResolveCSSByOffsetCommandData(
                    content = file.text,
                    cursor = offset
                )
            )
        )
        return response.result
    }

    fun resolveCss(file: VirtualFile, content: String): ResolveCSSResult? {
        val process = getProcess(file) ?: return null
        val response: ResolveCSSResponse = process.sendCommand(
            ResolveCSSCommand(
                data = ResolveCSSCommandData(
                    content = content
                )
            )
        )
        return response.result
    }

    override fun dispose() {
        communicationThread.shutdown()
        this.stopProcess()
    }
}
