package me.rerere.unocssintellij.documentation

import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.javascript.psi.e4x.impl.JSXmlAttributeValueImpl
import com.intellij.model.Pointer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.css.impl.CssElementType
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.refactoring.suggested.startOffset
import me.rerere.unocssintellij.model.UnocssResolveMeta
import me.rerere.unocssintellij.rpc.ResolveCSSResult
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.toHex

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

        val targets = mutableListOf<UnocssDocumentTarget>()
        val elementType = element.elementType
        val meta: UnocssResolveMeta
        // attribute without value
        if (attributeNameOnlyElementTypes.contains(elementType)) {
            if (element.parent.lastChild != element) return targets
            meta = UnocssResolveMeta(
                element,
                element.text.trim('"'),
                null,
                elementType !is CssElementType
            )
        } else {
            val attributeValueEle = PsiTreeUtil.getParentOfType(
                element,
                XmlAttributeValueImpl::class.java, JSXmlAttributeValueImpl::class.java
            ) ?: return targets

            val isLiteralValue = elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN

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
        targets: MutableList<UnocssDocumentTarget>
    ) {
        val matchResult = meta.resolveCss() ?: return

        if (matchResult.matchedTokens.isNotEmpty()) {
            targets.add(UnocssDocumentTarget(meta.bindElement, matchResult))
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

private val remRE = Regex("-?[\\d.]+rem;")

class UnocssDocumentTarget(
    private val targetElement: PsiElement?,
    private val result: ResolveCSSResult,
) : DocumentationTarget {

    override fun computePresentation(): TargetPresentation {
        return TargetPresentation
            .builder("Unocss Document")
            .presentation()
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = targetElement?.createSmartPointer()
        return Pointer {
            UnocssDocumentTarget(pointer?.dereference(), result)
        }
    }

    override fun computeDocumentation(): DocumentationResult {
        val cssFile: PsiFile = PsiFileFactory.getInstance(targetElement?.project)
            .createFileFromText(CSSLanguage.INSTANCE, resolveRemToPx(result.css))
        return DocumentationResult.Companion.asyncDocumentation {
            // Format the css
            WriteCommandAction.runWriteCommandAction(cssFile.project) {
                val doc = PsiDocumentManager.getInstance(cssFile.project)
                    .getDocument(cssFile) ?: return@runWriteCommandAction
                PsiDocumentManager.getInstance(cssFile.project).doPostponedOperationsAndUnblockDocument(doc)
                CodeStyleManager.getInstance(cssFile.project)
                    .reformatText(cssFile, 0, cssFile.textLength)
            }

            DocumentationResult.documentation(buildString {
                append(DocumentationMarkup.DEFINITION_START)
                append("<code>")
                runReadAction {
                    HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                        this,
                        cssFile.project,
                        CSSLanguage.INSTANCE,
                        cssFile.text,
                        DocumentationSettings.getHighlightingSaturation(true)
                    )
                }
                append("</code>")
                append(DocumentationMarkup.DEFINITION_END)

                append(DocumentationMarkup.CONTENT_START)
                val colors = parseColors(result.css)
                if (colors.isNotEmpty()) {
                    val color = colors.first().toHex()
                    val style = "display: inline-block; height: 16px; width: 16px; background-color: $color"
                    append("<div style=\"$style\"></div>")
                }

                append("Generated by Unocss")
                append(DocumentationMarkup.CONTENT_END)
            })
        }
    }

    private fun resolveRemToPx(css: String): String {
        val settingsState = UnocssSettingsState.instance
        if (css.isBlank()) return css

        val remToPxRatio = if (settingsState.remToPxPreview) {
            settingsState.remToPxRatio
        } else {
            -1.0
        }

        if (remToPxRatio < 1) return css
        var index = 0
        val output = StringBuilder()
        while (index < css.length) {
            val rem = remRE.find(css.substring(index)) ?: break
            val px = """ /* ${rem.value.substring(0, rem.value.length - 4).toFloat() * remToPxRatio}px */"""
            val end = index + rem.range.first + rem.value.length
            output.append(css.substring(index, end))
            output.append(px)
            index = end
        }
        output.append(css.substring(index))
        return output.toString()
    }
}