package me.rerere.unocssintellij.util

import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import me.rerere.unocssintellij.settings.UnocssSettingsState

private val remRE = Regex("-?[\\d.]+rem;")

fun resolveRemToPx(css: String, project: Project): String {
    if (css.isBlank()) return css

    val settingsState = UnocssSettingsState.of(project)
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

@Suppress("UnstableApiUsage")
suspend fun StringBuilder.appendHighlightedCss(project: Project, css: String, formatted: Boolean = true) {

    val cssDoc = readAndWriteAction {
        val cssFile: PsiFile = PsiFileFactory.getInstance(project).createFileFromText(CSSLanguage.INSTANCE, css)
        if (formatted) {
            writeAction {
                executeCommand(project) {
                    CodeStyleManager.getInstance(cssFile.project)
                        .reformatText(cssFile, 0, cssFile.textLength)
                }
                cssFile.text
            }
        } else value(cssFile.text)
    }

    readAction {
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            this,
            project,
            CSSLanguage.INSTANCE,
            cssDoc,
            DocumentationSettings.getHighlightingSaturation(true)
        )
    }
}

fun StringBuilder.appendUnocssColorPreview(project: Project, css: String) {
    val settingsState = UnocssSettingsState.of(project)
    // Preview color when inlay hint or line marker is disabled
    if (settingsState.colorAndIconPreviewType == UnocssSettingsState.ColorAndIconPreviewType.NONE) {
        val colors = parseColors(css)
        if (colors.isNotEmpty()) {
            val color = colors.first().toHex()
            append("<table style=\"margin-top: 0\">")
            append(DocumentationMarkup.SECTION_START)
            append("Color Preview:")
            append(DocumentationMarkup.SECTION_END)
            append(DocumentationMarkup.SECTION_START)
            append("""<div style="height: 16px; width: 16px; background-color: $color;"></div>""".trimIndent())
            append(DocumentationMarkup.SECTION_END)
            append("</table>")
        }
    }
}