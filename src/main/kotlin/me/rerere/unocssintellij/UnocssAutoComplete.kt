package me.rerere.unocssintellij

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.xml.XmlTokenImpl
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.HtmlFileElementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlToken
import com.intellij.util.ProcessingContext

class UnocssAutoComplete : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        super.fillCompletionVariants(parameters, result)

        val position = parameters.position
        val project = position.project ?: return
        val service = project.service<UnocssService>()

        if (position is XmlToken && position.tokenType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            println(result.prefixMatcher.toString())

            println("start")
            val r = ApplicationUtil.runWithCheckCanceled({
                service.getCompletion(parameters.originalFile.virtualFile, result.prefixMatcher.prefix)
            }, ProgressManager.getInstance().progressIndicator)
            r.forEach {
                result.addElement(LookupElementBuilder.create(it))
            }
            println("added")
            result.restartCompletionOnAnyPrefixChange()
            println("done")
        }
    }
}