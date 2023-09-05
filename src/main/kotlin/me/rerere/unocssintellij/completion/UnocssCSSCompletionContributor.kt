package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.CssAtRuleImpl
import com.intellij.psi.css.impl.CssDeclarationImpl
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.position.FilterPattern
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

class UnocssCSSCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_IDENT)
                .and(FilterPattern(UnocssCssCompletionFilter)),
            UnocssCSSTermListCompletionProvider
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_STRING_TOKEN)
                .and(FilterPattern(UnocssCssCompletionFilter)),
            UnocssCSSTermListCompletionProvider
        )
    }
}

object UnocssCssCompletionFilter : ElementFilter {

    /**
     * Unocss completion in css scope only support `@apply` and `--at-apply` value
     *
     * other directives like `@screen` and `theme()` will be provided by ReferenceContributor
     */
    override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
        val psiElement = element as PsiElement
        if (!psiElement.isValid) {
            return false
        }

        val cssDeclaration: PsiElement = PsiTreeUtil.getParentOfType(
            psiElement,
            // for --at-apply
            CssDeclarationImpl::class.java,
            // for @apply
            CssAtRuleImpl::class.java
        ) ?: return false

        val propKey = cssDeclaration.firstChild
        return propKey.text == "--at-apply" || propKey.text == "@apply"
    }

    override fun isClassAcceptable(hintClass: Class<*>?): Boolean = true
}

object UnocssCSSTermListCompletionProvider : UnocssCompletionProvider() {

    override fun resolvePrefix(parameters: CompletionParameters, result: CompletionResultSet): PrefixHolder {
        val element = parameters.position

        val rawPrefix = result.prefixMatcher.prefix
        val prefix = if (element.elementType == CssElementTypes.CSS_STRING_TOKEN)
            rawPrefix.substring(rawPrefix.lastIndexOf(":") + 1)
        else rawPrefix

        return PrefixHolder(prefix)
    }
}