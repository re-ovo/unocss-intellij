package me.rerere.unocssintellij.references.classname

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.findUnoConfigFile
import me.rerere.unocssintellij.util.getMatchedPositions

class UnocssClassNameReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns
                .psiElement(XmlAttributeValue::class.java),
            UnocssClassNameReferenceProvider
        )
    }
}

object UnocssClassNameReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (!UnocssSettingsState.instance.enable) {
            return PsiReference.EMPTY_ARRAY
        }
        val text = element.text
        val service = element.project.service<UnocssService>()
        val matched = runBlocking {
            withTimeout(1000) {
                service.resolveAnnotations(element.containingFile.virtualFile, text)
            }
        } ?: return PsiReference.EMPTY_ARRAY
        val matchedPositions = getMatchedPositions(text, matched)
        if (matchedPositions.isEmpty()) {
            return PsiReference.EMPTY_ARRAY
        }
        return matchedPositions.map {
            UnocssClassNameReference(element, TextRange(it.start, it.end))
        }.toTypedArray()
    }
}

class UnocssClassNameReference(element: PsiElement, range: TextRange) :
    PsiReferenceBase<PsiElement>(element, range, true) {
    override fun resolve(): PsiElement? {
        return element.project.findUnoConfigFile()
    }

//    private fun findUnoCSSProviderInConfig(configFile: PsiFile): PsiElement? {
//        val properties = configFile.childrenOfTypeDeeply<JSProperty>()
//        val presetsProperty = properties.find { it.name == "presets" }
//        val rulesProperty = properties.find { it.name == "rules" }
//        val shortcutsProperty = properties.find { it.name == "shortcuts" }
//
//        // find shortcuts first
//        shortcutsProperty?.childrenOfTypeDeeply<JSLiteralExpression>()?.forEach { expr ->
//            println(expr.nextSibling)
//            println(expr.nextSibling.nextSibling)
//        }
//
//        return null
//    }
}