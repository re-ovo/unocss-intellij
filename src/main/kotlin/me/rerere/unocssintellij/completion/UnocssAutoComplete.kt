package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.diff.comparison.trim
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlToken
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ProcessingContext
import com.intellij.util.ResourceUtil
import com.intellij.util.ui.ColorIcon
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.lang.psi.UnocssTypes
import me.rerere.unocssintellij.parser.parseColors
import me.rerere.unocssintellij.parser.trimCss

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
            result.addElement(
                LookupElementBuilder
                    .create(suggestion.className)
                    .withTypeText("Unocss")
                    .withIcon(
                        if (colors.isNotEmpty()) {
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