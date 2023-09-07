package me.rerere.unocssintellij.references.css

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import me.rerere.unocssintellij.util.UnoConfigHelper

open class UnocssThemeConfigReference(element: PsiElement, protected val textRange: TextRange) :
    PsiReferenceBase<PsiElement>(element, textRange),
    PsiPolyVariantReference {

    private val themeValue: String

    init {
        themeValue = element.text.substring(textRange.startOffset, textRange.endOffset)
            .trim('\'', '"')
    }

    override fun resolve(): PsiElement? {
        val resolveResults = multiResolve(false)
        return if (resolveResults.size == 1) resolveResults[0].element else null
    }

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val themeConfigValue = UnoConfigHelper.findThemeConfig(element) ?: return emptyArray()

        val result = mutableListOf<ResolveResult>()
        if (themeConfigValue is JSObjectLiteralExpression) {
            val referencedProperty = UnoConfigHelper
                .findThemeConfigProperty(themeConfigValue, themeValue.split("."))

            if (referencedProperty != null) {
                result.add(PsiElementResolveResult(referencedProperty))
            }
        }

        return result.toTypedArray()
    }
}