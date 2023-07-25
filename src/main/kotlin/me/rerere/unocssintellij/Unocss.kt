package me.rerere.unocssintellij

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.javascript.nodejs.packageJson.NodeInstalledPackageFinder
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import me.rerere.unocssintellij.util.toLocalVirtualFile

object Unocss {
    // Logger
    val Logger = logger<Unocss>()

    // Global JSON instance
    val GSON: Gson by lazy { GsonBuilder().create() }

    /**
     * Check if unocss is installed in the project
     *
     * @param project The project
     * @param context The context file
     */
    fun isUnocssInstalled(project: Project, context: VirtualFile) : Boolean {
        val packageJson = PackageJsonUtil.findUpPackageJson(context.toLocalVirtualFile()) ?: return false
        val unocssPackage = NodeInstalledPackageFinder(project, packageJson)
            .findInstalledPackage("unocss")
        val unocssPackageAt = NodeInstalledPackageFinder(project, packageJson)
            .findInstalledPackage("@unocss")
        return unocssPackage != null || unocssPackageAt != null
    }
}