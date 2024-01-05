package me.rerere.unocssintellij.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElementType

fun createXmlAttributeValueToken(project: Project, value: String): PsiElement? {
    val attribute = XmlElementFactory
        .getInstance(project)
        .createXmlAttribute("class", value.trim('"'))
    val attributeValue = attribute.valueElement as XmlAttributeValue
    return attributeValue.children.firstOrNull {
        it.elementType == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN
    }
}