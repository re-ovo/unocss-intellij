package me.rerere.unocssintellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.packageJson.NodeInstalledPackageFinder
import com.intellij.lang.javascript.JSLanguageUtil
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.lang.javascript.service.JSLanguageService
import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object Unocss

/**
 * Check if unocss is installed in the project
 *
 * @param project The project
 * @param context The context file
 */
fun isUnocssInstalled(project: Project, context: VirtualFile) : Boolean {
    val packageJson = PackageJsonUtil.findUpPackageJson(context) ?: return false
    val unocssPackage = NodeInstalledPackageFinder(project, packageJson)
        .findInstalledPackage("unocss")
    return unocssPackage != null
}

class FileListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val project = event.manager.project
        val file = event.newFile ?: return

        val interpreter = NodeJsInterpreterManager.getInstance(project).interpreter ?: return
        val configurator = NodeCommandLineConfigurator.find(interpreter)
        val directory = JSLanguageServiceUtil.getPluginDirectory(Unocss::class.java, "unojs") ?: return
        val exe =  "${directory}${File.separator}out.js"
        val commandLine = GeneralCommandLine("", exe)
        configurator.configure(commandLine)

        commandLine.withWorkDirectory(PackageJsonUtil.findUpPackageJson(file)?.parent?.path ?: return)

        val handler = CapturingProcessHandler(commandLine)
        println("start process")
        val output = handler.runProcess()

        println(
            """
                stdout: ${output.stdout}
                stderr: ${output.stderr}
                exitCode: ${output.exitCode}
            """.trimIndent()
        )
    }
}