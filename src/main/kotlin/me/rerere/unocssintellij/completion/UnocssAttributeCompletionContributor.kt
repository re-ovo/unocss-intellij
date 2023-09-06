package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.rpc.SuggestionItem
import me.rerere.unocssintellij.util.isClassAttribute

private val matchVariantGroupPrefixRE = Regex("^(?:.*\\s)?(.*)\\(([^)]*)$")

class UnocssAttributeCompletionContributor : CompletionContributor() {
    init {
        // match attribute value
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN),
            UnocssAttributeCompletionProvider
        )
        // match attribute name when using attributify presets
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(XmlElementType.XML_NAME),
            UnocssAttributeCompletionProvider
        )
        // match v-bind string value
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(JSLiteralExpression::class.java),
            UnocssJsLiteralCompletionProvider
        )
        // match v-bind object value
        // fixme: not working when typing, workaround: manually trigger code completion (e.g. via keymap)
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(LeafPsiElement::class.java)
                .withParent(JSProperty::class.java),
            UnocssJsLiteralCompletionProvider
        )
    }
}

object UnocssAttributeCompletionProvider : UnocssCompletionProvider() {

    private val skipTagNames = setOf("script", "style", "template")

    override fun shouldSkip(position: PsiElement): Boolean {
        // If user has not installed attributify preset, skip
        if (!UnocssConfigManager.hasPresetAttributify && position.elementType == XmlElementType.XML_NAME) {
            return true
        }

        val xmlTag = position.parentOfType<XmlTag>() ?: return false
        return xmlTag.name in skipTagNames
    }

    override fun resolvePrefix(parameters: CompletionParameters, result: CompletionResultSet): PrefixHolder? {
        val element = parameters.position

        val xmlAttrName = if (element.elementType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            val xmlAttributeEle = element.parentOfType<XmlAttribute>(true)
                ?: return null
            xmlAttributeEle.firstChild.text
        } else ""

        // The value of prefixMatcher.prefix seems like truncated by space by default,
        // so we may use the substring of element.text to keep the behavior of
        // class value and other attribute values consistent
        val prefixToResolve = element.text.substring(0, parameters.offset - element.startOffset)
        return resolvePrefix(element, prefixToResolve, xmlAttrName)
    }

    private fun resolvePrefix(element: PsiElement, prefix: String, attrName: String): PrefixHolder? {
        if (element.elementType != XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return PrefixHolder(prefix)
        }

        // attributify value
        if (!attrName.isClassAttribute()) {
            // use last().trim() to help manually trigger code completion (e.g. via keymap)
            // the attribute name will be used as prefix to query suggestions
            val typingPrefix = prefix.split(" ").last().trim()
            val removeVariantPrefix = typingPrefix.substring(typingPrefix.lastIndexOf(":") + 1)
            val prefixToSuggest = "$attrName-$removeVariantPrefix"
            return PrefixHolder(removeVariantPrefix, prefixToSuggest)
        }

        // class value

        val lastLeftBracket = prefix.lastIndexOf('(')
        val lastRightBracket = prefix.lastIndexOf(')')

        // no variant group or no need to complete variant group
        return if (lastLeftBracket < 0 || lastLeftBracket < lastRightBracket) {
            val typingPrefix = prefix.split(" ").last().trim()

            // ignore completion when typing prefix is blank
            if (typingPrefix.isBlank()) null
            else PrefixHolder(typingPrefix)
        } else {
            extractFromVariantGroup(prefix)
        }
    }

    override fun resolveSuggestionClassName(
        typingPrefix: String,
        prefixToSuggest: String,
        suggestion: SuggestionItem
    ) = if (typingPrefix != prefixToSuggest) {
        suggestion.className.substring(prefixToSuggest.length - typingPrefix.length)
    } else {
        suggestion.className
    }
}

object UnocssJsLiteralCompletionProvider : UnocssCompletionProvider() {
    override fun resolvePrefix(parameters: CompletionParameters, result: CompletionResultSet): PrefixHolder? {
        val element = parameters.position
        val xmlAttributeEle = element.parentOfType<XmlAttribute>(true)
            ?: return null
        val attrName = xmlAttributeEle.firstChild.text

        // Is anyone use unocss token on other attribute name?
        if (!attrName.isClassAttribute()) {
            return null
        }

        // The value of prefixMatcher.prefix seems like truncated by space by default,
        // so we may use the substring of element.text to keep the behavior of
        // class value and other attribute values consistent
        val prefixToResolve = element.text.substring(0, parameters.offset - element.startOffset)
            .trim('\'', '"')

        // we select the leaf PsiElement with parent JSLiteralExpression,
        // so we can simply treat the prefix as the whole text of the element
        return if (!prefixToResolve.contains("(")) {
            PrefixHolder(prefixToResolve.split(" ").last().trim())
        } else {
            extractFromVariantGroup(prefixToResolve)
        }
    }

    override fun resolveSuggestionClassName(
        typingPrefix: String,
        prefixToSuggest: String,
        suggestion: SuggestionItem
    ) = if (typingPrefix != prefixToSuggest) {
        suggestion.className.substring(prefixToSuggest.length - typingPrefix.length)
    } else {
        suggestion.className
    }
}

private fun extractFromVariantGroup(prefixToResolve: String): PrefixHolder? {
    val matchResult = matchVariantGroupPrefixRE.find(prefixToResolve) ?: return null
    val variants = matchResult.groupValues[1]
    // use last().trim() to help manually trigger code completion (e.g. via keymap)
    // the variant group will be used as prefix to query suggestions
    val token = matchResult.groupValues[2].split(" ").last().trim()

    val prefixToSuggest = "${variants.substring(variants.lastIndexOf(":") + 1)}$token"
    return PrefixHolder(token, prefixToSuggest)
}