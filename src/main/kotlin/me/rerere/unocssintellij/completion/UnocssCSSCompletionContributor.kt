package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.util.elementType

class UnocssCSSCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_IDENT),
            UnocssCSSTermListCompletionProvider
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_STRING_TOKEN),
            UnocssCSSTermListCompletionProvider
        )
    }
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