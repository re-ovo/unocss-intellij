package me.rerere.unocssintellij.marker

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue

/**
 * For JSX/TSX Support
 */
class UnocssJsLineMarkerProvider : UnocssLineMarkerProvider() {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {

        val attrValue: String = (if (element is JSLiteralExpression && element.isStringLiteral) {
            // JSLiteralExpression vue v-bind string literals
            element.stringValue
        } else if (element is JSProperty) {
            // JSProperty matched vue v-bind value object keys
            element.firstChild.text
        } else null) ?: return null

        val xmlAttrValueEle = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java) ?: return null
        val xmlName = xmlAttrValueEle.parent.firstChild.text

        // Both JSLiteralExpression's first child and JSProperty's first child are LeafPsiElement
        return getLineMarkerInfo(UnocssLineMarkerMeta(element.firstChild, xmlName, attrValue))
    }
}