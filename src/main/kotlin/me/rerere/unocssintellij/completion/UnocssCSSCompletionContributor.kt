package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons
import me.rerere.unocssintellij.util.trimCss

class UnocssCSSCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(CssElementTypes.CSS_IDENT),
            UnocssCSSTermListCompletionProvider
        )
    }
}

object UnocssCSSTermListCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        if (!UnocssSettingsState.instance.enable) return
        val element = parameters.position

        val project = element.project
        val service = project.service<UnocssService>()

        val prefix = result.prefixMatcher.prefix

        ApplicationUtil.runWithCheckCanceled({
            val maxItems = UnocssSettingsState.instance.maxItems
            service.getCompletion(parameters.originalFile.virtualFile, prefix, maxItems = maxItems)
        }, ProgressManager.getInstance().progressIndicator).forEach { suggestion ->
            val colors = parseColors(suggestion.css)
            val icon = parseIcons(suggestion.css)
            result.addElement(
                LookupElementBuilder
                    .create(suggestion.className)
                    .withPresentableText(suggestion.className)
                    .withTypeText("Unocss")
                    .withIcon(
                        if (colors.isNotEmpty()) {
                            ColorIcon(16, colors.first())
                        } else if (icon != null) {
                            SVGIcon(icon)
                        } else null
                    )
                    .withTailText(trimCss(suggestion.css), true)
            )
        }

        result.restartCompletionOnAnyPrefixChange()
    }
}