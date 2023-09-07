package me.rerere.unocssintellij.marker.line

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.util.elementType
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.util.inCssThemeFunction
import me.rerere.unocssintellij.util.parseHexColor

class UnocssCssLineMarkerProvider : UnocssLineMarkerProvider() {
    override fun doGetLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // match when use Directives transformer
        if (element.elementType == CssElementTypes.CSS_IDENT
            || element.elementType == CssElementTypes.CSS_STRING_TOKEN
        ) {
            return getFromCssIdent(element)
        }

        return null
    }

    private fun getFromCssIdent(element: PsiElement): LineMarkerInfo<*>? {
        val cssValue = element.text.trim('\'', '"')

        return if (element.inCssThemeFunction()) {
            getLineMarkerInfoFromConfigFile(element, cssValue)
        } else {
            getLineMarkerInfo(UnocssResolveMeta(element, cssValue, null))
        }
    }

    private fun getLineMarkerInfoFromConfigFile(element: PsiElement, themeArg: String): LineMarkerInfo<*>? {
        val configValue = UnocssConfigManager.getThemeValue(themeArg) ?: return null
        // color icon
        val colorValue = parseHexColor(configValue) ?: return null
        return LineMarkerInfo(
            element,
            element.textRange,
            ColorIcon(12, colorValue),
            null,
            null,
            GutterIconRenderer.Alignment.LEFT,
        ) { "UNOCSS" }
    }
}