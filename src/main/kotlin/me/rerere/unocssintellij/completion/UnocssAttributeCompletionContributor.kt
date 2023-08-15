package me.rerere.unocssintellij.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElementType
import com.intellij.refactoring.suggested.startOffset
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
    }
}

object UnocssAttributeCompletionProvider : UnocssCompletionProvider() {

    override fun resolvePrefix(parameters: CompletionParameters, result: CompletionResultSet): PrefixHolder? {
        val element = parameters.position

        val xmlAttrName = if (element.elementType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            val xmlAttributeEle = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
                ?: return null
            xmlAttributeEle.firstChild.text
        } else ""

        // Intellij CSS plugin seems to intervene class value prefix match,
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
        if (!isClassAttribute(attrName)) {
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

        val typingPrefix: String
        val prefixToSuggest: String
        // no variant group or no need to complete variant group
        if (lastLeftBracket < 0 || lastLeftBracket < lastRightBracket) {
            typingPrefix = prefix.split(" ").last().trim()
            prefixToSuggest = typingPrefix

            // ignore completion when typing prefix is blank
            if (typingPrefix.isBlank()) {
                return null
            }
        } else {
            val matchResult = matchVariantGroupPrefixRE.find(prefix) ?: return null
            val variants = matchResult.groupValues[1]
            // use last().trim() to help manually trigger code completion (e.g. via keymap)
            // the variant group will be used as prefix to query suggestions
            val token = matchResult.groupValues[2].split(" ").last().trim()

            typingPrefix = token
            prefixToSuggest = "${variants.substring(variants.lastIndexOf(":") + 1)}$token"
        }

        return PrefixHolder(typingPrefix, prefixToSuggest)
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
