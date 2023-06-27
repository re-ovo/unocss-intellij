package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.lang.psi.UnocssTypes
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons

class UnocssLineMarker : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.elementType == UnocssTypes.CLASSNAME) {
            val project = element.project
            val service = project.service<UnocssService>()

            val result = service.resolveCss(
                element.containingFile.virtualFile,
                element.text
            )
            val css = result?.css ?: return null

            // color icon
            val colorValue = parseColors(css)
            if (colorValue.isNotEmpty()) {
                return LineMarkerInfo(
                    element,
                    element.textRange,
                    ColorIcon(12, colorValue.first()),
                    null,
                    null,
                    GutterIconRenderer.Alignment.RIGHT,
                ) { "UNOCSS" }
            }

            // svg icon
            val iconValue = parseIcons(css) ?: return null
            val img = SVGIcon(iconValue)
            return LineMarkerInfo(
                element,
                element.textRange,
                img,
                null,
                null,
                GutterIconRenderer.Alignment.LEFT
            ) { "UNOCSS" }
        }
        return null
    }
}