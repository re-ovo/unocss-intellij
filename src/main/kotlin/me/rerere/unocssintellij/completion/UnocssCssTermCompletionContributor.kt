package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssTermList
import com.intellij.psi.css.impl.CssAtRuleImpl
import com.intellij.psi.css.impl.CssDeclarationImpl
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.filters.position.FilterPattern
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentOfTypes
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import me.rerere.unocssintellij.util.UnoConfigHelper

class UnocssCssTermCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_IDENT)
                .and(FilterPattern(UnocssCssTermCompletionFilter)),
            UnocssCssTermCompletionProvider
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_STRING_TOKEN)
                .and(FilterPattern(UnocssCssTermCompletionFilter)),
            UnocssCssTermCompletionProvider
        )
    }

    private object UnocssCssTermCompletionFilter : ElementFilter {

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
            if (psiElement.parentOfType<CssRulesetImpl>() == null) {
                return false
            }
            // skip
            if (psiElement.elementType === CssElementTypes.CSS_IDENT
                && psiElement.parent is CssDeclarationImpl
            ) {
                return false
            }
            val cssDeclaration: PsiElement = psiElement.parentOfTypes(
                // for apply variable
                CssDeclarationImpl::class,
                // for @apply
                CssAtRuleImpl::class
            ) ?: return false

            val propKey = cssDeclaration.firstChild
            return propKey.text == "@apply" || propKey.text in UnoConfigHelper.defaultApplyVariable
        }

        override fun isClassAcceptable(hintClass: Class<*>?): Boolean = true
    }
}

object UnocssCssTermCompletionProvider : UnocssCompletionProvider() {

    private const val COMPLETION_PLACEHOLDER = "IntellijIdeaRulezzz"

    override fun resolvePrefix(parameters: CompletionParameters, result: CompletionResultSet): PrefixHolder {
        val element = parameters.position

        val rawPrefix = result.prefixMatcher.prefix
        val prefix = if (element.elementType == CssElementTypes.CSS_STRING_TOKEN)
            rawPrefix.substring(rawPrefix.lastIndexOf(" ") + 1)
        else rawPrefix

        return PrefixHolder(prefix)
    }

    override fun customizeLookupElement(
        lookupElement: LookupElementBuilder,
        typingPrefix: String,
        className: String,
        position: PsiElement
    ): LookupElementBuilder {
        return lookupElement.withInsertHandler { context, item ->

            // wrap with quotes
            // see: https://unocss.dev/transformers/directives#adding-quotes
            if (item.lookupString.contains(':') && position.elementType != CssElementTypes.CSS_STRING_TOKEN) {
                // only for apply variable
                val cssDeclaration: PsiElement = position.parentOfType<CssDeclaration>() ?: return@withInsertHandler
                val prevEndOffset = cssDeclaration.endOffset
                val newEndOffset = prevEndOffset - COMPLETION_PLACEHOLDER.length + (item.lookupString.length - typingPrefix.length)

                position.parentOfType<CssTermList>()?.let {
                    context.document.insertString(it.startOffset, "\"")
                    context.document.insertString(newEndOffset + 1, "\"")
                    context.editor.caretModel.moveToOffset(context.selectionEndOffset)
                }
            }
        }
    }
}