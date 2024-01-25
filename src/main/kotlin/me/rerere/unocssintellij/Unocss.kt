package me.rerere.unocssintellij

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.javascript.nodejs.packageJson.NodeInstalledPackageFinder
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.rerere.unocssintellij.util.toLocalVirtualFile

object Unocss {
    // Logger
    val Logger = logger<Unocss>()

    // Config file name
    val ConfigFiles: Set<String> = listOf(
        "uno.config",
        "vite.config",
        "svelte.config",
        "iles.config",
        "astro.config",
        "nuxt.config",
    ).flatMap {
        listOf("$it.js", "$it.ts")
    }.toSet()

    // Global JSON instance
    val GSON: Gson by lazy { GsonBuilder().create() }

    /**
     * Check if unocss is installed in the project
     *
     * @param project The project
     * @param context The context file
     */
    suspend fun isUnocssInstalled(project: Project, context: VirtualFile) = coroutineScope {
        val packageJson = PackageJsonUtil.findUpPackageJson(context.toLocalVirtualFile())
            ?: return@coroutineScope false

        val unocssPackage = async {
            smartReadAction(project) {
                NodeInstalledPackageFinder(project, packageJson)
                    .findInstalledPackage("unocss")
            }
        }
        val unocssPackageAt = async {
            smartReadAction(project) {
                NodeInstalledPackageFinder(project, packageJson)
                    .findInstalledPackage("@unocss")
            }
        }

        unocssPackage.await() != null || unocssPackageAt.await() != null
    }
}