package me.rerere.unocssintellij.util

import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object IconResources {
    @JvmStatic
    val PluginIcon = IconLoader.getIcon("/icons/pluginIcon.svg", javaClass)
}

object UnocssConfigFile : LanguageFileType(JavascriptLanguage.INSTANCE) {
    override fun getName(): String = "Unocss"

    override fun getDescription(): String = "The instant on-demand atomic CSS engine."

    override fun getDefaultExtension(): String = "js"

    override fun getIcon(): Icon = IconResources.PluginIcon
}