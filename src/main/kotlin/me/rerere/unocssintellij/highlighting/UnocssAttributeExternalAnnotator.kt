package me.rerere.unocssintellij.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.MatchedPosition
import me.rerere.unocssintellij.util.isUnocssCandidate

data class UnocssInitInfo(
    val psiFile: PsiFile,
    val matchedPosition: CachedValue<List<MatchedPosition>?>
)

data class UnocssAnnotationResult(
    val annotations: List<MatchedPosition>,
)

/**
 * For highlighting unocss attributes
 * ported from vscode plugin
 */
class UnocssAttributeExternalAnnotator : ExternalAnnotator<UnocssInitInfo, UnocssAnnotationResult>() {

    override fun collectInformation(psiFile: PsiFile, editor: Editor, hasErrors: Boolean): UnocssInitInfo? {
        val project = psiFile.project
        val settingsState = UnocssSettingsState.of(project)
        if (!settingsState.enableUnderline) {
            return null
        }

        val provider = UnocssAnnotationsMatchedPositionCacheProvider(psiFile)
        val cachedValue = CachedValuesManager.getManager(project)
            .createCachedValue(psiFile, provider, false)

        return UnocssInitInfo(psiFile, cachedValue)
    }

    override fun doAnnotate(collectedInfo: UnocssInitInfo?): UnocssAnnotationResult? {
        if (collectedInfo == null) {
            return null
        }

        val cachedValue = collectedInfo.matchedPosition

        val matchedPositions = cachedValue.value ?: return null
        return UnocssAnnotationResult(matchedPositions)
    }

    override fun apply(file: PsiFile, annotationResult: UnocssAnnotationResult?, holder: AnnotationHolder) {
        annotationResult?.annotations
            ?.filter { file.findElementAt(it.start)?.isUnocssCandidate() == true }
            ?.forEach {
                holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
                    .range(TextRange.create(it.start, it.end))
                    .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                    .create()
            }
    }
}
