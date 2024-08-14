package me.rerere.unocssintellij.inspection

import com.intellij.codeInspection.XmlSuppressionProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import com.intellij.psi.xml.XmlElementType
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.model.UnocssResolveMeta

/**
 * For suppressing unknown attribute inspection
 */
class UnocssXmlSuppressionProvider : XmlSuppressionProvider() {

    companion object {
        private const val HTML_UNKNOWN_ATTRIBUTE = "HtmlUnknownAttribute"
        private const val HTML_UNKNOWN_TAG = "HtmlUnknownTag"
        private const val ESLINT = "Eslint"
    }

    override fun isProviderAvailable(file: PsiFile) = true

    override fun suppressForFile(element: PsiElement, inspectionId: String) {
    }

    override fun suppressForTag(element: PsiElement, inspectionId: String) {
    }

    override fun isSuppressedFor(element: PsiElement, inspectionId: String): Boolean {
        if (element.elementType == XmlElementType.XML_NAME
            && (inspectionId == HTML_UNKNOWN_ATTRIBUTE || inspectionId == ESLINT)) {
            return suppressForUnknownAttribute(element)
        }

        if (UnocssConfigManager.hasPreset(UnocssConfigManager.Presets.TAGIFY)
            && element.elementType == XmlElementType.XML_NAME
            && inspectionId == HTML_UNKNOWN_TAG) {
            return suppressForUnknownTag(element)
        }

        return false
    }

    private fun suppressForUnknownTag(tagElement: PsiElement): Boolean {
        val tagName = tagElement.text
        val matchResult = UnocssResolveMeta(tagElement, tagName, null, false).resolveCss()
            ?: return false

        return matchResult.matchedTokens.isNotEmpty()
    }

    private fun suppressForUnknownAttribute(attrElement: PsiElement): Boolean {
        val onlyAttributeName = attrElement.nextSibling == null

        val attrName = attrElement.text
        val attrValue = if (onlyAttributeName) null else {
            // XML_EQ -> XML_ATTRIBUTE_VALUE
            attrElement.siblings()
                .firstOrNull { it.elementType == XmlElementType.XML_ATTRIBUTE_VALUE }
                ?.text?.trim('"', '{', '}')
        }

        val matchResult = UnocssResolveMeta(attrElement, attrName, attrValue, true).resolveCss()
            ?: return false

        return if (onlyAttributeName) {
            matchResult.matchedTokens.contains(attributeNameOnlyMatchCandidate(attrName))
        } else {
            matchResult.matchedTokens.isNotEmpty()
        }
    }

    private fun attributeNameOnlyMatchCandidate(attributeName: String) =
        if (attributeName.indexOf('[') > 0) attributeName else "[$attributeName=\"\"]"
}