package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.documentation.UnocssCompletionLookupSymbol
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.rpc.SuggestionItem
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons

data class PrefixHolder(
    /**
     * Typing prefix is the prefix that user is typing, used to let intellij to match with suggestion
     */
    val typingPrefix: String,
    /**
     * Prefix to suggest is the prefix that will be used to query suggestions
     * usually has the same value as typing prefix, but
     *
     * - when using variant group, it will be added with variant prefix
     * - when using attributify presets, it will be added with html attribute name as prefix
     *
     * It will be fine to ignore variant prefix like `hover:` when querying suggestions
     */
    val prefixToSuggest: String
) {
    constructor(prefix: String) : this(prefix, prefix)
}

internal val PluginIcon by lazy {
    // Read icon from resources/META-INF/pluginIcon.svg
    val stream = UnocssCompletionProvider::class.java.classLoader
        .getResourceAsStream("META-INF/pluginIcon.svg")
    stream.use {
        it?.let { it1 -> SVGIcon.fromStream(it1, 16) }
    }
}

abstract class UnocssCompletionProvider : CompletionProvider<CompletionParameters>() {

    abstract fun resolvePrefix(parameters: CompletionParameters, result: CompletionResultSet): PrefixHolder?

    open fun resolveSuggestionClassName(
        typingPrefix: String,
        prefixToSuggest: String,
        suggestion: SuggestionItem
    ): String = suggestion.className

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.originalFile.project
        val settingsState = UnocssSettingsState.of(project)
        if (shouldSkip(parameters.position)) {
            return
        }

        val (typingPrefix, prefixToSuggest) = resolvePrefix(parameters, result) ?: return
        val completionResult = result.withPrefixMatcher(typingPrefix)

        val service = project.service<UnocssService>()
        runBlockingCancellable {
            service.getCompletion(
                ctx = parameters.originalFile.virtualFile,
                prefix = prefixToSuggest,
                maxItems = settingsState.maxItems
            )
        }.forEach { suggestion ->

            val className = resolveSuggestionClassName(typingPrefix, prefixToSuggest, suggestion)

            val colors = parseColors(suggestion.css)
            val icon = parseIcons(suggestion.css)

            val lookupObj = UnocssCompletionLookupSymbol(suggestion, project)
                .createPointer()
            var lookupElementBuilder = LookupElementBuilder
                .create(lookupObj, className)
                .withPresentableText(className)
                .withIcon(
                    if (colors.isNotEmpty()) {
                        ColorIcon(16, colors.first())
                    } else if (icon != null) {
                        SVGIcon.tryGetIcon(icon, 20).getOrNull()
                    } else PluginIcon
                )

            lookupElementBuilder = customizeLookupElement(lookupElementBuilder, typingPrefix, className, parameters.position)

            completionResult.addElement(lookupElementBuilder)
        }

        result.restartCompletionOnAnyPrefixChange()
    }

    protected open fun customizeLookupElement(lookupElement: LookupElementBuilder,
                                              typingPrefix: String,
                                              className: String,
                                              position: PsiElement): LookupElementBuilder {
        return lookupElement
    }

    protected open fun shouldSkip(position: PsiElement): Boolean {
        return false
    }
}

// Make auto popup work when typing '-'
// https://github.com/JetBrains/intellij-community/blob/4d5322af326084873e16cfafd0239a1713a52adc/plugins/terminal/src/org/jetbrains/plugins/terminal/exp/TerminalCompletionAutoPopupHandler.kt#L19
class TypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (!project.service<UnocssService>().isProcessRunning()) return Result.CONTINUE

        if (Character.isLetterOrDigit(charTyped) || charTyped == '-') {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            return Result.STOP
        }

        return Result.CONTINUE
    }
}