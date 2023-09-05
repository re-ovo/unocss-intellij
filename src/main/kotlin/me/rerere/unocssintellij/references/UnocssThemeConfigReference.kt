package me.rerere.unocssintellij.references

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

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
        val themeConfigValue = UnoConfigPsiHelper.findThemeConfig(element) ?: return emptyArray()

        val result = mutableListOf<ResolveResult>()
        if (themeConfigValue is JSObjectLiteralExpression) {
            val referencedProperty = UnoConfigPsiHelper
                .findThemeConfigProperty(themeConfigValue, themeValue.split("."))

            if (referencedProperty != null) {
                result.add(PsiElementResolveResult(referencedProperty))
            }
        }

        return result.toTypedArray()
    }

    protected fun computeTailText(property: JSProperty): String {
        val value = property.value
        val content = if (value is JSObjectLiteralExpression) {
            "{...}"
        } else {
            value!!.text.trim('\'', '"')
        }
        return " $content "
    }
}