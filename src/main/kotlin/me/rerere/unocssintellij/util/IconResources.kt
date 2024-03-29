package me.rerere.unocssintellij.util

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object IconResources {
    @JvmStatic
    val PluginIcon = IconLoader.getIcon("/icons/pluginIcon.svg", javaClass)
}

private val unoFileTypes = arrayOf(
    "uno.config",
    "unocss.config"
)

class UnoIconProvider: FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file.nameWithoutExtension in unoFileTypes) {
            return IconResources.PluginIcon
        }
        return null
    }
}