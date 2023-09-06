package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import me.rerere.unocssintellij.model.UnocssResolveMeta

/**
 * For JSX/TSX Support
 */
class UnocssJsLineMarkerProvider : UnocssHtmlLineMarkerProvider() {
    override fun doGetLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val lineMarkerInfo = super.doGetLineMarkerInfo(element)
        if (lineMarkerInfo != null) return lineMarkerInfo

        val attrValue: String = (if (element is JSLiteralExpression && element.isStringLiteral) {
            // JSLiteralExpression vue v-bind string literals
            element.stringValue
        } else if (element is JSProperty) {
            // JSProperty matched vue v-bind value object keys
            element.firstChild.text
        } else null) ?: return null

        val xmlAttrValueEle = element.parentOfType<XmlAttributeValue>() ?: return null
        val xmlName = xmlAttrValueEle.parent.firstChild.text

        // Both JSLiteralExpression's first child and JSProperty's first child are LeafPsiElement
        return getLineMarkerInfo(UnocssResolveMeta(element.firstChild, xmlName, attrValue))
    }
}