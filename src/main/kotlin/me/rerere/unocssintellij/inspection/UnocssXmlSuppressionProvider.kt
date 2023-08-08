package me.rerere.unocssintellij.inspection

import com.intellij.codeInspection.XmlSuppressionProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.util.buildDummyDivTag

/**
 * For suppressing unknown attribute inspection
 */
class UnocssXmlSuppressionProvider : XmlSuppressionProvider() {

    companion object {
        private const val HTML_UNKNOWN_ATTRIBUTE = "HtmlUnknownAttribute"
    }

    override fun isProviderAvailable(file: PsiFile) = true

    override fun suppressForFile(element: PsiElement, inspectionId: String) {
    }

    override fun suppressForTag(element: PsiElement, inspectionId: String) {
    }

    override fun isSuppressedFor(element: PsiElement, inspectionId: String): Boolean {
        if (element.elementType == XmlElementType.XML_NAME && inspectionId == HTML_UNKNOWN_ATTRIBUTE) {
            return suppressForUnknownAttribute(element)
        }

        return false
    }

    private fun suppressForUnknownAttribute(attrElement: PsiElement): Boolean {
        val onlyAttributeName = attrElement.nextSibling == null

        val attrName = attrElement.text
        val attrValue = if (onlyAttributeName) null else {
            // XML_EQ -> XML_ATTRIBUTE_VALUE
            attrElement.nextSibling?.nextSibling?.text?.trim('"', '{', '}', '[', ']')
        }

        val matchResult = runBlocking {
            withTimeoutOrNull(100) {
                attrElement.project.service<UnocssService>().resolveCss(
                    attrElement.containingFile.virtualFile,
                    buildDummyDivTag(attrName to attrValue)
                )
            }
        } ?: return false

        return if (onlyAttributeName) {
            matchResult.matchedTokens.contains(attributeNameOnlyMatchCandidate(attrName))
        } else {
            matchResult.matchedTokens.isNotEmpty()
        }
    }

    private fun attributeNameOnlyMatchCandidate(attributeName: String) = "[$attributeName=\"\"]"
}