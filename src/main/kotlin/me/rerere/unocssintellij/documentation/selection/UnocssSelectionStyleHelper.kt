package me.rerere.unocssintellij.documentation.selection

import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.codeInsight.editorActions.SelectWordUtil.CharCondition
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.documentation.selection.ui.SelectionResult
import me.rerere.unocssintellij.highlighting.UnocssAnnotationsMatchedPositionCacheProvider
import me.rerere.unocssintellij.util.MatchedPosition
import me.rerere.unocssintellij.util.getMatchedPositions
import me.rerere.unocssintellij.util.resolveRemToPx


const val classNamePlaceholder = "___"

const val noMediaMark = "_@_"

private val regexScopePlaceholder = Regex("\\s\\$\\$\\s+")

private fun Editor.getSelectionFromCurrentCaret(
    isWordPartCondition: CharCondition = CharCondition { ch -> ch == ' ' }
): TextRange? {
    val ranges = mutableListOf<TextRange>()
    SelectWordUtil.addWordOrLexemeSelection(false, this, caretModel.offset, ranges, isWordPartCondition)

    return when {
        ranges.isEmpty() -> null
        else -> ranges[0]
    }
}

fun Editor.getSelectionRange() = selectionModel.takeIf { it.hasSelection() }
    ?.run { TextRange(selectionStart, selectionEnd) }
    ?: getSelectionFromCurrentCaret()

fun Editor.parseSelectionForUnocss(range: TextRange?): String {
    if (range == null || range.isEmpty) return ""


    val selectionModel = selectionModel
    val isColumnSelectionMode = caretModel.caretCount > 1

    val selection = if (selectionModel.hasSelection(true) && isColumnSelectionMode) {
        selectionModel.getSelectedText(true) ?: return ""
    } else {
        document.getText(range)
    }.trim()

    return "${if (!selection.startsWith('<')) "<div " else ""}$selection${if (!selection.endsWith('>')) " >" else ""}"
}

suspend fun resolveSelectionStyle(project: Project, selectionResult: SelectionResult?): String? {
    if (selectionResult == null) return null

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectionResult.document)
    if (psiFile != null) {
        val matchedPosition = withContext(Dispatchers.IO) {
            readAction {
                CachedValuesManager.getManager(project)
                    .getCachedValue(psiFile, UnocssAnnotationsMatchedPositionCacheProvider(psiFile))
            }
        }

        val textRange = selectionResult.textRange

        val filtered = matchedPosition
            .filter { it.end > textRange.startOffset && it.start < textRange.endOffset }
            .takeIf { it.size > 1 } ?: return null

        return doGenerateMergedCss(filtered, project)
    } else {
        return generateMergedCss(project, selectionResult.content)
    }
}

suspend fun generateMergedCss(project: Project, code: String): String? {
    if (code.isBlank()) return null

    val unocssService = project.service<UnocssService>()
    val matchResult = withBackgroundProgress(project, "Resolving UnoCSS") {
        withTimeoutOrNull(500) {
            unocssService.resolveAnnotations(project.projectFile, code)
        }?.let { getMatchedPositions(code, it) }
    }?.takeIf { it.size > 1 } ?: return null

    return doGenerateMergedCss(matchResult, project)
}

@Suppress("UnstableApiUsage")
private suspend fun doGenerateMergedCss(
    matchResult: List<MatchedPosition>,
    project: Project
): String {

    val unocssService: UnocssService = project.service()
    val file = project.projectFile

    val sheetMap = concurrentMapOf<String, MutableMap<String, String>>()

    matchResult
        .asReversed()
        .distinctBy { "${it.start}-${it.end}" }
        .map { it.text }
        .forEachConcurrent { name ->
            val tokens = unocssService
                .resolveToken(file, name, classNamePlaceholder)?.result ?: emptyList()

            tokens.forEach { (_, className, cssText, media) ->
                if (className != null) {
                    val usedCssText = resolveRemToPx(cssText, project)
                    val selector = className
                        .replace(".$classNamePlaceholder", "&")
                        .replace(regexScopePlaceholder, " ")
                        .trim()

                    sheetMap.getOrPut(media ?: noMediaMark) { concurrentMapOf() }
                        .merge(selector, usedCssText) { old, new -> "$old$new" }
                }
            }
        }

    return sheetMap.map { (media, map) ->
        val body = map.keys
            .sorted()
            .joinToString("\n") { selector -> "$selector{${map[selector]}}" }

        if (media != noMediaMark) "$media{$body}" else body
    }.joinToString("\n")
}
