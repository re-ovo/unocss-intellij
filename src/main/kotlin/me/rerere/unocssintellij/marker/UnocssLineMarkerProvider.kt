package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.ui.ColorIcon
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.util.buildDummyDivTag
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons

data class UnocssLineMarkerMeta(
    val bindElement: PsiElement,
    val attrName: String,
    val attrValue: String?,
) {
    val project get() = bindElement.project
    val virtualFile: VirtualFile? get() = bindElement.containingFile.virtualFile
    val unocssService get() = project.service<UnocssService>()
}

abstract class UnocssLineMarkerProvider : LineMarkerProvider {

    protected fun getLineMarkerInfo(meta: UnocssLineMarkerMeta): LineMarkerInfo<*>? {
        val service = meta.unocssService
        val virtualFile = meta.virtualFile

        val content = buildDummyDivTag(meta.attrName to meta.attrValue)
        val css = runBlocking {
            withTimeout(100) { service.resolveCss(virtualFile, content) }
        }?.css ?: return null

        // color icon
        val colorValue = parseColors(css)
        if (colorValue.isNotEmpty()) {
            return LineMarkerInfo(
                meta.bindElement,
                meta.bindElement.textRange,
                ColorIcon(12, colorValue.first()),
                null,
                null,
                GutterIconRenderer.Alignment.LEFT,
            ) { "UNOCSS" }
        }

        // svg icon
        val iconValue = parseIcons(css) ?: return null
        val img = SVGIcon(iconValue)
        return LineMarkerInfo(
            meta.bindElement,
            meta.bindElement.textRange,
            img,
            null,
            null,
            GutterIconRenderer.Alignment.LEFT
        ) { "UNOCSS" }
    }
}