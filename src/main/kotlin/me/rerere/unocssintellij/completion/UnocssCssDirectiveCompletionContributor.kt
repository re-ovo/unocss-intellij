package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.ProcessingContext

/**
 * Provide `@apply`, `@screen`, `theme()` directive completion
 */
class UnocssCssDirectiveCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_IDENT),
            UnocssCssDirectiveCompletionProvider
        )
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_ATKEYWORD),
            UnocssCssDirectiveCompletionProvider
        )
    }
}

object UnocssCssDirectiveCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        when (position.elementType) {
            CssElementTypes.CSS_IDENT -> {
                result.addPriorityElement(
                    LookupElementBuilder
                        .create("--at-apply")
                        .withIcon(PluginIcon)
                        .withInsertHandler { insertionCtx, _ ->
                            insertionCtx.document.insertString(insertionCtx.selectionEndOffset, ": ")
                            insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset)
                        }
                )
                result.addPriorityElement(
                    LookupElementBuilder
                        .create("theme")
                        .withIcon(PluginIcon)
                        .withTailText("('themeRef')", true)
                        .withInsertHandler { insertionCtx, _ ->
                            insertionCtx.document.insertString(insertionCtx.selectionEndOffset, "('')")
                            insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset - 2)
                        }
                )
            }

            CssElementTypes.CSS_ATKEYWORD -> {
                val cssDeclaration = PsiTreeUtil.getParentOfType(position, CssRulesetImpl::class.java)
                if (cssDeclaration != null) {
                    result.addPriorityElement(
                        LookupElementBuilder
                            .create("apply")
                            .withPresentableText("@apply")
                            .withIcon(PluginIcon)
                            .withInsertHandler { insertionCtx, _ ->
                                insertionCtx.document.insertString(insertionCtx.selectionEndOffset, " ")
                                insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset)
                            }
                    )
                } else {
                    result.addPriorityElement(
                        LookupElementBuilder
                            .create("screen")
                            .withPresentableText("@screen")
                            .withIcon(PluginIcon)
                            .withInsertHandler { insertionCtx, _ ->
                                insertionCtx.document.insertString(insertionCtx.selectionEndOffset, "  {\n\n}")
                                insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset - 5)
                            }
                    )
                }
            }
        }

        result.restartCompletionOnAnyPrefixChange()
    }

    private fun CompletionResultSet.addPriorityElement(
        lookupElement: LookupElementBuilder,
        priority: Double = 1000.0
    ) {
        addElement(PrioritizedLookupElement.withPriority(lookupElement, priority))
    }
}