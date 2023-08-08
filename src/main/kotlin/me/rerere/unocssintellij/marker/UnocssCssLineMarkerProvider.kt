package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.util.elementType

class UnocssCssLineMarkerProvider : UnocssLineMarkerProvider() {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // match when use Directives transformer
        if (element.elementType == CssElementTypes.CSS_IDENT) {
            return getFromCssIdent(element)
        }

        return null
    }

    private fun getFromCssIdent(element: PsiElement): LineMarkerInfo<*>? {
        return getLineMarkerInfo(UnocssLineMarkerMeta(element, element.text, null))
    }
}