@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.util.appendHighlightedCss
import me.rerere.unocssintellij.util.appendUnocssColorPreview

class UnocssThemeConfigDocumentationTarget(
    private val targetElement: PsiElement?,
) : DocumentationTarget {

    private val themeConfigPath = targetElement?.text?.trim('"', '\'') ?: ""

    override fun computePresentation(): TargetPresentation {
        return TargetPresentation
            .builder("Unocss Document")
            .presentation()
    }

    override fun createPointer() = Pointer.hardPointer(this)

    override fun computeDocumentation(): DocumentationResult {
        return DocumentationResult.asyncDocumentation doc@{
            if (targetElement == null) {
                return@doc null
            }

            val configValue = UnocssConfigManager.getThemeValue(themeConfigPath) ?: return@doc null

            DocumentationResult.documentation(buildString {
                append(DocumentationMarkup.DEFINITION_START)
                appendHighlightedCss(targetElement.project, configValue, formatted = false)
                append(DocumentationMarkup.DEFINITION_END)

                append(DocumentationMarkup.CONTENT_START)
                appendUnocssColorPreview(targetElement.project, configValue)
                append(DocumentationMarkup.CONTENT_END)
            })
        }
    }
}
