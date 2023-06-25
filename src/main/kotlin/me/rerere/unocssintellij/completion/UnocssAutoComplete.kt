package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.diff.comparison.trim
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlToken
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.parser.parseColors
import me.rerere.unocssintellij.parser.trimCss

class UnocssAutoComplete : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            XmlPatterns.psiElement(XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN),
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

        if (position is XmlToken && position.parent is XmlAttributeValue) {
            //val attributeValue = position.parent as XmlAttributeValue
            val prefix = result.prefixMatcher.prefix
            //println("prefix: $prefix")
            ApplicationUtil.runWithCheckCanceled({
                service.getCompletion(
                    parameters.originalFile.virtualFile,
                    prefix,
                    prefix.length
                )
            }, ProgressManager.getInstance().progressIndicator).forEach { suggestion ->
                val colors = parseColors(suggestion.css)
                result.addElement(
                    LookupElementBuilder
                        .create(suggestion.className)
                        .withTypeText("Unocss")
                        .withIcon(
                            if(colors.isNotEmpty()) {
                                ColorIcon(16, colors.first())
                            } else {
                                null
                            }
                        )
                        .withTailText(trimCss(suggestion.css), true)
                )
            }

            result.restartCompletionOnAnyPrefixChange()
        }
    }
}