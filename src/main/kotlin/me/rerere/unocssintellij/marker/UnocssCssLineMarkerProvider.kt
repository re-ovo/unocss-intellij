package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.util.elementType
import me.rerere.unocssintellij.model.UnocssResolveMeta

class UnocssCssLineMarkerProvider : UnocssLineMarkerProvider() {
    override fun doGetLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // match when use Directives transformer
        if (element.elementType == CssElementTypes.CSS_IDENT
            || element.elementType == CssElementTypes.CSS_STRING_TOKEN) {
            return getFromCssIdent(element)
        }

        return null
    }

    private fun getFromCssIdent(element: PsiElement): LineMarkerInfo<*>? {
        val cssValue = element.text.trim('"')
        return getLineMarkerInfo(UnocssResolveMeta(element, cssValue, null))
    }
}