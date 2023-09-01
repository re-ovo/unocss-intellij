package me.rerere.unocssintellij.references

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.css.CssString
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.position.FilterPattern
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext

class UnocssCssReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    val elementFilter = UnocssCssConfigReferenceProvider.ReferenceFilter()
    val unocssProvider = UnocssCssConfigReferenceProvider()

    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(CssString::class.java).and(FilterPattern(elementFilter)),
      unocssProvider
    )
  }
}

class UnocssCssConfigReferenceProvider : PsiReferenceProvider() {

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is CssString) {
      return PsiReference.EMPTY_ARRAY
    }

    val stringToken = element.firstChild
    if (stringToken.elementType != CssElementTypes.CSS_STRING_TOKEN) {
      return PsiReference.EMPTY_ARRAY
    }

    // ignore quotes
    val range = TextRange(1, stringToken.textLength - 1)
    return arrayOf(UnocssThemeReference(element, range))
  }

  class ReferenceFilter : ElementFilter {

    override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
      val psiElement = element as PsiElement
      if (!psiElement.isValid) {
        return false
      }
      return UnoConfigPsiHelper.inCssThemeFunction(psiElement)
    }

    override fun isClassAcceptable(hintClass: Class<*>?): Boolean {
      return true
    }
  }
}