package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.css.impl.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.references.UnoConfigPsiHelper
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseHexColor

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
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement(CssElementTypes.CSS_STRING_TOKEN)
                .withSuperParent(4,
                    StandardPatterns
                        .instanceOf(CssFunctionImpl::class.java)
                ),
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
            CssElementTypes.CSS_STRING_TOKEN -> {
                // completion for path: theme('path')
                val parent = position.parentOfType<CssFunctionImpl>()
                if(parent != null && parent.name == "theme") {
                    val service = position.project.service<UnocssService>()
                    service.themeEntries
                        .forEach { (k, v) ->
                            val color = parseHexColor(v)
                            result.addElement(
                                LookupElementBuilder
                                    .create(k)
                                    .withTailText("  $v")
                                    .withTypeText("theme")
                                    .withIcon(
                                        if(color != null) {
                                            ColorIcon(16, color)
                                        } else PluginIcon
                                    )
                            )
                        }
                }
            }

            CssElementTypes.CSS_IDENT -> {
                // allow apply variable only in declaration key
                if (position.parent is CssDeclarationImpl) {
                    UnoConfigPsiHelper.defaultApplyVariable.forEach {
                        result.addPriorityElement(
                            LookupElementBuilder
                                .create(it)
                                .withIcon(PluginIcon)
                                .withInsertHandler { insertionCtx, _ ->
                                    insertionCtx.document.insertString(insertionCtx.selectionEndOffset, ": ")
                                    insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset)
                                }
                        )
                    }
                }

                // allow theme() only  in declaration value
                if (position.parent is CssTermImpl) {
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