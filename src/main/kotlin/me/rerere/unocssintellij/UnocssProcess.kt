package me.rerere.unocssintellij

import com.google.common.collect.Sets
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.wsl.WslNodeInterpreter
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rerere.unocssintellij.rpc.RpcAction
import me.rerere.unocssintellij.rpc.RpcCommand
import me.rerere.unocssintellij.util.toLocalVirtualFile
import java.io.File
import java.util.*
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private val MAX_WAIT_TIME = 10.seconds


class UnocssProcess(project: Project, context: VirtualFile) : Disposable {
    val process: Process
    val waitingCommands: MutableSet<AwaitingCommand> = Sets.newConcurrentHashSet()

    init {
        println("[UnoProcess] Starting UnoProcess")
        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter
        if (interpreter !is NodeJsLocalInterpreter && interpreter !is WslNodeInterpreter) error("Unsupported interpreter")
        val configurator = NodeCommandLineConfigurator.find(interpreter)
        val directory = JSLanguageServiceUtil.getPluginDirectory(Unocss::class.java, "unojs")
            ?: error("Plugin directory not found")
        val exe = "${directory}${File.separator}service.js"
        val commandLine = GeneralCommandLine("", exe)
        configurator.configure(commandLine)
        println("[UnoProcess] Command line: $commandLine")
        val realCtx = context.toLocalVirtualFile()
        val packageJson = PackageJsonUtil.findUpPackageJson(realCtx) ?: run {
            error("Package.json not found: $context")
        }
        val workDir = packageJson.parent.path
        commandLine.withWorkDirectory(workDir)
        println("[UnoProcess] Work directory: $workDir")
        val handler = CapturingProcessHandler(commandLine)

        process = handler.process

        // Create read thread
        process.inputStream.let { inputStream ->
            thread {
                inputStream.bufferedReader().useLines { lines ->

                    // Handle response
                    lines.forEach { line ->
                        if (line.startsWith("[UnoProcess]")) {
                            println(line)
                        } else {
                            val jsonObject = runCatching { JsonParser.parseString(line).asJsonObject }
                                .onFailure {
                                    println("[UnoProcess] Failed to parse json: $line")
                                }
                                .getOrNull() ?: return@forEach
                            val id = jsonObject["id"]?.asString ?: return@forEach
                            val waitingCommand = waitingCommands.find { it.id == id } ?: return@forEach
                            waitingCommands.remove(waitingCommand)
                            waitingCommand.callback(jsonObject)
                        }
                    }

                    // Check timeout
                    val end = System.currentTimeMillis()
                    waitingCommands.removeIf {
                        if (end - it.start > MAX_WAIT_TIME.inWholeMilliseconds) {
                            println("[UnoProcess] Command ${it.id} timeout")
                            it.callback(
                                JsonObject().apply {
                                    addProperty("error", "Timeout")
                                }
                            )
                            true
                        } else {
                            false
                        }
                    }
                }
                println("[UnoProcess] Read thread finished")
            }

            println("[UnoProcess] Read thread started")
        }

        // Create error thread
        process.errorStream.let { errorStream ->
            thread {
                errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        println("[UnoProcess] Error: $line")
                    }
                }
            }
        }
    }

    override fun dispose() {
        process.destroyForcibly()
        waitingCommands.clear()
    }

    // 发送命令并等待结果
    // 这里使用 suspendCancellableCoroutine 将回调转换为挂起并可取消的协程
    suspend inline fun <C, reified R> sendCommand(action: RpcAction, command: C?): R = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val start = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()

            // Send command to process
            val json = Unocss.GSON.toJson(RpcCommand(id, action.key, command)) + "\n"
            process.outputStream?.let {
                it.write(json.toByteArray())
                it.flush()
            } ?: run {
                continuation.resumeWithException(RuntimeException("Process output stream not found"))
                return@suspendCancellableCoroutine
            }

            // Wait for response
            this@UnocssProcess.waitingCommands.add(AwaitingCommand(id, start) { jsonObject ->
                if (jsonObject.has("error")) {
                    continuation.resumeWithException(
                        RuntimeException(jsonObject["error"]?.asString ?: "unknown error")
                    )
                } else if (!jsonObject.has("result")) {
                    continuation.resume(Unit as R)
                } else {
                    val res = Unocss.GSON.fromJson(jsonObject["result"].toString(), object : TypeToken<R>() {})
                    continuation.resume(res)
                }
            })

            // Handle cancellation
            continuation.invokeOnCancellation {
                waitingCommands.removeIf { it.id == id }
            }
        }
    }
}

data class AwaitingCommand(
    val id: String,
    val start: Long,
    val callback: (JsonObject) -> Unit
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AwaitingCommand

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}