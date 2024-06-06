package me.rerere.unocssintellij.references.classname

import com.intellij.lang.javascript.psi.JSArrayLiteralExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSLiteralExpressionKind
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.util.childrenOfTypeDeeply
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
            UnocssClassNameReference(element, TextRange(it.start, it.end), it.text)
        }.toTypedArray()
    }
}

class UnocssClassNameReference(element: PsiElement, range: TextRange, private val className: String) :
    PsiReferenceBase<PsiElement>(element, range, true) {
    override fun resolve(): PsiElement? {
        return findUnoCSSProviderInConfig(element.project.findUnoConfigFile() ?: return null)
    }

    private fun findUnoCSSProviderInConfig(configFile: PsiFile): PsiElement? {
        val properties = configFile.childrenOfTypeDeeply<JSProperty>()
        val presetsProperty = properties.find { it.name == "presets" }
        val rulesProperty = properties.find { it.name == "rules" }
        val shortcutsProperty = properties.find { it.name == "shortcuts" }

        // find shortcuts first
        if (shortcutsProperty != null) {
            val shortcuts = findUnoCSSRuleProviderInShortcuts(shortcutsProperty)
            if (shortcuts != null) {
                return shortcuts
            }
        }

        // find user defined rules
        if (rulesProperty != null) {
            val rules = findUnoCSSRuleProviderInRules(rulesProperty)
            if (rules != null) {
                return rules
            }
        }

        // find presets
        if (presetsProperty != null) {
            return presetsProperty
        }

        return null
    }

    private fun findUnoCSSRuleProviderInRules(rulesProperty: JSProperty): PsiElement? {
        val valueElement = rulesProperty.value?.let {
            if (it is JSReferenceExpression) {
                it.resolve()
            } else {
                it
            }
        } ?: return null

        // Find [/^m-(\d+)$/, ([, d]) => ({margin: `${d / 4}rem`})],
        valueElement.childrenOfTypeDeeply<JSArrayLiteralExpression>()
            .filter {
                it.expressions.size == 2 && it.expressions[0] is JSLiteralExpression
            }
            .forEach {
                val literalExpression = (it.expressions[0] as JSLiteralExpression)
                val kind = literalExpression.getExpressionKind(false)
                if (kind == JSLiteralExpressionKind.REGEXP) {
                    val regexElement = literalExpression.firstChild
                    val regex = runCatching { Regex(regexElement.text.trim('/')) }.getOrNull() ?: return@forEach
                    if (regex.matches(className)) {
                        return it
                    }
                } else if (kind == JSLiteralExpressionKind.QUOTED) {
                    val stringElement = literalExpression.firstChild
                    val string = stringElement.text.trim('"').trim('\'')
                    if (string == className) {
                        return it
                    }
                }
            }
        return null
    }

    private fun findUnoCSSRuleProviderInShortcuts(shortcuts: JSProperty): PsiElement? {
        val valueElement = shortcuts.value?.let {
            if (it is JSReferenceExpression) {
                it.resolve()
            } else {
                it
            }
        } ?: return null

        // Find the class name in shortcuts
        // Like 'btn': 'py-2 px-4 font-semibold rounded-lg shadow-md',
        valueElement.childrenOfTypeDeeply<JSProperty>().forEach {
            if (it.name == className) {
                return it
            }
        }

        // Find the dynamic class name in shortcuts
        // Like [/^btn-(.*)$/, ([, c]) => `bg-${c}-400 text-${c}-100 py-2 px-4 rounded-lg`],
        valueElement
            .childrenOfTypeDeeply<JSArrayLiteralExpression>()
            .filter { it.expressions.size == 2 && it.expressions[0] is JSLiteralExpression }
            .forEach {
                val literalExpression = (it.expressions[0] as JSLiteralExpression)
                val kind = literalExpression.getExpressionKind(false)
                if (kind == JSLiteralExpressionKind.REGEXP) {
                    val regexElement = literalExpression.firstChild
                    val regex = runCatching { Regex(regexElement.text.trim('/')) }.getOrNull() ?: return@forEach
                    if (regex.matches(className)) {
                        return it
                    }
                }
            }

        return null
    }
}