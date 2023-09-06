package me.rerere.unocssintellij.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.css.impl.CssElementTypes
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlElementType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.settings.UnocssSettingsState
import me.rerere.unocssintellij.util.MatchedPosition
import me.rerere.unocssintellij.util.getMatchedPositions
import me.rerere.unocssintellij.util.isJsString

data class UnocssInitInfo(
    val project: Project,
    val service: UnocssService,
    val psiFile: PsiFile,
    val fileContent: String,
)

data class UnocssAnnotationResult(
    val code: String,
    val annotations: List<MatchedPosition>,
)

/**
 * For highlighting unocss attributes
 * ported from vscode plugin
 */
class UnocssAttributeExternalAnnotator : ExternalAnnotator<UnocssInitInfo, UnocssAnnotationResult>() {

    override fun collectInformation(psiFile: PsiFile, editor: Editor, hasErrors: Boolean): UnocssInitInfo? {
        if (!UnocssSettingsState.instance.enable) return null

        val project = psiFile.project

        val virtualFile = psiFile.virtualFile
        if (virtualFile != null && virtualFile.isInLocalFileSystem) {
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return null
            val fileContent = document.text
            if (StringUtil.isEmptyOrSpaces(fileContent)) {
                return null
            }

            val unocssService = project.service<UnocssService>()
            return UnocssInitInfo(project, unocssService, psiFile, fileContent)
        }
        return null
    }

    override fun doAnnotate(collectedInfo: UnocssInitInfo?): UnocssAnnotationResult? {
        if (collectedInfo == null) {
            return null
        }

        val unocssService = collectedInfo.service
        val code = collectedInfo.fileContent
        val resolveResult = runBlocking {
            withTimeoutOrNull(1000) {
                unocssService.resolveAnnotations(collectedInfo.psiFile.virtualFile, code)
            }
        } ?: return null

        val matchedPositions = getMatchedPositions(code, resolveResult)
        return UnocssAnnotationResult(code, matchedPositions)
    }

    override fun apply(file: PsiFile, annotationResult: UnocssAnnotationResult?, holder: AnnotationHolder) {
        annotationResult?.annotations?.forEach {
            val element = file.findElementAt(it.start) ?: return@forEach
            if (isValidElement(element)) {
                holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES)
                    .range(TextRange.create(it.start, it.end))
                    .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                    .create()
            }
        }
    }
}

private val VALID_ELEMENT_TYPES = setOf(
    XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN,
    XmlElementType.XML_NAME,
    CssElementTypes.CSS_STRING_TOKEN,
    CssElementTypes.CSS_IDENT,
)

private fun isValidElement(element: PsiElement): Boolean {
    val elementType = element.elementType
    return elementType in VALID_ELEMENT_TYPES || isJsString(element)
}