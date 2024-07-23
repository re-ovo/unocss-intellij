@file:Suppress("UnstableApiUsage")

package me.rerere.unocssintellij.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.createSmartPointer
import me.rerere.unocssintellij.UnocssConfigManager
import me.rerere.unocssintellij.util.appendHighlightedCss

class UnocssThemeScreenDocumentationTarget(
    private val targetElement: PsiElement?,
) : DocumentationTarget {

    override fun computePresentation(): TargetPresentation {
        return TargetPresentation
            .builder("Unocss Document")
            .presentation()
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val pointer = targetElement?.createSmartPointer()
        return Pointer {
            UnocssThemeScreenDocumentationTarget(pointer?.dereference())
        }
    }

    override fun computeDocumentation(): DocumentationResult {
        return DocumentationResult.asyncDocumentation doc@{
            if (targetElement == null) {
                return@doc null
            }
            val match = breakpointRE.matchEntire(targetElement.text) ?: return@doc null
            val prefix = match.groupValues[1]
            val breakpointName = match.groupValues[2]

            val doc = computeScreenBreakpointsDoc(prefix, breakpointName) ?: return@doc null

            DocumentationResult.documentation(buildString {
                append(DocumentationMarkup.DEFINITION_START)
                if (doc == UNDEFINED_BREAKPOINT) {
                    append("Unable to find breakpoint: $breakpointName")
                } else {
                    appendHighlightedCss(targetElement.project, doc)
                }
                append(DocumentationMarkup.DEFINITION_END)

                append(DocumentationMarkup.CONTENT_START)
                append("Unocss Config Breakpoints")
                append(DocumentationMarkup.CONTENT_END)
            })
        }
    }

    private fun computeScreenBreakpointsDoc(
        prefix: String,
        breakpointName: String
    ): String? {
        val breakpointsConf = UnocssConfigManager.theme["breakpoints"] ?: return null
        if (!breakpointsConf.isJsonObject) {
            return null
        }
        val breakpointsObj = breakpointsConf.asJsonObject
        val breakpoints = breakpointsObj.keySet()
            .filter { breakpointsObj[it].isJsonPrimitive }
            .mapIndexed { index, key ->
                BreakpointEntry(key, breakpointsObj[key].asJsonPrimitive.asString, index)
            }

        val (_, size, index) = breakpoints.find { it.point == breakpointName }
            ?: return UNDEFINED_BREAKPOINT
        return when (prefix) {
            "lt" -> "@media (max-width: ${calcMaxWidthBySize(size)})"
            "at" -> {
                if (index < breakpoints.lastIndex) {
                    "@media (min-width: $size) and (max-width: ${calcMaxWidthBySize(breakpoints[index + 1].size)})"
                } else {
                    "@media (min-width: ${size})"
                }
            }

            else -> "@media (min-width: ${size})"
        }
    }

    private fun calcMaxWidthBySize(size: String): String {
        val value = (sizePattern.find(size) ?: return size).groupValues[0]
        val unit = size.substring(value.length)
        return try {
            val maxWidth = value.toDouble() - 0.1
            "${maxWidth}${unit}"
        } catch (e: NumberFormatException) {
            size
        }
    }

    private data class BreakpointEntry(val point: String, val size: String, val index: Int)

    companion object {
        private val breakpointRE = Regex("^(?:(lt|at)-)?(\\w+)$")
        private val sizePattern = Regex("^-?[0-9]+\\.?[0-9]*")
        private const val UNDEFINED_BREAKPOINT = "undefined"
    }
}