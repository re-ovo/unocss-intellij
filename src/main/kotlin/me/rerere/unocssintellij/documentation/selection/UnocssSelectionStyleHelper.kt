package me.rerere.unocssintellij.documentation.selection

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.rd.util.concurrentMapOf
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.highlighting.UnocssAnnotationsMatchedPositionCacheProvider
import me.rerere.unocssintellij.util.MatchedPosition
import me.rerere.unocssintellij.util.resolveRemToPx


const val classNamePlaceholder = "___"

const val noMediaMark = "_@_"

private val regexScopePlaceholder = Regex("\\s\\$\\$\\s+")

private infix fun MatchedPosition.matchRange(textRange: TextRange): Boolean {
    return end > textRange.startOffset && start < textRange.endOffset
}

suspend fun lookupSelectionMatchedPositions(psiFile: PsiFile, ranges: Array<TextRange>): List<MatchedPosition> {
    val matchedPosition = readAction {
        CachedValuesManager.getManager(psiFile.project)
            .getCachedValue(psiFile, UnocssAnnotationsMatchedPositionCacheProvider(psiFile))
    }

    return matchedPosition.filter { ranges.any(it::matchRange) }
}

suspend fun resolveSelectionStyle(project: Project, document: Document, selectRanges: Array<TextRange>): String? {
    val psiFile = readAction {
        PsiDocumentManager.getInstance(project).getPsiFile(document)
    } ?: return null

    val filtered = lookupSelectionMatchedPositions(psiFile, selectRanges)

    return mergeCss(filtered, project)
}

suspend fun mergeCss(
    matchResult: List<MatchedPosition>,
    project: Project
): String {

    val unocssService: UnocssService = project.service()
    val file = project.projectFile

    val sheetMap = concurrentMapOf<String, MutableMap<String, String>>()

    @Suppress("UnstableApiUsage")
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
