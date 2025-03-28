package me.rerere.unocssintellij.marker.line

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTokenType
import me.rerere.unocssintellij.model.UnocssResolveMeta

open class UnocssHtmlLineMarkerProvider : UnocssLineMarkerProvider() {
    override fun doGetLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // match with "Valueless" attributify preset
        if (element.elementType == XmlTokenType.XML_NAME) {
            return getFromXmlName(element)
        }
        // match with class value or attributify preset usage
        if (element.elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
            return getFromXmlAttributeValueToken(element)
        }

        return null
    }

    private fun getFromXmlName(element: PsiElement): LineMarkerInfo<*>? {
        // only attribute name
        return if (element.nextSibling == null) {
            getLineMarkerInfo(UnocssResolveMeta(element, element.text, null))
        } else null
    }

    private fun getFromXmlAttributeValueToken(element: PsiElement): LineMarkerInfo<*>? {
        val xmlAttrValueEle = element.parentOfType<XmlAttributeValue>() ?: return null
        val xmlName = xmlAttrValueEle.parent.firstChild.text

        return getLineMarkerInfo(UnocssResolveMeta(element, xmlName, element.text))
    }
}