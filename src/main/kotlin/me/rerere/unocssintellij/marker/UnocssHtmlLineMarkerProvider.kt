package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementType

class UnocssHtmlLineMarkerProvider : UnocssLineMarkerProvider() {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // match with "Valueless" attributify preset
        if (element.elementType == XmlElementType.XML_NAME) {
            return getFromXmlName(element)
        }
        // match with class value or attributify preset usage
        if (element.elementType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return getFromXmlAttributeValueToken(element)
        }

        return null
    }

    private fun getFromXmlName(element: PsiElement): LineMarkerInfo<*>? {
        // only attribute name
        return if (element.nextSibling == null) {
             getLineMarkerInfo(UnocssLineMarkerMeta(element, element.text, null))
        } else null
    }

    private fun getFromXmlAttributeValueToken(element: PsiElement): LineMarkerInfo<*>? {
        val xmlAttrValueEle = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java) ?: return null
        val xmlName = xmlAttrValueEle.parent.firstChild.text

        return getLineMarkerInfo(UnocssLineMarkerMeta(element, xmlName, element.text))
    }
}