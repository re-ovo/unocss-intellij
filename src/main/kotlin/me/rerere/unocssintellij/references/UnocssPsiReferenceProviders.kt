package me.rerere.unocssintellij.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.css.CssString
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssTokenImpl
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.UnoConfigHelper
import me.rerere.unocssintellij.util.inCssThemeFunction
import me.rerere.unocssintellij.util.inScreenDirective

object UnocssCssThemeReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (!UnocssSettingsState.instance.enable) {
            return PsiReference.EMPTY_ARRAY
        }
        if (element !is CssString) {
            return PsiReference.EMPTY_ARRAY
        }

        val stringToken = element.firstChild
        if (stringToken.elementType != CssElementTypes.CSS_STRING_TOKEN) {
            return PsiReference.EMPTY_ARRAY
        }

        // ignore quotes
        val range = TextRange(1, stringToken.textLength - 1)
        return arrayOf(UnocssThemeConfigReference(element, range))
    }

    object ReferenceFilter : ElementFilter {

        override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
            val psiElement = element as PsiElement
            if (!psiElement.isValid) {
                return false
            }
            return psiElement.inCssThemeFunction()
        }

        override fun isClassAcceptable(hintClass: Class<*>?): Boolean {
            return true
        }
    }
}

object UnocssCssScreenReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (!UnocssSettingsState.instance.enable) {
            return PsiReference.EMPTY_ARRAY
        }
        if (element !is CssTokenImpl) {
            return PsiReference.EMPTY_ARRAY
        }

        val keyword = element.text
        val prefix = UnoConfigHelper.screenPrefixes.firstOrNull { keyword.startsWith(it) }

        val range = if (prefix != null) {
            TextRange(prefix.length, keyword.length)
        } else {
            TextRange(0, keyword.length)
        }
        return arrayOf(UnocssThemeBreakpointsConfigReference(element, range))
    }

    object ReferenceFilter : ElementFilter {

        override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
            val psiElement = element as PsiElement
            if (!psiElement.isValid) {
                return false
            }

            return psiElement.inScreenDirective()
        }

        override fun isClassAcceptable(hintClass: Class<*>?): Boolean {
            return true
        }
    }
}