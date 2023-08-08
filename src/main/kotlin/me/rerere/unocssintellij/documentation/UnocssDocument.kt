package me.rerere.unocssintellij.documentation

import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.javascript.psi.e4x.impl.JSXmlAttributeValueImpl
import com.intellij.model.Pointer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
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
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.createSmartPointer
import com.intellij.refactoring.suggested.startOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.rpc.ResolveCSSResult
import me.rerere.unocssintellij.util.buildDummyDivTag
import me.rerere.unocssintellij.util.parseColors
import me.rerere.unocssintellij.util.toHex

private val variantGroupPattern = Regex("(.*[:-])\\((.*)\\)")
private val splitVariantGroupRE = Regex("\\s+(?![^(]*\\))")

class UnocssDocumentTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): MutableList<out DocumentationTarget> {
        val element: PsiElement = file.findElementAt(offset) ?: return mutableListOf()

        val targets = mutableListOf<UnocssDocumentTarget>()
        val service = file.project.service<UnocssService>()

        val attribute: Pair<String, String?>
        val elementType = element.elementType
        // attribute without value
        if (elementType == XmlElementType.XML_NAME || elementType == CssElementTypes.CSS_IDENT) {
            if (element.parent.lastChild != element) return targets
            attribute = element.text to null
        } else {
            val attributeValueEle = PsiTreeUtil.getParentOfType(
                element,
                XmlAttributeValueImpl::class.java, JSXmlAttributeValueImpl::class.java
            ) ?: return targets

            val isLiteralValue = elementType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN

            val attributeEle = attributeValueEle.parent
            val attributeNameEle = attributeEle.firstChild

            val offsetValue = getOffsetValue(offset, element, isLiteralValue) ?: return targets
            attribute = attributeNameEle.text to offsetValue
        }

        resolveCssDocumentation(element, attribute, file, service, targets)
        return targets
    }

    private fun resolveCssDocumentation(
        element: PsiElement,
        attribute: Pair<String, String?>,
        file: PsiFile,
        service: UnocssService,
        targets: MutableList<UnocssDocumentTarget>
    ) {
        val matchResult = runBlocking {
            withTimeoutOrNull(100) {
                service.resolveCss(file.virtualFile, buildDummyDivTag(attribute))
            }
        } ?: return

        if (matchResult.matchedTokens.isNotEmpty()) {
            targets.add(UnocssDocumentTarget(element, matchResult))
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
            .createFileFromText(CSSLanguage.INSTANCE, result.css)
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
}