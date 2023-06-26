package me.rerere.unocssintellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.rerere.unocssintellij.rpc.RpcCommand
import me.rerere.unocssintellij.rpc.RpcResponse
import me.rerere.unocssintellij.util.toLocalVirtualFile
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class UnocssProcess(private val project: Project) {
    val lock = ReentrantLock()
    var process: Process? = null
    var inputStream = process?.inputStream?.bufferedReader()

    fun start(context: VirtualFile) {
        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter ?: run {
            error("Node.js interpreter not found")
        }
        val configurator = NodeCommandLineConfigurator.find(interpreter)
        val directory = JSLanguageServiceUtil.getPluginDirectory(Unocss::class.java, "unojs") ?: return
        val exe = "${directory}${File.separator}service.js"
        val commandLine = GeneralCommandLine("", exe)
        configurator.configure(commandLine)

        val realCtx = context.toLocalVirtualFile()
        val packageJson = PackageJsonUtil.findUpPackageJson(realCtx) ?: run {
            error("Package.json not found: $context")
        }
        val workDir = packageJson.parent.path
        commandLine.withWorkDirectory(workDir)
        val handler = CapturingProcessHandler(commandLine)

        process = handler.process.also {
            it.onExit().thenAccept {
                println("Unocss process exited")
            }
        }
        inputStream = process?.inputStream?.bufferedReader()
    }

    fun stop() {
        process?.destroy()
    }

    fun isRunning(): Boolean {
        return process?.isAlive ?: false
    }

    inline fun <reified C, reified R> sendCommand(command: C): R where C : RpcCommand, R : RpcResponse {
        lock.withLock {
            val json = Unocss.JSON.encodeToString<C>(command)

            // Send command to process
            process?.outputStream?.let {
                it.write(json.toByteArray())
                it.write("\n".toByteArray())
                it.flush()
            } ?: error("null response, running ${isRunning()}")

            // Read response from process
            val resJson = inputStream?.readLine() ?: error("Process not running")
            return Unocss.JSON.decodeFromString<R>(resJson)
        }
    }
}