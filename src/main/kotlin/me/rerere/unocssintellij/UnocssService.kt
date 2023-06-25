package me.rerere.unocssintellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import me.rerere.unocssintellij.rpc.*
import java.util.concurrent.Executors

@Service(Service.Level.PROJECT)
class UnocssService(project: Project) : Disposable {
    private var unocssProcess: UnocssProcess = UnocssProcess(project)
    private val communicationThread = Executors.newSingleThreadExecutor()

    private fun getProcess(ctx: VirtualFile): UnocssProcess {
        if (!unocssProcess.isRunning()) {
            unocssProcess.start(ctx)
            communicationThread.execute {
                unocssProcess.sendCommand<ResolveConfig, RpcResponseUnit>(
                    ResolveConfig()
                )
                println("Unocss process started")
            }
        }
        return unocssProcess
    }

    fun getCompletion(ctx: VirtualFile, prefix: String): List<String> {
        val process = getProcess(ctx)
        val response: SuggestionResponse = process.sendCommand(
            SuggestionCommand(
                data = SuggestionCommandData(
                    content = prefix
                )
            )
        )
        println("$prefix => response: $response")
        return response.result
    }

    fun resolveCss(file: PsiFile, offset: Int): ResolveCSSResult {
        val process = getProcess(file.virtualFile)
        val response: ResolveCSSResponse = process.sendCommand(
            ResolveCSSCommand(
                data = ResolveCSSCommandData(
                    content = file.text,
                    cursor = offset
                )
            )
        )
        return response.result
    }

    override fun dispose() {
        communicationThread.shutdown()
        unocssProcess.stop()
    }
}
