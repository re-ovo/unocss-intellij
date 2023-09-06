package me.rerere.unocssintellij.documentation

import com.intellij.lang.javascript.psi.e4x.impl.JSXmlAttributeValueImpl
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.css.impl.CssAtRuleImpl
import com.intellij.psi.css.impl.CssElementType
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.startOffset
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.util.inCssThemeFunction
import me.rerere.unocssintellij.util.inScreenDirective
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.isLeafJsLiteral

private val variantGroupPattern = Regex("(.*[:-])\\((.*)\\)")
private val splitVariantGroupRE = Regex("\\s+(?![^(]*\\))")

private val attributeNameOnlyElementTypes = setOf(
    XmlElementType.XML_NAME,
    CssElementTypes.CSS_IDENT,
    CssElementTypes.CSS_STRING_TOKEN
)

class UnocssDocumentTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): MutableList<out DocumentationTarget> {
        if (!UnocssSettingsState.instance.enable) return mutableListOf()
        val element: PsiElement = file.findElementAt(offset) ?: return mutableListOf()

        val targets = mutableListOf<DocumentationTarget>()
        val elementType = element.elementType
        val meta: UnocssResolveMeta
        // attribute without value
        if (attributeNameOnlyElementTypes.contains(elementType)) {
            val parent = element.parent
            if (parent !is CssAtRuleImpl && parent.lastChild != element) return targets
            meta = UnocssResolveMeta(
                element,
                element.text.trim('"'),
                null,
                elementType !is CssElementType
            )
        } else {
            val attributeValueEle = element.parentOfTypes(
                XmlAttributeValueImpl::class, JSXmlAttributeValueImpl::class
            ) ?: return targets

            val isLiteralValue = elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN || element.isLeafJsLiteral()

            val attributeEle = attributeValueEle.parent
            val attributeNameEle = attributeEle.firstChild

            val offsetValue = getOffsetValue(offset, element, isLiteralValue) ?: return targets
            meta = UnocssResolveMeta(element, attributeNameEle.text, offsetValue)
        }

        resolveCssDocumentation(meta, targets)
        return targets
    }

    private fun resolveCssDocumentation(
        meta: UnocssResolveMeta,
        targets: MutableList<in DocumentationTarget>
    ) {
        val element = meta.bindElement
        when {
            element.inCssThemeFunction() -> {
                targets.add(UnocssThemeConfigDocumentTarget(element))
            }

            element.inScreenDirective() -> {
                targets.add(UnocssThemeScreenDocumentTarget(element))
            }

            else -> {
                val matchResult = meta.resolveCss() ?: return
                if (matchResult.matchedTokens.isNotEmpty()) {
                    targets.add(UnocssDocumentTarget(element, matchResult))
                }
            }
        }
    }

    private fun getOffsetValue(absOffset: Int, element: PsiElement, isLiteralValue: Boolean): String? {
        val rawValue = element.text
        return if (isLiteralValue) {
            val relativeOffset = absOffset - element.startOffset

            val attributeValues = rawValue.split(" ").filter { it.isNotBlank() }
            val rearranged = rearrangeAttrValues(rawValue)

            var startOffset = 0
            var currentValue: String? = null
            for ((index, attributeValue) in attributeValues.withIndex()) {
                if (startOffset + attributeValue.length >= relativeOffset) {
                    currentValue = rearranged[index]
                    break
                }
                startOffset += attributeValue.length + 1
            }
            currentValue
        } else {
            rawValue
        }
    }


    private fun rearrangeAttrValues(rawValue: String): List<String> {
        // split by space, but ignore the space in the variant group
        val rearranged = rawValue.split(splitVariantGroupRE).filter { it.isNotBlank() }
        val result = mutableListOf<String>()

        rearranged.forEach { group ->
            val matched = variantGroupPattern.find(group)
            if (matched != null) {
                // transfer the variant group into "prefix + property"
                matched.groupValues[2]
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .forEach { result.add(matched.groupValues[1] + it) }
            } else {
                result.add(group)
            }
        }

        return result
    }
}
