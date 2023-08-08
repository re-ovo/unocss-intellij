package me.rerere.unocssintellij.model

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.unocssintellij.UnocssService
import me.rerere.unocssintellij.rpc.ResolveCSSResult
import me.rerere.unocssintellij.util.buildDummyDivTag

data class UnocssResolveMeta(
    val bindElement: PsiElement,
    val attrName: String,
    val attrValue: String? = null,
    val wrapDom: Boolean = attrValue != null,
) {
    val project get() = bindElement.project

    val virtualFile: VirtualFile? get() = bindElement.containingFile.virtualFile

    val unocssService get() = project.service<UnocssService>()

    val resolveContent
        get() = if (wrapDom) {
            buildDummyDivTag(attrName to attrValue)
        } else attrName

    fun resolveCss(): ResolveCSSResult? = runBlocking {
        withTimeoutOrNull(100) { unocssService.resolveCss(virtualFile, resolveContent) }
    }
}
