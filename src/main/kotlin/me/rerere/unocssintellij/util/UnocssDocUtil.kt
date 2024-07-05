package me.rerere.unocssintellij.util

import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.unocssintellij.settings.UnocssSettingsState

private val remRE = Regex("-?[\\d.]+rem;")

val regexScopePlaceholder = Regex("\\s\\$\\$\\s+")

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

suspend fun StringBuilder.appendHighlightedCss(project: Project, css: String, formatted: Boolean = true) {
    val cssFile: PsiFile = withContext(Dispatchers.EDT) {
        PsiFileFactory.getInstance(project).createFileFromText(CSSLanguage.INSTANCE, css)
    }

    if (formatted) {
        WriteCommandAction.runWriteCommandAction(cssFile.project) {
            val doc = PsiDocumentManager.getInstance(cssFile.project)
                .getDocument(cssFile) ?: return@runWriteCommandAction
            PsiDocumentManager.getInstance(cssFile.project).doPostponedOperationsAndUnblockDocument(doc)
            CodeStyleManager.getInstance(cssFile.project)
                .reformatText(cssFile, 0, cssFile.textLength)
        }
    }

    readAction {
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            this,
            cssFile.project,
            CSSLanguage.INSTANCE,
            cssFile.text,
            DocumentationSettings.getHighlightingSaturation(true)
        )
    }
}