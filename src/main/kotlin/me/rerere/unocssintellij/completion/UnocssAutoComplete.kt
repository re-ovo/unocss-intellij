package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.lang.psi.UnocssTypes
import me.rerere.unocssintellij.marker.SVGIcon
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.parseIcons
import me.rerere.unocssintellij.util.trimCss

class UnocssAutoComplete : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns
                .psiElement(UnocssTypes.CLASSNAME),
            UnocssCompletionProvider()
        )
    }
}

class UnocssCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val project = parameters.position.project
        val service = project.service<UnocssService>()
        val position = parameters.position

        val prefix = result.prefixMatcher.prefix
        val file = parameters.originalFile.virtualFile

        ApplicationUtil.runWithCheckCanceled({
            service.getCompletion(
                file,
                prefix,
                prefix.length
            )
        }, ProgressManager.getInstance().progressIndicator).forEach { suggestion ->
            val colors = parseColors(suggestion.css)
            val icon = parseIcons(suggestion.css)
            result.addElement(
                LookupElementBuilder
                    .create(suggestion.className)
                    .withTypeText("Unocss")
                    .withIcon(
                        if (colors.isNotEmpty()) {
                            ColorIcon(16, colors.first())
                        } else if(icon != null ){
                            SVGIcon(icon)
                        } else null
                    )
                    .withTailText(trimCss(suggestion.css), true)
            )
        }

        result.restartCompletionOnAnyPrefixChange()
    }
}