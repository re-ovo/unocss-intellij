package me.rerere.unocssintellij.references

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import me.rerere.unocssintellij.util.UnoConfigHelper

class UnocssThemeBreakpointsConfigReference(element: PsiElement, textRange: TextRange) :
    UnocssThemeConfigReference(element, textRange) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val themeConfigValue = UnoConfigHelper.findThemeConfig(element) ?: return emptyArray()

        val results = mutableListOf<ResolveResult>()
        if (themeConfigValue is JSObjectLiteralExpression) {
            val breakpointsProp = themeConfigValue.properties
                .firstOrNull { it.name == "breakpoints" }
                ?.value
            if (breakpointsProp is JSObjectLiteralExpression) {
                val config = breakpointsProp.properties.firstOrNull {
                    it.name == element.text.substring(textRange.startOffset, textRange.endOffset)
                }
                if (config != null) {
                    results.add(PsiElementResolveResult(config))
                }
            }
        }

        return results.toTypedArray()
    }

    override fun getVariants(): Array<Any> {
        val themeConfigValue = UnoConfigHelper.findThemeConfig(element) ?: return emptyArray()
        if (themeConfigValue !is JSObjectLiteralExpression) return emptyArray()

        val variants = mutableListOf<LookupElement>()
        val breakpointsProp = themeConfigValue.properties
            .firstOrNull { it.name == "breakpoints" }
            ?.value

        if (breakpointsProp is JSObjectLiteralExpression) {
            for (it in breakpointsProp.properties) {
                val propName = it.name ?: continue

                variants.add(
                    LookupElementBuilder
                        .create(propName)
                        .withLookupString(propName)
                        .withPresentableText(propName)
                        .withTypeText(themeConfigValue.containingFile.name)
                        .withTailText(computeTailText(it), true)
                )
            }
        }

        return variants.toTypedArray()
    }
}