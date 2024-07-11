package me.rerere.unocssintellij.highlighting

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.util.MatchedPosition
import me.rerere.unocssintellij.util.getMatchedPositions

class UnocssAnnotationsMatchedPositionCacheProvider(
    private val psiFile: PsiFile
) : CachedValueProvider<List<MatchedPosition>> {

    override fun compute(): Result<List<MatchedPosition>>? {
        val virtualFile = psiFile.virtualFile
        if (virtualFile == null || !virtualFile.isInLocalFileSystem) {
            return null
        }

        val project = psiFile.project
        val document = PsiDocumentManager.getInstance(project)
            .getDocument(psiFile) ?: return null
        val fileContent = document.text
        if (StringUtil.isEmptyOrSpaces(fileContent)) {
            return null
        }

        val unocssService = psiFile.project.service<UnocssService>()
        val annotations = runBlockingCancellable {
            withTimeoutOrNull(1000) {
                unocssService.resolveAnnotations(psiFile.virtualFile, fileContent)
            }
        } ?: return null

        return Result.create(getMatchedPositions(fileContent, annotations), psiFile)
    }
}