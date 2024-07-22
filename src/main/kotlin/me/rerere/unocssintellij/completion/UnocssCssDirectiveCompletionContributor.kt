package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.css.impl.CssDeclarationImpl
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.css.impl.CssTermImpl
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.documentation.UnocssThemeConfigCompletionLookupSymbol
import me.rerere.unocssintellij.util.UnoConfigHelper
import me.rerere.unocssintellij.util.inCssThemeFunction
import me.rerere.unocssintellij.util.isScreenDirectiveIdent
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
            PlatformPatterns.psiElement(CssElementTypes.CSS_STRING_TOKEN),
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
        if (!UnocssConfigManager.hasTransformerDirective) {
            return
        }

        val position = parameters.position
        when (position.elementType) {
            CssElementTypes.CSS_STRING_TOKEN -> {
                // completion for path: theme('path')
                if (position.inCssThemeFunction()) {
                    UnocssConfigManager.themeEntries.forEach { (k, v) ->
                        val color = parseHexColor(v)
                        val symbol = UnocssThemeConfigCompletionLookupSymbol(v, position.project)
                            .createPointer()
                        result.addElement(
                            LookupElementBuilder
                                .create(symbol, k)
                                .withTypeText("Unocss theme")
                                .withIcon(color?.let { ColorIcon(16, color) } ?: PluginIcon)
                        )
                    }
                }
            }

            CssElementTypes.CSS_IDENT -> {
                val parent = position.parent
                // allow <apply variable> only in declaration key
                if (parent is CssDeclarationImpl) {
                    UnoConfigHelper.defaultApplyVariable.forEach {
                        result.addPriorityElement(
                            LookupElementBuilder
                                .create(it)
                                .withIcon(PluginIcon)
                                .withInsertHandler { insertionCtx, _ ->
                                    insertionCtx.document.insertString(insertionCtx.selectionEndOffset, ": ;")
                                    insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset - 1)
                                }
                        )
                    }
                }

                // allow theme() only in declaration value
                if (parent is CssTermImpl) {
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

                // @screen breakpoints completions
                addBreakpointsLookupElements(position, result)
            }

            CssElementTypes.CSS_ATKEYWORD -> {
                val cssDeclaration = position.parentOfType<CssRulesetImpl>()
                if (cssDeclaration != null) {
                    result.addPriorityElement(
                        LookupElementBuilder
                            .create("apply")
                            .withPresentableText("@apply")
                            .withIcon(PluginIcon)
                            .withInsertHandler { insertionCtx, _ ->
                                insertionCtx.document.insertString(insertionCtx.selectionEndOffset, " ;")
                                insertionCtx.editor.caretModel.moveToOffset(insertionCtx.selectionEndOffset - 1)
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

    private fun addBreakpointsLookupElements(position: PsiElement, result: CompletionResultSet) {
        if (!position.isScreenDirectiveIdent()) {
            return
        }
        val breakpoints = UnocssConfigManager.theme["breakpoints"] ?: return
        if (breakpoints.isJsonObject) {
            val breakpointsObj = breakpoints.asJsonObject
            breakpointsObj.keySet()
                .filter { breakpointsObj[it].isJsonPrimitive }
                .forEach {
                    val propName = it
                    val propValue = breakpointsObj[it].asJsonPrimitive.asString

                    result.addElement(
                        LookupElementBuilder
                            .create(propName)
                            .withIcon(PluginIcon)
                            .withTypeText("Unocss breakpoints")
                            .withTailText(" $propValue ", true)
                    )
                }
        }
    }

    private fun CompletionResultSet.addPriorityElement(
        lookupElement: LookupElementBuilder,
        priority: Double = 1000.0
    ) {
        addElement(PrioritizedLookupElement.withPriority(lookupElement, priority))
    }
}