package me.rerere.unocssintellij.documentation

import com.intellij.lang.javascript.psi.e4x.impl.JSXmlAttributeValueImpl
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.css.impl.CssAtRuleImpl
import com.intellij.psi.css.impl.CssElementType
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfTypes
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.startOffset
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.attributeNameOnlyElementTypes
import me.rerere.unocssintellij.util.inCssThemeFunction
import me.rerere.unocssintellij.util.isLeafJsLiteral
import me.rerere.unocssintellij.util.isScreenDirectiveIdent
import me.rerere.unocssintellij.util.isUnocssCandidate

private val classNameRE = Regex("""\w+:\([^)]+\)|\S+""")
private val variantNameRE = Regex("""(\w+):\(([^)]+)\)""")

class UnocssDocumentTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): MutableList<out DocumentationTarget> {
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
                XmlAttributeValueImpl::class,
                JSXmlAttributeValueImpl::class,
            )
            if (attributeValueEle != null) { // attribute with value
                val isLiteralValue = elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN || element.isLeafJsLiteral()

                val attributeEle = attributeValueEle.parent
                val attributeNameEle = attributeEle.firstChild

                val offsetValue = getOffsetValue(offset, element, isLiteralValue) ?: return targets
                meta = UnocssResolveMeta(element, attributeNameEle.text, offsetValue)
            } else if (element.isLeafJsLiteral()) { // js literal
                val settingsState = UnocssSettingsState.of(file.project)
                if (settingsState.isMatchedJsLiteral(element)) {
                    val offsetValue = getOffsetValue(offset, element, true) ?: return targets
                    meta = UnocssResolveMeta(element, offsetValue)
                } else {
                    return targets
                }
            } else {
                // Still not match, return empty list
                return targets
            }
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
                targets.add(UnocssThemeConfigDocumentationTarget(element))
            }

            element.isScreenDirectiveIdent() -> {
                targets.add(UnocssThemeScreenDocumentationTarget(element))
            }

            element.isUnocssCandidate() -> {
                val matchResult = meta.resolveCss() ?: return
                if (matchResult.matchedTokens.isNotEmpty()) {
                    targets.add(UnocssAttributeDocumentationTarget(element, matchResult))
                }
            }
        }
    }

    private fun getOffsetValue(absOffset: Int, element: PsiElement, isLiteralValue: Boolean): String? {
        val rawValue = element.text
        return if (isLiteralValue) {
            val relativeOffset = absOffset - element.startOffset

            // 匹配所有class names
            val matches = classNameRE.findAll(rawValue)

            for (match in matches) {
                if (relativeOffset in match.range) {
                    val variantGroup = variantNameRE.find(match.value)
                    if (variantGroup == null) { // normal class name
                        return match.value
                    } else { // variant group
                        val classOffset = relativeOffset - match.range.first
                        val (variantName, variantValue) = variantGroup.destructured
                        val valueOffset = classOffset - variantName.length - 2

                        // hover on variant name or bracket
                        if (classOffset <= variantName.length + 1 || classOffset >= match.value.length - 1) {
                            return match.value
                        }

                        val groupMatches = classNameRE.findAll(variantValue)
                        for (groupMatch in groupMatches) {
                            if (valueOffset in groupMatch.range) {
                                return variantName + ":" + groupMatch.value
                            }
                        }
                    }
                }
            }

            // Still not match, maybe a space?

            rawValue
        } else {
            rawValue
        }
    }

}
